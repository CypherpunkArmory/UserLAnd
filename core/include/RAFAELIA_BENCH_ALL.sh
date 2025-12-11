#!/usr/bin/env bash
set -euo pipefail

CFLAGS="-O3 -march=native -Werror"

log() { printf '[RAFAELIA_BENCH] %s\n' "$*"; }

build() {
  local src="$1" bin="$2"
  if [[ -f "$src" ]]; then
    log "BUILD  $src -> $bin"
    gcc $CFLAGS "$src" -o "$bin" -lm
  else
    log "SKIP   $src (não encontrado)"
  fi
}

matrix_send() {
  local tag="$1" hash="$2"
  if [[ -x ./raf_matrix ]]; then
    log "MATRIX $tag $hash"
    ./raf_matrix "$tag $hash" || log "MATRIX falhou (ignorado)"
  else
    log "MATRIX binário ./raf_matrix não encontrado, pulando"
  fi
}

trinity_svg() {
  local log_file="$1" svg_out="$2"
  if [[ -x ./trinity_core ]]; then
    log "TRINITY ingest -> $svg_out"
    cat "$log_file" | ./trinity_core --ingest --svg > "$svg_out" || \
      log "TRINITY falhou (ignorado)"
  else
    log "TRINITY ./trinity_core não encontrado, pulando"
  fi
}

# ---------------------------
# 1) COMPILAÇÃO DE TODOS OS CORES
# ---------------------------

build rafaelia_toroidal_soc_stable.c raf_toroid
build rafaelia_matrix_soc.c          raf_mtx_soc
build rafaelia_hyper_core.c          raf_hyper_core
build rafaelia_hyper_core_v800.c     raf_hyper_core_v800
build rafaelia_infinite_core.c       raf_infinite_core
build rafaelia_infinite_tuner.c      raf_infinite_tuner
build rafaelia_omni_core.c           raf_omni_core
build raf_vector_core.c              raf_vec
build rafaelia_matrix_core.c         raf_mtx_core
build rafaelia_trinity_ultimate.c   raf_trinity_ultimate

# ---------------------------
# 2) BENCHES INDIVIDUAIS
# ---------------------------

bench_toroid() {
  if [[ ! -x ./raf_toroid ]]; then return; fi
  log "RUN    raf_toroid --bench 10"
  ./raf_toroid --bench 10 | tee toro_bench.log
  local hash
  hash=$(grep 'Final CRC' toro_bench.log | awk '{print $4}')
  [[ -n "${hash:-}" ]] && matrix_send "TOROIDAL_BENCH_CRC" "$hash"
  trinity_svg toro_bench.log toro_memory.svg
}

bench_matrix_soc() {
  if [[ ! -x ./raf_mtx_soc ]]; then return; fi
  log "RUN    raf_mtx_soc --bench 20"
  ./raf_mtx_soc --bench 20 | tee matrix_soc_bench.log
  local hash
  hash=$(grep 'Hash' matrix_soc_bench.log | awk '{print $3}')
  [[ -n "${hash:-}" ]] && matrix_send "MATRIX_SOC_HASH" "$hash"
  trinity_svg matrix_soc_bench.log matrix_soc_memory.svg
}

bench_hyper_core() {
  if [[ ! -x ./raf_hyper_core ]]; then return; fi
  log "RUN    raf_hyper_core --bench 20"
  ./raf_hyper_core --bench 20 | tee hyper_core_bench.log
  local hash
  hash=$(grep 'Hash' hyper_core_bench.log | awk '{print $3}')
  [[ -n "${hash:-}" ]] && matrix_send "HYPER_CORE_HASH" "$hash"
  trinity_svg hyper_core_bench.log hyper_core_memory.svg
}

bench_hyper_core_v800() {
  if [[ ! -x ./raf_hyper_core_v800 ]]; then return; fi
  # v800 normalmente roda em modo --bridge; aqui só damos um ping standalone
  log "RUN    raf_hyper_core_v800 (modo demo standalone)"
  ./raf_hyper_core_v800 2> /dev/null || true
}

bench_infinite_core() {
  if [[ ! -x ./raf_infinite_core ]]; then return; fi
  log "RUN    raf_infinite_core --bench 5000 8"
  ./raf_infinite_core --bench 5000 8 | tee infinite_core_bench.log
  local hash
  hash=$(grep 'Hash' infinite_core_bench.log | awk '{print $3}')
  [[ -n "${hash:-}" ]] && matrix_send "INFINITE_CORE_HASH" "$hash"
  trinity_svg infinite_core_bench.log infinite_core_memory.svg
}

bench_infinite_tuner() {
  if [[ ! -x ./raf_infinite_tuner ]]; then return; fi
  log "RUN    raf_infinite_tuner --bench 5000 8"
  ./raf_infinite_tuner --bench 5000 8 | tee infinite_tuner_bench.log
  local hash
  hash=$(grep 'Hash' infinite_tuner_bench.log | awk '{print $3}')
  [[ -n "${hash:-}" ]] && matrix_send "INFINITE_TUNER_HASH" "$hash"
  trinity_svg infinite_tuner_bench.log infinite_tuner_memory.svg
}

bench_omni_core() {
  if [[ ! -x ./raf_omni_core ]]; then return; fi
  # Atenção: dependendo da versão, --bench pode não existir.
  # Se travar, comente essa função.
  log "RUN    raf_omni_core --bench 5000 8"
  ./raf_omni_core --bench 5000 8 | tee omni_bench.log || log "omni_core bench retornou erro (talvez modo interativo)"
  local hash
  hash=$(grep 'Hash' omni_bench.log 2>/dev/null | awk '{print $3}')
  [[ -n "${hash:-}" ]] && matrix_send "OMNI_CORE_HASH" "$hash"
  if [[ -f omni_bench.log ]]; then
    trinity_svg omni_bench.log omni_core_memory.svg
  fi
}

bench_vector_core() {
  if [[ ! -x ./raf_vec ]]; then return; fi
  # Aqui assumimos que você tem um modo --bench similar ao log que mostrou.
  log "RUN    raf_vec --bench 10000"
  ./raf_vec --bench 10000 | tee vec_bench.log || log "raf_vec --bench não implementado, apenas compilado"
  if grep -q 'BEMCH' vec_bench.log 2>/dev/null; then
    local hash
    hash=$(grep 'Hash' vec_bench.log | awk '{print $3}')
    [[ -n "${hash:-}" ]] && matrix_send "VECTOR_CORE_HASH" "$hash"
    trinity_svg vec_bench.log vector_core_memory.svg
  fi
}

bench_matrix_core() {
  if [[ ! -x ./raf_mtx_core ]]; then return; fi
  # Dependendo da versão, pode ser só uma simulação simples
  log "RUN    raf_mtx_core (sem flags, modo padrão)"
  ./raf_mtx_core | tee matrix_core_run.log || true
}

bench_trinity_ultimate() {
  if [[ ! -x ./raf_trinity_ultimate ]]; then return; fi
  log "RUN    raf_trinity_ultimate (modo padrão)"
  ./raf_trinity_ultimate | tee trinity_ultimate_run.log || true
}

bench_all() {
  bench_toroid
  bench_matrix_soc
  bench_hyper_core
  bench_hyper_core_v800
  bench_infinite_core
  bench_infinite_tuner
  bench_omni_core
  bench_vector_core
  bench_matrix_core
  bench_trinity_ultimate
}

log "=== RAFAELIA BENCH SUITE START ==="
bench_all
log "=== RAFAELIA BENCH SUITE DONE  ==="

