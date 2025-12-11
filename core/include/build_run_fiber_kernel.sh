#!/usr/bin/env bash
set -euo pipefail

CC="${CC:-clang}"
CFLAGS="${CFLAGS:--O3 -march=native -Wall -Wextra}"

echo "==============================================="
echo " RAFAELIA – FIBER-H BUILD & TEST SUITE"
echo "==============================================="
echo "[*] CC    = $CC"
echo "[*] CFLAGS= $CFLAGS"
echo

build_bin () {
  local src="$1"
  local out="$2"
  if [ -f "$src" ]; then
    echo "[*] Compilando $src -> $out ..."
    $CC $CFLAGS -o "$out" "$src"
    echo "[OK] $out"
    echo
  else
    echo "[!] Fonte $src não encontrado – pulando."
    echo
  fi
}

# 1) Kernel de decisão / integridade
build_bin fiber_kernel.c fiber_kernel

# 2) Benchmark Lógica 5000x
build_bin bench_fiber_lanes6_mode.c bench_fiber_lanes6_mode

# 3) Hash tree standalone
build_bin fiber_hash_tree.c fiber_hash_tree

# 4) Avalanche + Monte Carlo + Speed + Integridade (núcleo FIBER)
build_bin fiber_avalanche_montecarlo.c fiber_avalanche_montecarlo

# 5) Stress lab (se existir)
build_bin fiber_stress_lab.c fiber_stress_lab

echo "==============================================="
echo " TESTES RÁPIDOS"
echo "==============================================="

# Auto-testes kernel
if [ -x ./fiber_kernel ]; then
  echo "[*] Rodando fiber_kernel (auto-testes T1/T3)..."
  ./fiber_kernel || echo "[!] fiber_kernel retornou erro."
  echo
fi

# Benchmark lanes6
if [ -x ./bench_fiber_lanes6_mode ]; then
  echo "[*] Rodando bench_fiber_lanes6_mode (BLOCK_SIZE=1024)..."
  ./bench_fiber_lanes6_mode || echo "[!] bench_fiber_lanes6_mode retornou erro."
  echo
fi

# Hash tree em cima do próprio stress_lab, se existir
if [ -x ./fiber_hash_tree ] && [ -f fiber_stress_lab.c ]; then
  echo "[*] Hash tree de fiber_stress_lab.c com B=1024..."
  ./fiber_hash_tree fiber_stress_lab.c 1024 || echo "[!] fiber_hash_tree retornou erro."
  echo
fi

# Avalanche / Monte Carlo / Speed / Integridade
if [ -x ./fiber_avalanche_montecarlo ]; then
  echo "[*] Rodando fiber_avalanche_montecarlo all ..."
  ./fiber_avalanche_montecarlo all || echo "[!] fiber_avalanche_montecarlo retornou erro."
  echo
fi

echo "==============================================="
echo " SUITE FIBER-H – BUILD & TEST FINALIZADO"
echo "==============================================="
