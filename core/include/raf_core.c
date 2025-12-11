/*
 * =============================================================================
 * 🌌 RAFAELIA CORE IMPLEMENTATION (v1000.c)
 * =============================================================================
 * ROLE : Implementação do ABSOLUTE CORE (sem UI, sem bench)
 * =============================================================================
 */

#define _POSIX_C_SOURCE 200809L

#include "raf_core.h"
#include <stdlib.h>
#include <string.h>

// -----------------------------------------------------------------------------
// GENESIS – inicializa contexto + config padrão
// -----------------------------------------------------------------------------
int raf_genesis(AbsoluteMatrix *ctx, RafConfig *cfg) {
    if (!ctx || !cfg) return -1;

    memset(ctx, 0, sizeof(*ctx));
    ctx->state_crc = 0x52414641u;   // "RAFA"
    ctx->rng_state = 0xCAFEBABEu;

    cfg->dim_n        = 1000u;
    cfg->phys_ram_mb  = 0u;
    cfg->active_cores = 4u;
    cfg->decay_rate   = 0.985f;
    cfg->inject_power = 10.0f;

    return 0;
}

// -----------------------------------------------------------------------------
// ALOCAÇÃO – universo físico alinhado (heap)
// -----------------------------------------------------------------------------
int raf_alloc_universe(AbsoluteMatrix *ctx, RafConfig *cfg, uint64_t mb_target) {
    if (!ctx || !cfg || mb_target == 0u) return -1;

    if (ctx->tensor) {
        free(ctx->tensor);
        ctx->tensor = NULL;
    }

    uint64_t bytes = mb_target * 1024u * 1024u;
    uint64_t cells = bytes / sizeof(float);
    if (cells == 0u) return -1;

    // Próxima potência de 2 para habilitar máscara bitwise (&)
    uint64_t pow2 = 1u;
    while (pow2 < cells) pow2 <<= 1u;

    size_t final_bytes = (size_t)(pow2 * sizeof(float));
    void *ptr = NULL;
    if (posix_memalign(&ptr, CACHE_LINE, final_bytes) != 0) {
        return -1;
    }

    ctx->tensor = (float *)ptr;
    memset(ctx->tensor, 0, final_bytes);

    ctx->phys_cells = (uint32_t)pow2;
    ctx->phys_mask  = (uint32_t)(pow2 - 1u);
    cfg->phys_ram_mb = final_bytes / (1024u * 1024u);

    return 0;
}

// -----------------------------------------------------------------------------
// INJEÇÃO – evento virtual → célula física
// -----------------------------------------------------------------------------
void raf_inject(AbsoluteMatrix *ctx, const RafConfig *cfg,
                uint64_t vx, uint64_t vy, uint64_t vz, float energy) {
    if (!ctx || !cfg || !ctx->tensor || cfg->active_cores == 0u) return;

    uint32_t p_idx = raf_holo_map(vx, vy, vz, ctx->phys_mask);
    uint32_t core  = p_idx % cfg->active_cores;
    uint32_t head  = ctx->cursor_heads[core];

    if (head < 4096u) {
        ctx->cursors[core][head] = p_idx;
        ctx->cursor_heads[core]  = head + 1u;
    }

    ctx->tensor[p_idx] += energy;
    ctx->global_energy += energy;

    ctx->state_crc = raf_fast_crc32(ctx->state_crc, &p_idx, sizeof(uint32_t));
}

// -----------------------------------------------------------------------------
// FÍSICA SOC – processa todas as lanes
// -----------------------------------------------------------------------------
void raf_process_lanes(AbsoluteMatrix *ctx, const RafConfig *cfg) {
    if (!ctx || !cfg || !ctx->tensor || cfg->active_cores == 0u) return;

    ctx->ops_metric = 0u;

    for (uint32_t c = 0u; c < cfg->active_cores; ++c) {
        uint32_t head = ctx->cursor_heads[c];

        for (uint32_t k = 0u; k < head; ++k) {
            uint32_t idx = ctx->cursors[c][k] & ctx->phys_mask;
            float val = ctx->tensor[idx];

            if (val > 4.0f) {
                float excess = val * 0.5f;
                ctx->tensor[idx] = val - excess;

                uint32_t l = (idx - 1u) & ctx->phys_mask;
                uint32_t r = (idx + 1u) & ctx->phys_mask;

                ctx->tensor[l] += excess * 0.25f;
                ctx->tensor[r] += excess * 0.25f;

                ctx->ops_metric++;
            }

            ctx->tensor[idx] *= cfg->decay_rate;
        }

        ctx->cursor_heads[c] = 0u;
    }

    ctx->cycle_count++;
}

// -----------------------------------------------------------------------------
// DESTROY – libera heap e zera metadados básicos
// -----------------------------------------------------------------------------
void raf_destroy(AbsoluteMatrix *ctx) {
    if (!ctx) return;

    if (ctx->tensor) {
        free(ctx->tensor);
        ctx->tensor = NULL;
    }

    ctx->phys_cells = 0u;
    ctx->phys_mask  = 0u;
}
