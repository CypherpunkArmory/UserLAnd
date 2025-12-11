#!/usr/bin/env bash
set -euo pipefail

# Repository root (one level above scripts/)
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SRC_DIR="${SRC_DIR:-"$ROOT_DIR"}"

# Output directory for compiled binaries
OUT_DIR="${OUT_DIR:-"$ROOT_DIR/build/bin"}"

# C compiler (defaults to native cc: gcc or clang)
CC="${CC:-cc}"

# Optional cross-target, e.g.:
#   TARGET="--target=x86_64-linux-musl"
TARGET="${TARGET:-}"

# Default CFLAGS: optimized, hidden symbols, no -static by default.
CFLAGS="${CFLAGS:-"-O2 -pipe -fvisibility=hidden"}"

# Extra linker flags, if any.
LDFLAGS="${LDFLAGS:-}"

mkdir -p "$OUT_DIR"

build() {
  local src="$1"
  local bin_name="$2"
  local out="$OUT_DIR/$bin_name"

  echo "[build] $src -> $out" >&2

  if [[ -n "$TARGET" ]]; then
    # Cross or special target (e.g. MUSL)
    "$CC" $TARGET $CFLAGS "$SRC_DIR/$src" -o "$out" $LDFLAGS
  else
    # Native build (glibc / default system toolchain)
    "$CC" $CFLAGS "$SRC_DIR/$src" -o "$out" $LDFLAGS
  fi
}

echo "RAFAELIA static/dynamic builder"

if ! command -v "$CC" >/dev/null 2>&1; then
  echo "Compiler '$CC' not found (set CC or install gcc/clang)" >&2
  exit 1
fi

# Core binaries to build. Extend this list as needed.
build raf_cpu_core.c raf_cpu_core
build raf_mem_core.c raf_mem_core
build raf_disk_core.c raf_disk_core
