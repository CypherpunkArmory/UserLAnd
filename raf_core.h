/*
 * =============================================================================
 * 🌌 RAFAELIA CORE HEADER (v1000.h)
 * =============================================================================
 * ROLE : Estruturas + Math inline + Prototypes do ABSOLUTE CORE
 * ARCH : C11 | Cache Aligned | Bitwise Mask | Xorshift RNG
 * =============================================================================
 */

#ifndef RAF_CORE_H
#define RAF_CORE_H

#include <stdint.h>
#include <stddef.h>

#define MAX_CORES  128
#define CACHE_LINE 64

// -----------------------------------------------------------------------------
// CONFIGURAÇÃO DE EXECUÇÃO
// -----------------------------------------------------------------------------
typedef struct {
    uint64_t dim_n;          // Dimensão virtual (N^3 conceitual)
    uint64_t phys_ram_mb;    // RAM física alocada (MB)
    uint32_t active_cores;   // Lanes lógicos de processamento
    float    decay_rate;     // Taxa de resfriamento
    float    inject_power;   // Energia padrão por injeção
} RafConfig;

// -----------------------------------------------------------------------------
// ESTADO DA MATRIZ ABSOLUTA (Holográfica / Físico)
// -----------------------------------------------------------------------------
typedef struct __attribute__((aligned(CACHE_LINE))) {
    float    *tensor;         // Heap alinhado (células físicas)
    uint32_t phys_mask;       // (phys_cells - 1) para & bitwise
    uint32_t phys_cells;      // Número de células físicas (potência de 2)

    // Lanes / cursores ativos
    uint32_t cursors[MAX_CORES][4096];
    uint32_t cursor_heads[MAX_CORES];

    // Estado atemporal
    uint32_t state_crc;       // “Relógio” CRC
    uint32_t rng_state;       // RNG interno (xorshift)
    uint64_t cycle_count;     
    uint64_t ops_metric;      // Operações SOC no último tick
    float    global_energy;   // Energia total no campo
} AbsoluteMatrix;

// -----------------------------------------------------------------------------
// MATH KERNEL (INLINE PARA PERFORMANCE MÁXIMA)
// -----------------------------------------------------------------------------

// Xorshift32 – PRNG rápido e leve
static inline uint32_t raf_xorshift32(uint32_t *state) {
    uint32_t x = *state;
    x ^= x << 13;
    x ^= x >> 17;
    x ^= x << 5;
    *state = x;
    return x;
}

// CRC32 simplificado (sem tabela, cache-friendly)
static inline uint32_t raf_fast_crc32(uint32_t prev, const void *data, size_t len) {
    const uint8_t *p = (const uint8_t *)data;
    uint32_t c = ~prev;
    while (len--) {
        c ^= *p++;
        for (int k = 0; k < 8; ++k) {
            c = (c >> 1) ^ (0xEDB88320u & (uint32_t)-(int32_t)(c & 1u));
        }
    }
    return ~c;
}

// Hash espacial FNV-1a -> índice físico (via máscara bitwise)
static inline uint32_t raf_holo_map(uint64_t x, uint64_t y, uint64_t z, uint32_t mask) {
    uint32_t h = 2166136261u;
    h = (h ^ (uint32_t)x) * 16777619u;
    h = (h ^ (uint32_t)y) * 16777619u;
    h = (h ^ (uint32_t)z) * 16777619u;
    return h & mask;
}

// -----------------------------------------------------------------------------
// PROTÓTIPOS DO ENGINE
// -----------------------------------------------------------------------------

// Inicializa estruturas e valores padrão (não aloca RAM ainda)
int  raf_genesis(AbsoluteMatrix *ctx, RafConfig *cfg);

// Aloca universo físico alinhado (MB → pot. de 2, com máscara)
int  raf_alloc_universe(AbsoluteMatrix *ctx, RafConfig *cfg, uint64_t mb_target);

// Injeta energia em coordenadas virtuais (vx,vy,vz)
void raf_inject(AbsoluteMatrix *ctx, const RafConfig *cfg,
                uint64_t vx, uint64_t vy, uint64_t vz, float energy);

// Processa um “tick” de física SOC (todas as lanes)
void raf_process_lanes(AbsoluteMatrix *ctx, const RafConfig *cfg);

// Libera heap e zera metadados críticos
void raf_destroy(AbsoluteMatrix *ctx);

#endif // RAF_CORE_H
