#!/usr/bin/env bash
set -euo pipefail

# Tamanho do arquivo em MiB (default: 64 MiB – rápido)
SIZE_MIB="${1:-64}"
FILE="hash_test_${SIZE_MIB}MiB.bin"
CPU_MHZ=2800.0

echo "[*] Tamanho alvo: ${SIZE_MIB} MiB"

# Gera o arquivo só uma vez
if [ ! -f "$FILE" ]; then
  echo "[*] Gerando arquivo de teste: $FILE (${SIZE_MIB} MiB, /dev/urandom)..."
  dd if=/dev/urandom of="$FILE" bs=1M count="$SIZE_MIB" status=none
else
  echo "[*] Usando arquivo existente: $FILE"
fi

BYTES=$(( SIZE_MIB * 1024 * 1024 ))

bench() {
  local label="$1"
  shift
  local cmd="$*"

  echo
  echo "== $label =="
  echo "[cmd] $cmd"

  local start end elapsed
  start=$(date +%s.%N)
  eval "$cmd" > /dev/null
  end=$(date +%s.%N)

  elapsed=$(awk "BEGIN { print ($end - $start) }")
  local mibs
  mibs=$(awk "BEGIN { if ($elapsed > 0) print $SIZE_MIB / $elapsed; else print 0 }")
  local cpb
  cpb=$(awk "BEGIN { if ($elapsed > 0) print ($elapsed * $CPU_MHZ * 1e6) / $BYTES; else print 0 }")

  echo "Tempo (s)  : $elapsed"
  echo "MiB/s      : $mibs"
  echo "ciclos/byte: $cpb (CPU_MHz=$CPU_MHZ)"
}

# 1) Seu hash (FIBER) – usa o binário já pronto
if [ -x ./fiber_hash_tree ]; then
  bench "FIBER-H (fiber_hash_tree)" "./fiber_hash_tree \"$FILE\" 1024"
else
  echo "[!] ./fiber_hash_tree não encontrado ou não executável – pulando FIBER."
fi

# 2) BLAKE3 (b3sum), se existir
if command -v b3sum >/dev/null 2>&1; then
  bench "BLAKE3 (b3sum)" "b3sum \"$FILE\""
else
  echo "[!] b3sum não encontrado – pulando BLAKE3."
fi

# 3) SHA3-256 (sha3sum), se existir
if command -v sha3sum >/dev/null 2>&1; then
  bench "SHA3-256 (sha3sum)" "sha3sum \"$FILE\""
else
  echo "[!] sha3sum não encontrado – pulando SHA3-256."
fi
