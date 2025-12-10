#!/usr/bin/env sh
set -eu
SCRIPT_DIR="$(CDPATH= cd -- "$(dirname "$0")" && pwd)"
BIN="${RAF_CPU_CORE_BIN:-$SCRIPT_DIR/../build/bin/raf_cpu_core}"

if [ ! -x "$BIN" ]; then
  echo "Binário $BIN não encontrado. Rode scripts/build_static.sh primeiro." >&2
  exit 1
fi

exec "$BIN" "$@"
