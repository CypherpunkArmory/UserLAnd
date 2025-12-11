#!/usr/bin/env sh
set -eu

SCRIPT_DIR="$(CDPATH= cd -- "$(dirname "$0")" && pwd)"
BIN_NAME="raf_mem_core"
DEFAULT_BIN="$SCRIPT_DIR/../build/bin/$BIN_NAME"
BIN="${RAF_MEM_CORE_BIN:-$DEFAULT_BIN}"

if [ ! -x "$BIN" ] && command -v "$BIN_NAME" >/dev/null 2>&1; then
  BIN="$(command -v "$BIN_NAME")"
fi

if [ ! -x "$BIN" ]; then
  echo "Binário $BIN não encontrado. Rode scripts/build_static.sh primeiro ou exporte RAF_MEM_CORE_BIN." >&2
  exit 1
fi

exec "$BIN" "$@"
