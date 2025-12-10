#!/usr/bin/env bash
# Draft – UserLAnd / RAFAELIA debug build helper
set -euo pipefail

# Strong compilation flags: performance + hardening
CFLAGS="-std=c11 -O2 -march=native -Wall -Wextra -Werror \
-fstack-protector-strong -D_FORTIFY_SOURCE=2 -fPIE"

LDFLAGS="-pie"

log() {
  printf '[RDBG_BUILDER] %s\n' "$*"
}

build_dbg() {
  local src="raf_userland_debug.c"
  local bin="raf_userland_debug"

  if [[ -f "$src" ]]; then
    log "BUILD $src -> $bin"
    gcc $CFLAGS "$src" -o "$bin" $LDFLAGS -lm
  else
    log "SKIP $src (not found)"
  fi
}

bench_dbg() {
  if [[ ! -x ./raf_userland_debug ]]; then
    log "SKIP bench (binary not built)"
    return
  fi

  # Example synthetic input: CPU, MEM(MB), IO, THREADS
  log "RUN raf_userland_debug < synthetic stream"
  {
    echo "# cpu,mem,io,thr"
    echo "15.0, 512, 40, 120"
    echo "78.0, 2048, 900, 350"
    echo "99.0, 3900, 1200, 600"
  } | ./raf_userland_debug | tee rdbg_matrix.log
}

build_dbg
bench_dbg
