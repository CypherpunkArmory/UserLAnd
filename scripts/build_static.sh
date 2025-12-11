#!/usr/bin/env bash
set -euo pipefail

# Diretório raiz do repositório (um nível acima da pasta scripts/)
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SRC_DIR="$ROOT_DIR"

# Pasta de saída dos binários
OUT_DIR="${OUT_DIR:-"$ROOT_DIR/build/bin"}"

# Compilador: por padrão, o "cc" nativo (pode ser gcc ou clang)
CC="${CC:-cc}"

# Alvo opcional:
# - Em CI / GitHub, deixamos vazio (target nativo).
# - Em casa, se quiser MUSL estático, você pode chamar, por exemplo:
#     TARGET="--target=x86_64-linux-musl" \
#     CFLAGS="-O2 -pipe -static -fvisibility=hidden" \
#     CC=clang \
#     ./scripts/build_static.sh
TARGET="${TARGET:-}"

# Flags padrão: otimização moderada, sem -static por padrão (mais compatível)
CFLAGS="${CFLAGS:-"-O2 -pipe -fvisibility=hidden"}"

# Flags extras de link, se precisar
LDFLAGS="${LDFLAGS:-}"

mkdir -p "$OUT_DIR"

build() {
  local src="$1" bin_name="$2"
  local out="$OUT_DIR/$bin_name"
  echo "[build] $src -> $out" >&2

  if [[ -n "$TARGET" ]]; then
    # Se TARGET estiver definido (ex: MUSL), inclui no comando
    "$CC" $TARGET $CFLAGS "$SRC_DIR/$src" -o "$out" $LDFLAGS
  else
    # Caso normal: compilação nativa (glibc/dinâmico)
    "$CC" $CFLAGS "$SRC_DIR/$src" -o "$out" $LDFLAGS
  fi
}

echo "🦉 RAFAELIA static/dynamic builder"

if ! command -v "$CC" >/dev/null 2>&1; then
  echo "Compilador '$CC' não encontrado (defina CC ou instale gcc/clang)" >&2
  exit 1
fi

# Aqui você pode adicionar mais fontes se quiser
build raf_cpu_core.c raf_cpu_core
```0
