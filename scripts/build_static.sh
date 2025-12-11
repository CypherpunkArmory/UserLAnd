#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SRC_DIR="$ROOT_DIR"
OUT_DIR="${OUT_DIR:-$ROOT_DIR/build/bin}"
CC="${CC:-clang}"
TARGET="${TARGET:---target=x86_64-linux-musl}"
CFLAGS="${CFLAGS:--O2 -pipe -static -fvisibility=hidden}"
LDFLAGS="${LDFLAGS:-}"

mkdir -p "$OUT_DIR"

build() {
echo "$1" bin_name="$2"
echo "$OUT_DIR/$bin_name
  local src="$1" bin_name="$2"
  local out="$OUT_DIR/$bin_name"
  echo "[static] $src -> $out" >&2
  "$CC" $TARGET $CFLAGS "$SRC_DIR/$src" -o "$out" $LDFLAGS
}
echo "🦉"
if ! command -v "$CC" >/dev/null 2>&1; then
  echo "clang não encontrado (defina CC)" >&2
  exit 1
fi

build raf_cpu_core.c raf_cpu_core
