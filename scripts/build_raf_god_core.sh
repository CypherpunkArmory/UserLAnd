#!/usr/bin/env bash
set -euo pipefail

# Build the RAFAELIA "GOD" core example with aggressive-but-portable flags.
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SRC="${ROOT}/raf_god_core.c"
OUT_DIR="${ROOT}/build/bin"
OUT_BIN="${OUT_DIR}/raf_god_core_example"

mkdir -p "${OUT_DIR}"

: "${CC:=clang}"
CFLAGS=(
  -std=c11
  -Wall -Wextra -Werror -pedantic
  -O3 -DNDEBUG
  -D_POSIX_C_SOURCE=200809L
  -fvisibility=hidden
  -march=native -mtune=native
)

"${CC}" "${CFLAGS[@]}" -o "${OUT_BIN}" "${SRC}" -lm

cat <<MSG
Built ${OUT_BIN}
Run interativamente: ${OUT_BIN}
Bridge JSON (--bridge): ${OUT_BIN} --bridge
MSG
