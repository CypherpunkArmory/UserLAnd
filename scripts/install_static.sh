#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
PREFIX="${PREFIX:-"$ROOT_DIR/build/install"}"
BIN_DEST="$PREFIX/bin"

mkdir -p "$BIN_DEST"

"$SCRIPT_DIR/build_static.sh"

for bin in raf_cpu_core raf_mem_core raf_disk_core; do
  install -m 0755 "$ROOT_DIR/build/bin/$bin" "$BIN_DEST/$bin"
done

for wrapper in raf_cpu_core.sh raf_mem_core.sh raf_disk_core.sh; do
  install -m 0755 "$SCRIPT_DIR/$wrapper" "$BIN_DEST/$wrapper"
done

echo "Instalado em $BIN_DEST. Adicione \"$BIN_DEST\" ao PATH para usar os utilitários raf_*_core." >&2
