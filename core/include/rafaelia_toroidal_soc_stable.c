/*
 * ======================================================================================
 * 🌀 RAFAELIA TOROIDAL SOC (System-on-Chip Simulation) – STABLE + BENCH
 * ======================================================================================
 * ARCH: C11 | Linear Memory | CRC Task Stacking | No Dependencies beyond libm
 * LOGIC: Torus Topology -> Adaptive Threshold -> Avalanche -> Dissipation
 * AUTHOR: Rafael & Co-Creative Engine
 * LICENSE: RAFCODE-Φ
 * ======================================================================================
 */

#include <stdio.h>
#include <stdlib.h>
#include <math.h>
#include <stdint.h>
#include <string.h>
#include <time.h>

/* --- GLOBAL CONFIG (compile-time) ---------------------------------------- */

#define SIZE        100             /* Grid: 100 x 100 x 100 */
#define TOTAL_CELLS (SIZE * SIZE * SIZE)
#define DEFAULT_STEPS 30
#define R_MAJOR     20.0f
#define THICKNESS   14.0f

/* --- GLOBAL STATE -------------------------------------------------------- */

/* Single linear buffer (no malloc, cache-friendly) */
static float T[TOTAL_CELLS];
static uint32_t GLOBAL_CRC = 0xFFFFFFFF;

/* --- CRC32 KERNEL -------------------------------------------------------- */
/* Bare-metal CRC32 (no lookup table, low memory footprint) */

static uint32_t crc32_step(uint32_t crc, const void *data, size_t len) {
    const uint8_t *p = (const uint8_t *)data;
    crc = ~crc;
    while (len--) {
        crc ^= *p++;
        for (int i = 0; i < 8; i++) {
            crc = (crc >> 1) ^ (0xEDB88320u & (-(int32_t)(crc & 1u)));
        }
    }
    return ~crc;
}

/* Push a single float (energy_delta, avalanche count, etc.) into global CRC */
static void stack_execution_bit(float energy_delta) {
    GLOBAL_CRC = crc32_step(GLOBAL_CRC, &energy_delta, sizeof(float));
}

/* --- TOROIDAL INITIALIZATION -------------------------------------------- */

static void init_toroid(void) {
    float cx = SIZE / 2.0f;
    float cy = SIZE / 2.0f;
    float cz = SIZE / 2.0f;

    GLOBAL_CRC = 0xFFFFFFFFu; /* Reset CRC at genesis */

    float *ptr = T;
    for (int x = 0; x < SIZE; x++) {
        float dx = x - cx;
        float dx2 = dx * dx;

        for (int y = 0; y < SIZE; y++) {
            float dy = y - cy;
            float r_xy = sqrtf(dx2 + dy * dy);
            float tube_dist_base = r_xy - R_MAJOR;
            float tube_dist_sq = tube_dist_base * tube_dist_base;

            for (int z = 0; z < SIZE; z++) {
                float dz = z - cz;
                float d = sqrtf(tube_dist_sq + dz * dz);
                *ptr = expf(-(d * d) / (2.0f * THICKNESS * THICKNESS));
                ptr++;
            }
        }
    }
}

/* --- STATISTICS --------------------------------------------------------- */

static void calc_stats(double *mean, double *std_dev, double *sum_total) {
    double sum = 0.0;
    double sum_sq = 0.0;

    for (int i = 0; i < TOTAL_CELLS; i++) {
        double val = (double) T[i];
        sum += val;
        sum_sq += val * val;
    }

    *sum_total = sum;
    *mean = sum / (double) TOTAL_CELLS;
    double variance = (sum_sq / (double) TOTAL_CELLS) - (*mean * *mean);
    if (variance < 0.0) variance = 0.0;
    *std_dev = sqrt(variance);
}

/* --- SOC STEP (SINGLE ITERATION) ---------------------------------------- */
/* Returns number of avalanches in this step. */

