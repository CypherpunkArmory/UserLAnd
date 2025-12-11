#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
ASSETS_BIN="$SCRIPT_DIR/terminal-term/src/main/assets/bin"

mkdir -p "$ASSETS_BIN"

"$ROOT_DIR/scripts/build_static.sh"

for bin in raf_cpu_core raf_mem_core raf_disk_core; do
  install -m 0755 "$ROOT_DIR/build/bin/$bin" "$ASSETS_BIN/$bin"
done

for wrapper in raf_cpu_core.sh raf_mem_core.sh raf_disk_core.sh; do
  install -m 0755 "$ROOT_DIR/scripts/$wrapper" "$ASSETS_BIN/$wrapper"
done

echo "Copiados raf_*_core para $ASSETS_BIN. Eles serão incluídos no APK e estarão no PATH da sessão do Termux." >&2
