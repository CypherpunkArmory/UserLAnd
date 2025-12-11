#!/usr/bin/env bash
set -euo pipefail

# Uso:
#   ./fiber_speed_compare_cli.sh            # 256 MiB (default)
#   ./fiber_speed_compare_cli.sh 512        # 512 MiB
#
# Requisitos:
#   - ./fiber_hash_tree já compilado no diretório atual
#   - b3sum (BLAKE3) instalado (opcional; se não tiver, ele pula)
#   - sha3sum (SHA3-256) instalado (opcional; se não tiver, ele pula)

SIZE_MIB="\${1:-256}"
TEST_FILE="fiber_speed_test_\${SIZE_MIB}MiB.bin"
CPU_MHZ=2800.0

echo "[*] Tamanho alvo: \${SIZE_MIB} MiB"

if [ ! -f "\$TEST_FILE" ]; then
  echo "[*] Gerando arquivo de teste: \$TEST_FILE (\${SIZE_MIB} MiB, /dev/urandom)..."
  dd if=/dev/urandom of="\$TEST_FILE" bs=1M count="\$SIZE_MIB" status=none
else
  echo "[*] Usando arquivo de teste existente: \$TEST_FILE"
fi

BYTES=\$(( SIZE_MIB * 1024 * 1024 ))

bench() {
  local label="\$1"
  shift
  local cmd="\$*"

  echo
  echo "== \$label =="
  echo "[cmd] \$cmd"

  local start end elapsed
  start=\$(date +%s.%N)
  # executa comando jogando saída fora
  eval "\$cmd" > /dev/null
  end=\$(date +%s.%N)

  # calcula tempo, MiB/s e ciclos/byte
  elapsed=\$(awk "BEGIN { print (\$end - \$start) }")
  local mibs
  mibs=\$(awk "BEGIN { if (\$elapsed > 0) print $SIZE_MIB / \$elapsed; else print 0 }")
  local cpb
  cpb=\$(awk "BEGIN { if (\$elapsed > 0) print (\$elapsed * $CPU_MHZ * 1e6) / $BYTES; else print 0 }")

  echo "Tamanho    : \${SIZE_MIB} MiB"
  echo "Tempo (s)  : \$elapsed"
  echo "MiB/s      : \$mibs"
  echo "ciclos/byte: \$cpb (CPU_MHz=\$CPU_MHZ)"
}

# 1) FIBER-H TREE (seu binário)
if [ -x ./fiber_hash_tree ]; then
  bench "FIBER-H_TREE (file+tree)" "./fiber_hash_tree \"\$TEST_FILE\" 1024"
else
  echo "[!] ./fiber_hash_tree não encontrado ou não executável – pulando FIBER."
fi

# 2) BLAKE3 (b3sum), se existir
if command -v b3sum >/dev/null 2>&1; then
  bench "BLAKE3 (b3sum)" "b3sum \"\$TEST_FILE\""
else
  echo "[!] b3sum não encontrado – BLAKE3 não será medido."
fi

# 3) SHA3-256 (sha3sum), se existir
if command -v sha3sum >/dev/null 2>&1; then
  bench "SHA3-256 (sha3sum)" "sha3sum \"\$TEST_FILE\""
else
  echo "[!] sha3sum não encontrado – SHA3-256 não será medido."
fi
