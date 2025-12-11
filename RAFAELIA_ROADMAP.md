# RAFAELIA → UserLAnd unificação (versão de engenharia)

Esta rota consolida o pedido de aplicar a filosofia de programação atemporal e de
baixo nível em todas as camadas (C, JNI/Java/Kotlin, Rust, scripts) para reduzir
footprint, aumentar IOPS/throughput e entregar um APK arm64 pronto.

## Fase 0 – Higiene do repositório (imediata)
- **Layout**: `core/` (núcleo C/ASM), `core/include/` (headers), `tools/` (CLIs),
  `app/` (Android/Kotlin/Java/JNI), `scripts/` (bench, debug, wrappers),
  `build/` (rootfs, artefatos, releases).
- **Duplicações**: eliminar árvores duplicadas e arquivos obsoletos.
- **Flags padrão**: `-std=c11 -Wall -Wextra -Werror -D_POSIX_C_SOURCE=200809L`
  para C; `-fvisibility=hidden` em libs; `-Oz`/`-Os` para footprint quando
  pertinente.

## Fase 1 – Núcleo RAFAELIA como biblioteca
- **Artefato**: `core/librafaelia` produz `librafaelia.a/.so` com os kernels
  atuais (`rafaelia_absolute_core`, `raf_dev_debug_perf`, `raf_userland_debug`,
  `raf_cpu_core`, etc.).
- **API mínima**: leitura/normalização de métricas, geração de matrizes de
  severidade, exportação JSON/SVG. Headers em `core/include/raf_core.h`.
- **Hot path**: SoA, bitsets (uint64_t) e vetorização (`__ARM_NEON`/SSE) com
  fallback escalar; nada de alocação/I/O dentro de loops críticos.

## Fase 2 – Runtime RAFAELIA dentro do UserLAnd
- **Rootfs dedicado**: Debian/Ubuntu em `build/rafaelia-rootfs/` com
  `librafaelia` instalada, CLIs (`raf_diag`, `raf_bench`, `raf_status`) e
  scripts no `PATH`. Banner e shell inicial com atalhos RAFAELIA.
- **Distro oficial**: entrada “RAFAELIA Linux” no app, apontando para essa
  rootfs; diretório de troca padrão
  `/sdcard/Android/data/tech.ula/files/rafaelia/` para JSON/SVG/logs.

## Fase 3 – NDK/JNI + Kotlin/Java (baixo nível)
- **Módulo NDK**: `app/src/main/cpp/rafaelia/` com `CMakeLists.txt` apontando
  para `core/`. `-ffunction-sections -fdata-sections -Wl,--gc-sections` para
  reduzir tamanho; `-fno-exceptions -fno-rtti` quando possível.
- **JNI mínima**: `external fun rafCpuSnapshot(): String` e
  `external fun rafDevPerfStep(input: RafInput): RafOutput` chamando a API C.
  Retornos compactos (buffers fixos, CRC embutido) para reduzir GC.
- **UI Dev Mode**: tela Kotlin consumindo JNI para gráficos/alertas; botões de
  bench e exportação para o diretório de troca.

## Fase 4 – Rust e pipelines empilhados
- **FFI compartilhado**: crate Rust fino (`core/rust/raf-ffi`) consumindo
  `librafaelia` via bindgen estável; expor apenas estruturas SoA/bitset para
  evitar cópia.
- **Executáveis Rust**: CLIs de alta IOPS (`raf_stacker_rs`, `raf_matrix_rs`)
  usando `#![no_std]` onde der, `rayon`/`tokio` opcional sob feature flag.
- **Interop JNI**: opcionalmente usar `jni` crate para reduzir cola Java quando
  a lógica ficar majoritariamente em Rust.

## Fase 5 – Afinar IOPS/throughput e footprint
- **Perfilamento disciplinado**: counters de ciclos, `perf stat` nos bins,
  benchmarks automatizados via `RAFAELIA_BENCH_ALL.sh` integrados ao CI.
- **Alinhamento e caches**: buffers `alignas(64)` para matrizes quentes;
  pré-alocação/double-buffer nos pipelines; macros `RAF_UNROLL` para trechos
  críticos; prefetch explícito onde fizer diferença.
- **I/O minimalista**: logs estruturados apenas nas bordas; compressão/CRC
  dedicados; evitar syscalls dentro do núcleo de cálculo.

## Fase 6 – Empacotamento e entrega
- **APK arm64**: `./gradlew assembleRelease` com ABI `arm64-v8a` e dependência
  direta do `librafaelia.so` gerado; stripping de símbolos e `android:extractNativeLibs="false"`.
- **Rootfs**: publicar tarball da distro RAFAELIA junto com o APK; scripts de
  pós-instalação para sincronizar versões de núcleos e CLIs.
- **Observabilidade**: logs RAFAELIA no `logcat` com prefixo `RAF_METRIC`; modo
  developer bloqueado por opt-in explícito do usuário.

## Incrementos contínuos
- Automatizar lint/format (C: `clang-format` opcional; Kotlin: `ktlint`) sem
  alterar hot path. Planejar gates de CI para compilação C/NDK, testes unitários
  Kotlin e sanity dos scripts.
- Manter fallback escalar portável e tabelar benchmarks por arquitetura
  (ARM/x86) para garantir que otimizações alinhadas não quebrem portabilidade.
