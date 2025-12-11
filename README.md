![UserLAnd Feature Graphic](https://raw.githubusercontent.com/CypherpunkArmory/UserLAnd/master/fastlane/metadata/android/en-US/images/featureGraphic.png)

# Welcome to UserLAnd

The easiest way to run a Linux distribution or application on Android.   
Features: 
* Run full linux distros or specific applications on top of Android.
* Install and uninstall like a regular app.
* No root required.

[<img src="https://play.google.com/intl/en_us/badges/images/generic/en-play-badge.png"
     alt="Get it on Google Play"
     height="80">](https://play.google.com/store/apps/details?id=tech.ula)
     
## Have a bug report or a feature request?
You can see our templates by visiting our [issue center](https://github.com/CypherpunkArmory/UserLAnd/issues).

## Want to contribute?
See our [CONTRIBUTING](https://github.com/CypherpunkArmory/UserLAnd/blob/master/CONTRIBUTING.md) document.

## Start using UserLAnd
See our [Getting Started](https://github.com/CypherpunkArmory/UserLAnd/wiki/Getting-Started-in-UserLAnd) page.

## UserLAnd assets
The assets that UserLAnd depends on and the scripts that build them are contained in other repositories.  

The common assets that are used for all distros and application are found at [CypherpunkArmory/UserLAnd-Assets-Support](https://github.com/CypherpunkArmory/UserLAnd-Assets-Support).  

Distribution or application specific assets are found under CypherpunkArmory/UserLAnd-Assets-(__Distribution/App__). For example, our Debian specific assets can be found at [CypherpunkArmory/UserLAnd-Assets-Debian](https://github.com/CypherpunkArmory/UserLAnd-Assets-Debian)

## Static musl/clang utilities

We ship tiny, static helpers compiled with musl/clang and invoked by thin shell scripts (no Python wrappers).

Build them all in one reproducible step:

```sh
./scripts/build_static.sh
```

Install/copy them so they are reachable on your `$PATH`:

```sh
# Local install for the UserLAnd tree
./scripts/install_static.sh

# Package into the Termux APK assets (used during app builds)
./termux-app/package_static_bins.sh
```

### JSON contracts

All helpers emit stable JSON payloads so monitoring tools can parse the same schema across environments.

* `raf_cpu_core` reads `/proc/stat` from stdin and prints:

  ```json
  {
    "timestamp_ms": 1715890000000,
    "cpus": [
      {
        "id": "cpu0",
        "user": 1,
        "nice": 2,
        "system": 3,
        "idle": 4,
        "iowait": 5,
        "irq": 6,
        "softirq": 7,
        "steal": 8,
        "guest": 9,
        "guest_nice": 10
      }
    ]
  }
  ```

* `raf_mem_core` reads `/proc/meminfo` directly and prints a single object with all keys from `/proc/meminfo`:

  ```json
  {
    "timestamp_ms": 1715890000000,
    "meminfo": {
      "MemTotal": 16303424,
      "MemFree": 1024
    }
  }
  ```

* `raf_disk_core` reads `/proc/diskstats` directly and prints:

  ```json
  {
    "timestamp_ms": 1715890000000,
    "disks": [
      {
        "name": "sda",
        "major": 8,
        "minor": 0,
        "reads_completed": 0,
        "reads_merged": 0,
        "sectors_read": 0,
        "time_reading_ms": 0,
        "writes_completed": 0,
        "writes_merged": 0,
        "sectors_written": 0,
        "time_writing_ms": 0,
        "ios_in_progress": 0,
        "time_doing_io_ms": 0,
        "weighted_time_io_ms": 0
      }
    ]
  }
  ```

Each binary has a matching shell entrypoint (`scripts/raf_cpu_core.sh`, `scripts/raf_mem_core.sh`, `scripts/raf_disk_core.sh`) that prefers the local `build/bin` artifacts but also falls back to whatever is on the `$PATH`, ensuring consistent invocation names everywhere.

### Kernel telemetry daemon

The lightweight daemon `core/librafaelia/raf_kerneld.py` polls the helpers above, keeps a rolling CPU/Mem/Disk state in memory and exposes a JSON API for the Android/UserLAnd app to consume.

Minimum requirements:

* Python 3.10+
* `gcc` (to build `raf_cpu_core`, `raf_mem_core`, `raf_disk_core` via `scripts/build_static.sh`)
* Optional: `librrafaelia_core.so` compiled from `core/include/raf_core.c` (drop it into `core/librafaelia/librrafaelia_core.so` or point `--core-lib` to its location)

Build the helper binaries once (no need to commit the artifacts):

```sh
./scripts/build_static.sh
```

Start the daemon on the internal loopback, pointing it at the helper scripts (they automatically pick up the freshly built binaries):

```sh
python core/librafaelia/raf_kerneld.py --host 127.0.0.1 --port 8027 --interval 2 \
  --cpu-cmd scripts/raf_cpu_core.sh --mem-cmd scripts/raf_mem_core.sh --disk-cmd scripts/raf_disk_core.sh
```

HTTP endpoints (all JSON):

* `GET /status` → latest telemetry (CPU load, `MemFree`, disk I/O deltas) plus current RAF core config (`active_cores`, `decay_rate`, universe MB) and thresholds.
* `GET /jobs` → recent auto-tuning actions (core throttling, decay adjustments, universe resize) and collector errors, useful for UI surface in the app.

The daemon auto-tunes the core when telemetry crosses thresholds:

* High CPU load throttles `active_cores`; low load ramps back up until `--max-cores`.
* Low `MemFree` halves the allocated universe via `raf_alloc_universe`.
* Sustained disk I/O time nudges `decay_rate` down to cool the workload.

### RAFAELIA "GOD" core example

Para testar o núcleo RAFAELIA mais completo já incluído neste repositório, use o
script de build dedicado e siga o guia `RAF_GOD_CORE_EXAMPLE.md`:

```sh
./scripts/build_raf_god_core.sh
```

O binário interativo/bridge será gerado em `build/bin/raf_god_core_example`.
