#!/usr/bin/env sh
set -eu

SCRIPT_DIR="$(CDPATH= cd -- "$(dirname "$0")" && pwd)"
BIN_NAME="raf_kerneld"
DEFAULT_BIN="$SCRIPT_DIR/../build/bin/$BIN_NAME"
BIN="${RAF_KERNELD_BIN:-$DEFAULT_BIN}"

if [ ! -x "$BIN" ] && command -v "$BIN_NAME" >/dev/null 2>&1; then
  BIN="$(command -v "$BIN_NAME")"
fi

if [ ! -x "$BIN" ]; then
  echo "{\"error\":\"$BIN_NAME not found\",\"hint\":\"run scripts/build_static.sh or export RAF_KERNELD_BIN\",\"bin\":\"$BIN\"}"
  exit 127
fi

exec "$BIN" "$@"
