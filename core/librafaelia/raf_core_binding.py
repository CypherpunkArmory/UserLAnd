"""Lightweight ctypes wrapper around raf_core.c for dynamic tuning.

The wrapper is optional: when the shared library cannot be loaded the
controller falls back to a pure-Python stub so that the daemon can still
run and log intented adjustments.
"""

from __future__ import annotations

import ctypes
from ctypes import c_float, c_uint32, c_uint64, c_void_p, POINTER
from dataclasses import dataclass
from pathlib import Path
from typing import Optional


LIB_DEFAULT_PATH = Path(__file__).resolve().parent.parent / "librafaelia" / "librrafaelia_core.so"


class RafConfig(ctypes.Structure):
    _fields_ = [
        ("dim_n", c_uint64),
        ("phys_ram_mb", c_uint64),
        ("active_cores", c_uint32),
        ("decay_rate", c_float),
        ("inject_power", c_float),
    ]


class AbsoluteMatrix(ctypes.Structure):
    _fields_ = [
        ("tensor", c_void_p),
        ("phys_mask", c_uint32),
        ("phys_cells", c_uint32),
        ("cursors", c_uint32 * (128 * 4096)),
        ("cursor_heads", c_uint32 * 128),
        ("state_crc", c_uint32),
        ("rng_state", c_uint32),
        ("cycle_count", c_uint64),
        ("ops_metric", c_uint64),
        ("global_energy", c_float),
    ]


@dataclass
class CoreStatus:
    active_cores: int
    decay_rate: float
    universe_mb: int
    initialized: bool
    backend: str


class RafCoreController:
    """Helper around the C core to adjust runtime parameters."""

    def __init__(self, lib_path: Optional[Path] = None, initial_universe_mb: int = 256, active_core_cap: int = 8):
        self.lib_path = Path(lib_path) if lib_path else LIB_DEFAULT_PATH
        self.initial_universe_mb = max(1, initial_universe_mb)
        self.active_core_cap = max(1, active_core_cap)
        self.lib = None
        self.config = RafConfig()
        self.ctx = AbsoluteMatrix()
        self.initialized = False
        self.backend = "stub"
        self._load_library()

    def _load_library(self) -> None:
        if not self.lib_path.exists():
            return
        try:
            self.lib = ctypes.CDLL(str(self.lib_path))
        except OSError:
            self.lib = None
            return

        if not all(hasattr(self.lib, name) for name in ("raf_genesis", "raf_alloc_universe")):
            self.lib = None
            return

        self.lib.raf_genesis.argtypes = [POINTER(AbsoluteMatrix), POINTER(RafConfig)]
        self.lib.raf_genesis.restype = ctypes.c_int
        self.lib.raf_alloc_universe.argtypes = [POINTER(AbsoluteMatrix), POINTER(RafConfig), c_uint64]
        self.lib.raf_alloc_universe.restype = ctypes.c_int
        self.backend = "ctypes"

    def ensure_initialized(self) -> bool:
        if self.lib is None:
            return False
        if self.initialized:
            return True
        if self.lib.raf_genesis(ctypes.byref(self.ctx), ctypes.byref(self.config)) != 0:
            return False
        if self.lib.raf_alloc_universe(ctypes.byref(self.ctx), ctypes.byref(self.config), c_uint64(self.initial_universe_mb)) != 0:
            return False
        self.initialized = True
        return True

    def set_active_cores(self, cores: int) -> bool:
        cores = max(1, min(self.active_core_cap, cores))
        self.config.active_cores = c_uint32(cores)
        return self.ensure_initialized()

    def set_decay_rate(self, decay: float) -> bool:
        self.config.decay_rate = c_float(decay)
        return self.ensure_initialized()

    def resize_universe_mb(self, mb: int) -> bool:
        mb = max(1, mb)
        self.config.phys_ram_mb = c_uint64(mb)
        if self.lib is None:
            return False
        if not self.ensure_initialized():
            return False
        return self.lib.raf_alloc_universe(ctypes.byref(self.ctx), ctypes.byref(self.config), c_uint64(mb)) == 0

    def status(self) -> CoreStatus:
        return CoreStatus(
            active_cores=int(self.config.active_cores),
            decay_rate=float(self.config.decay_rate),
            universe_mb=int(self.config.phys_ram_mb or self.initial_universe_mb),
            initialized=bool(self.initialized),
            backend=self.backend,
        )
