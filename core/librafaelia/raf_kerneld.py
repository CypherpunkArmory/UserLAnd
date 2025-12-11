"""Telemetry daemon for Rafaeila kernel helpers.

The daemon periodically invokes the lightweight C helpers (raf_cpu_core,
raf_mem_core, raf_disk_core), computes load metrics, and exposes a JSON
HTTP API for Android/UserLAnd components.
"""

from __future__ import annotations

import argparse
import json
import subprocess
import threading
import time
from dataclasses import dataclass
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from typing import Dict, List, Optional

from raf_core_binding import RafCoreController

ROOT_DIR = Path(__file__).resolve().parent.parent.parent
DEFAULT_CPU_CMD = ROOT_DIR / "scripts" / "raf_cpu_core.sh"
DEFAULT_MEM_CMD = ROOT_DIR / "scripts" / "raf_mem_core.sh"
DEFAULT_DISK_CMD = ROOT_DIR / "scripts" / "raf_disk_core.sh"


class CommandError(RuntimeError):
    pass


class RafHelperRunner:
    def __init__(self, command: Path):
        self.command = Path(command)

    def run(self, *, input_data: Optional[bytes] = None) -> Dict:
        result = subprocess.run(
            [str(self.command)],
            input=input_data,
            capture_output=True,
            check=False,
        )
        if result.returncode != 0:
            raise CommandError(result.stderr.decode() or f"command {self.command} failed")
        try:
            return json.loads(result.stdout.decode())
        except json.JSONDecodeError as exc:
            raise CommandError(f"invalid JSON from {self.command}: {exc}") from exc


@dataclass
class DiskDelta:
    time_doing_io_ms: int
    weighted_time_io_ms: int


@dataclass
class TelemetrySnapshot:
    timestamp: float
    cpu_json: Dict
    mem_json: Dict
    disk_json: Dict
    cpu_load: float
    mem_free_kb: int
    disk_delta: DiskDelta


def _pick_cpu_root(snapshot: Dict) -> Optional[Dict]:
    for sample in snapshot.get("cpus", []):
        if sample.get("id") == "cpu":
            return sample
    return None


def _compute_cpu_load(prev: Optional[Dict], curr: Dict) -> float:
    prev_cpu = _pick_cpu_root(prev) if prev else None
    curr_cpu = _pick_cpu_root(curr)
    if curr_cpu is None:
        return 0.0

    def totals(sample: Dict) -> tuple[int, int]:
        idle = int(sample.get("idle", 0)) + int(sample.get("iowait", 0))
        non_idle = (
            int(sample.get("user", 0))
            + int(sample.get("nice", 0))
            + int(sample.get("system", 0))
            + int(sample.get("irq", 0))
            + int(sample.get("softirq", 0))
            + int(sample.get("steal", 0))
        )
        total = idle + non_idle + int(sample.get("guest", 0)) + int(sample.get("guest_nice", 0))
        return total, idle

    if prev_cpu is None:
        return 0.0

    prev_total, prev_idle = totals(prev_cpu)
    curr_total, curr_idle = totals(curr_cpu)

    total_delta = curr_total - prev_total
    idle_delta = curr_idle - prev_idle
    if total_delta <= 0:
        return 0.0
    return max(0.0, min(1.0, float(total_delta - idle_delta) / float(total_delta)))


def _compute_disk_delta(prev: Optional[Dict], curr: Dict) -> DiskDelta:
    def sum_fields(snapshot: Optional[Dict], key: str) -> int:
        if snapshot is None:
            return 0
        total = 0
        for disk in snapshot.get("disks", []):
            total += int(disk.get(key, 0))
        return total

    prev_time = sum_fields(prev, "time_doing_io_ms")
    prev_weighted = sum_fields(prev, "weighted_time_io_ms")
    curr_time = sum_fields(curr, "time_doing_io_ms")
    curr_weighted = sum_fields(curr, "weighted_time_io_ms")
    return DiskDelta(
        time_doing_io_ms=max(0, curr_time - prev_time),
        weighted_time_io_ms=max(0, curr_weighted - prev_weighted),
    )


