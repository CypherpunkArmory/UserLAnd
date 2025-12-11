#!/data/data/com.termux/files/usr/bin/bash
# ============================================================================
# RAFAELIA :: FIBER-H SUITE ALL (kernel + bench + hash tree)
#  - Compila fiber_kernel.c, bench_fiber_lanes6_mode.c, fiber_hash_tree.c
#  - Executa auto-testes do kernel (T1/T3)
#  - Executa benchmark Lógica 5000x
#  - Calcula FIBER-H HASH TREE em fontes-chave (se existirem)
# ============================================================================

set -euo pipefail

CC="${CC:-clang}"
CFLAGS="${CFLAGS:- -O3 -Wall -Wextra}"

KERNEL_SRC="fiber_kernel.c"
BENCH_SRC="bench_fiber_lanes6_mode.c"
TREE_SRC="fiber_hash_tree.c"

KERNEL_BIN="fiber_kernel"
BENCH_BIN="bench_fiber_lanes6_mode"
TREE_BIN="fiber_hash_tree"

log() { printf '[*] %s\n' "$*"; }
err() { printf '[ERROR] %s\n' "$*" >&2; }

build_bin() {
    local src="$1"
    local out="$2"

    if [[ ! -f "$src" ]]; then
        err "Fonte nao encontrado: $src"
        return 1
    fi

    log "Compilando $src -> $out ..."
    $CC $CFLAGS -o "$out" "$src"
    log "OK: $out"
}

run_kernel_selftest() {
    if [[ ! -x "./$KERNEL_BIN" ]]; then
        err "Binario kernel nao encontrado: $KERNEL_BIN"
        return 1
    fi
    log "Executando auto-testes do kernel (T1/T3)..."
    "./$KERNEL_BIN"
}

run_bench() {
    if [[ ! -x "./$BENCH_BIN" ]]; then
        err "Binario bench nao encontrado: $BENCH_BIN"
        return 1
    fi
    log "Executando benchmark FIBER-H LANES6 (Logica 5000x)..."
    "./$BENCH_BIN"
}

run_hash_tree_on_file() {
    local file="$1"
    local bs="${2:-1024}"

    if [[ ! -x "./$TREE_BIN" ]]; then
        err "Binario hash tree nao encontrado: $TREE_BIN"
        return 1
    fi
    if [[ ! -f "$file" ]]; then
        log "[skip] Arquivo nao encontrado para hash tree: $file"
        return 0
    fi

    log "HASH TREE => $file (B=${bs})"
    "./$TREE_BIN" "$file" "$bs"
}

run_hash_tree_suite() {
    log "==== FIBER-H HASH TREE SUITE ===="

    # Núcleo de hash
    run_hash_tree_on_file "fiber_hash.c"          1024
    run_hash_tree_on_file "fiber_hash_lanes6.c"   1024
    run_hash_tree_on_file "fiber_ecc.c"           1024

    # Núcleo de kernel/bench/stress
    run_hash_tree_on_file "fiber_kernel.c"        1024
    run_hash_tree_on_file "bench_fiber_lanes6_mode.c" 1024
    run_hash_tree_on_file "fiber_stress_lab.c"    1024

    log "==== FIM HASH TREE SUITE ===="
}

main() {
    log "=== RAFAELIA FIBER-H SUITE ALL – START ==="

    # 1) Build
    build_bin "$KERNEL_SRC" "$KERNEL_BIN"
    build_bin "$BENCH_SRC"  "$BENCH_BIN"
    build_bin "$TREE_SRC"   "$TREE_BIN"

    # 2) Kernel self-test
    run_kernel_selftest

    # 3) Bench 5000x
    run_bench

    # 4) Hash tree em fontes chave
    run_hash_tree_suite

    log "=== RAFAELIA FIBER-H SUITE ALL – DONE ==="
}

main "$@"