static int soc_step(void) {
    double mean, std, current_energy;
    calc_stats(&mean, &std, &current_energy);

    /* Adaptive threshold */
    float threshold = (float) (mean + 0.8 * std);
    int avalanches = 0;

    for (int idx = 0; idx < TOTAL_CELLS; idx++) {
        if (T[idx] > threshold) {
            /* Supercritical cell: loses part of its energy */
            T[idx] -= (threshold * 0.5f);

            /* Reconstruct coordinates for 3D neighborhood */
            int z = idx % SIZE;
            int y = (idx / SIZE) % SIZE;
            int x = idx / (SIZE * SIZE);

            for (int dx = -1; dx <= 1; dx++) {
                int nx = x + dx;
                if (nx < 0 || nx >= SIZE) continue;

                for (int dy = -1; dy <= 1; dy++) {
                    int ny = y + dy;
                    if (ny < 0 || ny >= SIZE) continue;

                    for (int dz = -1; dz <= 1; dz++) {
                        int nz = z + dz;
                        if (nz < 0 || nz >= SIZE) continue;
                        if (dx == 0 && dy == 0 && dz == 0) continue; /* self */

                        int n_idx = (nx * SIZE * SIZE) + (ny * SIZE) + nz;
                        T[n_idx] += threshold * 0.02f;
                    }
                }
            }
            avalanches++;
        }
    }

    /* Global dissipation */
    for (int i = 0; i < TOTAL_CELLS; i++) {
        T[i] *= 0.995f;
    }

    /* Stack avalanche count into CRC as time-hologram */
    stack_execution_bit((float) avalanches);

    return avalanches;
}

/* --- UTILS: TIMER -------------------------------------------------------- */

static double now_seconds(void) {
    return (double) clock() / (double) CLOCKS_PER_SEC;
}

/* --- RUN MODES ----------------------------------------------------------- */

static void run_simulation(int steps) {
    printf("=== RAFAELIA OMEGA — TOROIDAL SOC (C-Core) ===\n");
    printf("Config: %dx%dx%d | Mem: %.2f MB | Steps: %d\n",
           SIZE, SIZE, SIZE,
           (float) (TOTAL_CELLS * sizeof(float)) / 1024.0f / 1024.0f,
           steps);

    init_toroid();

    double initial_sum, m, s;
    calc_stats(&m, &s, &initial_sum);
    printf("Energia Inicial: %.4f\n", initial_sum);
    printf("CRC Inicial    : %08X\n", GLOBAL_CRC);

    int total_avalanches = 0;

    for (int i = 0; i < steps; i++) {
        int av = soc_step();
        total_avalanches += av;

        if (i % 5 == 0) {
            printf(" [Step %02d] Avalanches: %d | CRC: %08X\n",
                   i, av, GLOBAL_CRC);
        }
    }

    double final_sum, f_mean, f_std;
    calc_stats(&f_mean, &f_std, &final_sum);

    float min = 1e9f, max = -1e9f;
    for (int i = 0; i < TOTAL_CELLS; i++) {
        if (T[i] < min) min = T[i];
        if (T[i] > max) max = T[i];
    }

    printf("\n=== RESULTADOS FINAIS ===\n");
    printf("Energia Final  : %.4f\n", final_sum);
    printf("Delta Energia  : %.4f\n", final_sum - initial_sum);
    printf("Avalanches Tot : %d\n", total_avalanches);
    printf("Estado Medio   : %.6f\n", f_mean);
    printf("Maximo         : %.6f\n", max);
    printf("Minimo         : %.6f\n", min);
    printf("CRC Assinatura : %08X (Unique Execution Hash)\n", GLOBAL_CRC);
}

/* Simple benchmark: measure steps/s and cell-updates/s */
static void run_benchmark(int steps) {
    printf("[BENCH] RAFAELIA TOROIDAL SOC\n");
    printf("[BENCH] Grid = %dx%dx%d (cells = %d), steps = %d\n",
           SIZE, SIZE, SIZE, TOTAL_CELLS, steps);

    init_toroid();

    double t0 = now_seconds();
    int total_av = 0;
    for (int i = 0; i < steps; i++) {
        total_av += soc_step();
    }
    double t1 = now_seconds();

    double elapsed = t1 - t0;
    if (elapsed <= 0.0) elapsed = 1e-9;

    double steps_per_s = (double) steps / elapsed;
    double cell_updates_per_s = (double) (steps * TOTAL_CELLS) / elapsed;

    printf("[BENCH] Elapsed       : %.6f s\n", elapsed);
    printf("[BENCH] Steps/s       : %.2f\n", steps_per_s);
    printf("[BENCH] CellUpdates/s : %.2f\n", cell_updates_per_s);
    printf("[BENCH] TotalAvalanche: %d\n", total_av);
    printf("[BENCH] Final CRC     : %08X\n", GLOBAL_CRC);
}

/* --- MAIN ---------------------------------------------------------------- */

int main(int argc, char *argv[]) {
    int steps = DEFAULT_STEPS;

    if (argc > 1) {
        if (strcmp(argv[1], "--bench") == 0) {
            if (argc > 2) {
                int s = atoi(argv[2]);
                if (s > 0) steps = s;
            }
            run_benchmark(steps);
            return 0;
        } else if (strcmp(argv[1], "--steps") == 0 && argc > 2) {
            int s = atoi(argv[2]);
            if (s > 0) steps = s;
        }
    }

    run_simulation(steps);
    return 0;
}