class TelemetryCollector:
    def __init__(self, cpu_cmd: Path, mem_cmd: Path, disk_cmd: Path, interval: float = 2.0):
        self.cpu_runner = RafHelperRunner(cpu_cmd)
        self.mem_runner = RafHelperRunner(mem_cmd)
        self.disk_runner = RafHelperRunner(disk_cmd)
        self.interval = interval
        self._prev_cpu = None
        self._prev_disk = None

    def _read_proc_stat(self) -> bytes:
        return Path("/proc/stat").read_bytes()

    def collect_once(self) -> TelemetrySnapshot:
        cpu_json = self.cpu_runner.run(input_data=self._read_proc_stat())
        mem_json = self.mem_runner.run()
        disk_json = self.disk_runner.run()

        cpu_load = _compute_cpu_load(self._prev_cpu, cpu_json)
        disk_delta = _compute_disk_delta(self._prev_disk, disk_json)
        mem_free_kb = int(mem_json.get("meminfo", {}).get("MemFree", 0))

        self._prev_cpu = cpu_json
        self._prev_disk = disk_json

        return TelemetrySnapshot(
            timestamp=time.time(),
            cpu_json=cpu_json,
            mem_json=mem_json,
            disk_json=disk_json,
            cpu_load=cpu_load,
            mem_free_kb=mem_free_kb,
            disk_delta=disk_delta,
        )


@dataclass
class TuningThresholds:
    cpu_hot: float = 0.85
    cpu_cool: float = 0.30
    mem_floor_kb: int = 512_000
    io_busy_ms: int = 500


