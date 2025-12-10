/*
 * =============================================================================
 * 🌌 RAFAELIA ABSOLUTE CORE – CLI / BENCH (v1000)
 * =============================================================================
 * Uso:
 *   ./raf_absolute_core --bench <steps> <inj_per_step>
 * =============================================================================
 */

#define _POSIX_C_SOURCE 200809L

#include "raf_core.h"
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <time.h>
#include <inttypes.h>

// Timer em segundos (double)
static double raf_now_seconds(void) {
    struct timespec ts;
    if (clock_gettime(CLOCK_MONOTONIC, &ts) != 0) {
        return 0.0;
    }
    return (double)ts.tv_sec + (double)ts.tv_nsec / 1e9;
}

static int raf_run_bench(uint32_t steps, uint32_t inj_per_step) {
    AbsoluteMatrix ctx;
    RafConfig cfg;

    if (raf_genesis(&ctx, &cfg) != 0) {
        fprintf(stderr, "GENESIS failed.\n");
        return 1;
    }

    // 64 MB como padrão (igual aos testes anteriores)
    if (raf_alloc_universe(&ctx, &cfg, 64u) != 0) {
        fprintf(stderr, "Alloc universe failed.\n");
        return 1;
    }

    double t0 = raf_now_seconds();
    uint64_t total_inj  = (uint64_t)steps * (uint64_t)inj_per_step;
    uint64_t total_ops  = 0u;

    for (uint32_t s = 0u; s < steps; ++s) {
        for (uint32_t j = 0u; j < inj_per_step; ++j) {
            uint64_t vx = ctx.state_crc;
            uint64_t vy = ctx.state_crc >> 8;
            uint64_t vz = (uint64_t)s;
            raf_inject(&ctx, &cfg, vx, vy, vz, cfg.inject_power);
        }
        raf_process_lanes(&ctx, &cfg);
        total_ops += ctx.ops_metric;
    }

    double t1 = raf_now_seconds();
    double elapsed = (t1 > t0) ? (t1 - t0) : 0.0;
    if (elapsed <= 0.0) elapsed = 1e-9;

    double steps_per_s        = (double)steps     / elapsed;
    double events_per_s       = (double)total_inj / elapsed;
    double soc_cells_per_s    = (double)total_ops / elapsed;

    printf("SYS: RAM Aligned. Cells: %u | Mask: %08X\n",
           ctx.phys_cells, ctx.phys_mask);
    printf("[BENCH] RAFAELIA ABSOLUTE CORE\n");
    printf("[BENCH] Virtual Dim     : %" PRIu64 "^3\n", cfg.dim_n);
    printf("[BENCH] Physical Cells  : %u\n", ctx.phys_cells);
    printf("[BENCH] Steps           : %" PRIu32 "\n", steps);
    printf("[BENCH] Inj/step        : %" PRIu32 "\n", inj_per_step);
    printf("[BENCH] Total Inj       : %" PRIu64 "\n", total_inj);
    printf("[BENCH] Elapsed         : %.6f s\n", elapsed);
    printf("[BENCH] Steps/s         : %.2f\n", steps_per_s);
    printf("[BENCH] Virtual events/s: %.2f\n", events_per_s);
    printf("[BENCH] SOC-cells/s     : %.2f\n", soc_cells_per_s);
    printf("[BENCH] Energy          : %.4f\n", ctx.global_energy);
    printf("[BENCH] Hash            : %08X\n", ctx.state_crc);

    raf_destroy(&ctx);
    return 0;
}

int main(int argc, char *argv[]) {
    if (argc == 4 && strcmp(argv[1], "--bench") == 0) {
        uint32_t steps = (uint32_t) strtoul(argv[2], NULL, 10);
        uint32_t inj   = (uint32_t) strtoul(argv[3], NULL, 10);

        if (steps == 0u || inj == 0u) {
            fprintf(stderr, "Invalid bench args.\n");
            return 1;
        }
        return raf_run_bench(steps, inj);
    }

    fprintf(stderr,
            "Usage: %s --bench <steps> <inj_per_step>\n",
            argv[0]);
    return 1;
}