class AutoTuner:
    def __init__(self, controller: RafCoreController, thresholds: TuningThresholds):
        self.controller = controller
        self.thresholds = thresholds
        self.job_log: List[Dict[str, str]] = []
        self.max_jobs = 50

    def _append_job(self, title: str, detail: str) -> None:
        entry = {"title": title, "detail": detail, "ts": time.time()}
        self.job_log.append(entry)
        if len(self.job_log) > self.max_jobs:
            self.job_log.pop(0)

    def evaluate(self, snapshot: TelemetrySnapshot) -> None:
        status = self.controller.status()
        if snapshot.cpu_load >= self.thresholds.cpu_hot and status.active_cores > 1:
            new_cores = max(1, status.active_cores - 1)
            if self.controller.set_active_cores(new_cores):
                self._append_job(
                    "active_cores",
                    f"CPU load {snapshot.cpu_load:.2f} -> throttling cores {status.active_cores}→{new_cores}",
                )
        elif snapshot.cpu_load <= self.thresholds.cpu_cool and status.active_cores < self.controller.active_core_cap:
            new_cores = min(self.controller.active_core_cap, status.active_cores + 1)
            if self.controller.set_active_cores(new_cores):
                self._append_job(
                    "active_cores",
                    f"CPU cool {snapshot.cpu_load:.2f} -> ramping cores {status.active_cores}→{new_cores}",
                )

        if snapshot.mem_free_kb < self.thresholds.mem_floor_kb:
            target_mb = max(64, status.universe_mb // 2)
            if self.controller.resize_universe_mb(target_mb):
                self._append_job(
                    "universe",
                    f"MemFree={snapshot.mem_free_kb}kB -> shrinking universe to {target_mb}MB",
                )

        if snapshot.disk_delta.weighted_time_io_ms > self.thresholds.io_busy_ms:
            new_decay = max(0.90, min(0.999, status.decay_rate * 0.98))
            if self.controller.set_decay_rate(new_decay):
                self._append_job(
                    "decay_rate",
                    f"I/O busy {snapshot.disk_delta.weighted_time_io_ms}ms -> decay {status.decay_rate:.3f}→{new_decay:.3f}",
                )


class SharedState:
    def __init__(self, tuner: AutoTuner):
        self.lock = threading.Lock()
        self.snapshot: Optional[TelemetrySnapshot] = None
        self.tuner = tuner

    def update(self, snapshot: TelemetrySnapshot) -> None:
        with self.lock:
            self.snapshot = snapshot
            self.tuner.evaluate(snapshot)

    def export(self) -> Dict:
        with self.lock:
            snap = self.snapshot
            status = self.tuner.controller.status()
            return {
                "telemetry": {
                    "timestamp": snap.timestamp if snap else None,
                    "cpu_load": snap.cpu_load if snap else None,
                    "mem_free_kb": snap.mem_free_kb if snap else None,
                    "io_time_ms": snap.disk_delta.time_doing_io_ms if snap else None,
                    "io_weighted_ms": snap.disk_delta.weighted_time_io_ms if snap else None,
                    "raw": {
                        "cpu": snap.cpu_json if snap else None,
                        "mem": snap.mem_json if snap else None,
                        "disk": snap.disk_json if snap else None,
                    },
                },
                "core": status.__dict__,
                "thresholds": self.tuner.thresholds.__dict__,
            }

    def jobs(self) -> List[Dict[str, str]]:
        with self.lock:
            return list(self.tuner.job_log)


class ApiHandler(BaseHTTPRequestHandler):
    shared_state: SharedState

    def _write_json(self, payload: Dict, status: int = 200) -> None:
        body = json.dumps(payload, default=float).encode()
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def log_message(self, fmt: str, *args):  # noqa: D401 - silence noisy default logging
        return

    def do_GET(self) -> None:  # noqa: N802 - BaseHTTPRequestHandler API
        if self.path == "/status":
            self._write_json(self.shared_state.export())
        elif self.path == "/jobs":
            self._write_json({"jobs": self.shared_state.jobs()})
        else:
            self._write_json({"error": "not found"}, status=404)


class TelemetryThread(threading.Thread):
    def __init__(self, collector: TelemetryCollector, state: SharedState):
        super().__init__(daemon=True)
        self.collector = collector
        self.state = state
        self._stop = threading.Event()

    def run(self) -> None:
        while not self._stop.is_set():
            try:
                snapshot = self.collector.collect_once()
                self.state.update(snapshot)
            except (OSError, CommandError) as exc:
                self.state.tuner._append_job("collector_error", str(exc))
            time.sleep(self.collector.interval)

    def stop(self) -> None:
        self._stop.set()


def build_server(state: SharedState, host: str, port: int) -> ThreadingHTTPServer:
    ApiHandler.shared_state = state
    return ThreadingHTTPServer((host, port), ApiHandler)


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Rafaeila kernel telemetry daemon")
    parser.add_argument("--host", default="127.0.0.1", help="Bind address for the HTTP API")
    parser.add_argument("--port", type=int, default=8027, help="Port for the HTTP API")
    parser.add_argument("--interval", type=float, default=2.0, help="Polling interval in seconds")
    parser.add_argument("--core-lib", type=Path, default=None, help="Path to librrafaelia_core.so")
    parser.add_argument("--universe-mb", type=int, default=256, help="Initial universe size in MB")
    parser.add_argument("--max-cores", type=int, default=8, help="Maximum active cores allowed")
    parser.add_argument("--cpu-cmd", type=Path, default=DEFAULT_CPU_CMD, help="Command for raf_cpu_core JSON helper")
    parser.add_argument("--mem-cmd", type=Path, default=DEFAULT_MEM_CMD, help="Command for raf_mem_core JSON helper")
    parser.add_argument("--disk-cmd", type=Path, default=DEFAULT_DISK_CMD, help="Command for raf_disk_core JSON helper")
    return parser.parse_args()


def main() -> None:
    args = parse_args()
    controller = RafCoreController(args.core_lib, args.universe_mb, args.max_cores)
    thresholds = TuningThresholds()
    tuner = AutoTuner(controller, thresholds)
    state = SharedState(tuner)

    collector = TelemetryCollector(args.cpu_cmd, args.mem_cmd, args.disk_cmd, args.interval)
    worker = TelemetryThread(collector, state)
    worker.start()

    server = build_server(state, args.host, args.port)
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        server.shutdown()
    finally:
        worker.stop()


if __name__ == "__main__":
    main()
