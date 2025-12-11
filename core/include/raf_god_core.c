/*
 * ====================================================================
 * 🌌 RAFAELIA GOD CORE (v1005 - BENCHMARK EDITION, <80 cols)
 * ====================================================================
 * ARCH : C11 | Monolithic | Aligned | -Werror clean
 * MODES: BBS UI  | JSON --bridge
 * PHYS : Zeta SOC | Zipraf Vec | Phantom Benchmark
 * ====================================================================
 */

#define _POSIX_C_SOURCE 200809L

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <math.h>
#include <time.h>
#include <inttypes.h>

/* CONSTANTES BÁSICAS */
#define MAX_CORES   256
#define CACHE_LINE  64
#define VEC_DIM     64
#define FIB_DEPTH   42

/* CORES ANSI */
#define C_RST  "\x1b[0m"
#define C_RED  "\x1b[31m"
#define C_GRN  "\x1b[32m"
#define C_YEL  "\x1b[33m"
#define C_BLU  "\x1b[34m"
#define C_MAG  "\x1b[35m"
#define C_CYN  "\x1b[36m"
#define C_WHT  "\x1b[37m"
#define C_DIM  "\x1b[2m"

/* CONFIGURAÇÃO DO UNIVERSO */
typedef struct {
    uint64_t dim_n;
    uint64_t phys_ram_mb;
    uint32_t active_cores;
    float    decay_rate;
    float    inject_power;
    float    threshold;
    float    zeta_factor;
} RafConfig;

/* RESULTADO DE BENCHMARK */
typedef struct {
    double   iops;
    double   bandwidth_gb;
    uint64_t score;
} RafBench;

/* ESTADO GLOBAL */
typedef struct __attribute__((aligned(CACHE_LINE))) {
    float    *tensor;
    float    vector_space[VEC_DIM];

    uint32_t phys_mask;
    uint32_t phys_cells;

    uint32_t cursors[MAX_CORES][4096];
    uint32_t cursor_heads[MAX_CORES];

    uint32_t state_crc;
    uint32_t rng_state;

    uint64_t cycle_count;
    uint64_t ops_total;
    uint64_t ops_tick;

    float    global_energy;
    uint64_t fib_seq[FIB_DEPTH];

    RafBench last_bench;
} GodMatrix;

static GodMatrix MTX;
static RafConfig CFG;

/* ========================= KERNEL MATH ========================= */

static inline uint32_t _mix32(uint32_t h) {
    h ^= h >> 16;
    h *= 0x85ebca6b;
    h ^= h >> 13;
    h *= 0xc2b2ae35;
    h ^= h >> 16;
    return h;
}

static inline uint32_t _holo_map(uint64_t x,
                                 uint64_t y,
                                 uint64_t z) {
    uint32_t h = (uint32_t)x ^ (uint32_t)y ^ (uint32_t)z;
    h = _mix32(h);
    return h & MTX.phys_mask;
}

static inline uint32_t _crc(uint32_t prev,
                            const void *data,
                            size_t len) {
    const uint8_t *p = (const uint8_t *)data;
    uint32_t c = ~prev;
    while (len--) {
        c ^= *p++;
        for (int k = 0; k < 4; k++) {
            c = (c >> 1) ^
                (0xEDB88320u & (uint32_t)-(int32_t)(c & 1u));
        }
    }
    return ~c;
}

static inline uint32_t _rng(uint32_t *state) {
    uint32_t x = *state;
    x ^= x << 13;
    x ^= x >> 17;
    x ^= x << 5;
    *state = x;
    return x;
}

static inline float _swiglu(float x) {
    return x / (1.0f + expf(-x));
}

/* ====================== MEMÓRIA & GÊNESE ======================= */

static void _alloc_universe(uint64_t mb_target) {
    if (MTX.tensor) {
        free(MTX.tensor);
        MTX.tensor = NULL;
    }

    if (mb_target < 1) mb_target = 1;
    uint64_t bytes = mb_target * 1024ULL * 1024ULL;
    uint64_t cells = bytes / sizeof(float);

    uint64_t pow2 = 1;
    while (pow2 < cells) pow2 <<= 1;

    size_t final_bytes = (size_t)(pow2 * sizeof(float));

    if (posix_memalign((void **)&MTX.tensor,
                       CACHE_LINE,
                       final_bytes) != 0) {
        fprintf(stderr,
                C_RED "FATAL: alloc failure\n" C_RST);
        exit(1);
    }

    memset(MTX.tensor, 0, final_bytes);
    MTX.phys_cells   = (uint32_t)pow2;
    MTX.phys_mask    = (uint32_t)(pow2 - 1u);
    CFG.phys_ram_mb  = final_bytes / (1024u * 1024u);
    memset(MTX.vector_space, 0, sizeof(MTX.vector_space));
}

static void _genesis(void) {
    memset(&MTX, 0, sizeof(GodMatrix));

    MTX.state_crc = 0x52414641u;
    MTX.rng_state = 0xCAFEBABEu;

    CFG.dim_n        = 1000;
    CFG.active_cores = 4;
    CFG.decay_rate   = 0.99f;
    CFG.inject_power = 5.0f;
    CFG.threshold    = 4.0f;
    CFG.zeta_factor  = 0.001f;

    _alloc_universe(64);

    MTX.fib_seq[0] = 1;
    MTX.fib_seq[1] = 1;
    for (int i = 2; i < FIB_DEPTH; i++) {
        MTX.fib_seq[i] =
            MTX.fib_seq[i - 1] + MTX.fib_seq[i - 2];
    }
}

/* ======================= FÍSICA SOC+ZETA ====================== */

static void _normalize_zipraf(void) {
    for (int i = 0; i < VEC_DIM; i++) {
        float v = MTX.vector_space[i];
        if (v > 1.0f) {
            MTX.vector_space[i] = 1.0f + logf(v);
        } else if (v < 0.001f) {
            MTX.vector_space[i] = 0.001f;
        }
    }
}

static void _inject_energy(uint64_t vx,
                           uint64_t vy,
                           uint64_t vz,
                           float energy) {
    uint32_t p_idx = _holo_map(vx, vy, vz);
    uint32_t core  = p_idx % CFG.active_cores;
    uint32_t head  = MTX.cursor_heads[core];

    if (head < 4096u) {
        MTX.cursors[core][head] = p_idx;
        MTX.cursor_heads[core]++;
    }

    MTX.tensor[p_idx]   += energy;
    MTX.global_energy   += energy;
    MTX.state_crc        = _crc(MTX.state_crc,
                                &p_idx,
                                sizeof(uint32_t));

    uint32_t slot = p_idx & (VEC_DIM - 1u);
    MTX.vector_space[slot] += _swiglu(energy);
}

static void _process_physics(void) {
    MTX.ops_tick = 0;

    for (uint32_t c = 0;
         c < CFG.active_cores;
         c++) {

        uint32_t head = MTX.cursor_heads[c];

        for (uint32_t k = 0; k < head; k++) {
            uint32_t idx =
                MTX.cursors[c][k] & MTX.phys_mask;
            float val = MTX.tensor[idx];

            if (val > CFG.threshold) {
                float excess = val * 0.5f;
                MTX.tensor[idx] -= excess;

                uint32_t l =
                    (idx - 1u) & MTX.phys_mask;
                uint32_t r =
                    (idx + 1u) & MTX.phys_mask;

                MTX.tensor[l] += excess * 0.25f;
                MTX.tensor[r] += excess * 0.25f;
                MTX.ops_tick++;
            }

            MTX.tensor[idx] =
                (MTX.tensor[idx] * CFG.decay_rate) +
                CFG.zeta_factor;
        }

        MTX.cursor_heads[c] = 0;
    }

    MTX.ops_total   += MTX.ops_tick;
    MTX.cycle_count++;
}

/* ===================== BENCHMARK (PHANTOM) ==================== */

static void _run_benchmark(void) {
    printf(C_CYN "\n>> phantom benchmark (1s)...\n"
           C_RST);

    clock_t start = clock();
    uint64_t ops_start = MTX.ops_total;
    double   elapsed   = 0.0;
    uint64_t cycles    = 0;

    while (elapsed < 1.0) {
        for (int k = 0; k < 100; k++) {
            uint32_t r = _rng(&MTX.rng_state);
            _inject_energy(r, r >> 10, r >> 20,
                           10.0f);
        }
        _process_physics();
        cycles++;

        elapsed = (double)(clock() - start)
                  / (double)CLOCKS_PER_SEC;
    }

    uint64_t ops_delta = MTX.ops_total - ops_start;

    MTX.last_bench.iops =
        (double)ops_delta / (elapsed > 0.0
                             ? elapsed
                             : 1.0);
    MTX.last_bench.bandwidth_gb =
        (MTX.last_bench.iops * 3.0 * 4.0) /
        (1024.0 * 1024.0 * 1024.0);

    MTX.last_bench.score =
        (uint64_t)(MTX.last_bench.iops / 1000.0) +
        (uint64_t)(MTX.last_bench.bandwidth_gb *
                   1000.0);
}

/* ===================== BRIDGE (JSON STREAM) =================== */

static void _bridge_mode(void) {
    uint8_t buffer[4096];
    size_t  n;

    printf("SYS: RAM=%" PRIu64 "MB cells=%" PRIu32
           " mask=%08X\n",
           CFG.phys_ram_mb,
           MTX.phys_cells,
           MTX.phys_mask);

    while ((n = fread(buffer, 1,
                      sizeof(buffer), stdin)) > 0) {
        uint32_t h = _crc(0, buffer, n);

        _inject_energy(h & 0xFFFFu,
                       (h >> 16) & 0xFFFFu,
                       MTX.cycle_count,
                       (float)(buffer[0] % 10u));

        _process_physics();
        _normalize_zipraf();

        if (MTX.cycle_count % 50u == 0u) {
            printf("{\"crc\":\"%08X\","
                   "\"ops\":%" PRIu64 ","
                   "\"nrg\":%.2f,"
                   "\"cycles\":%" PRIu64 ","
                   "\"bytes\":%" PRIu64 ","
                   "\"vec0\":%.3f,"
                   "\"vec1\":%.3f}\n",
                   MTX.state_crc,
                   MTX.ops_tick,
                   MTX.global_energy,
                   MTX.cycle_count,
                   (uint64_t)MTX.cycle_count *
                     (uint64_t)sizeof(buffer),
                   MTX.vector_space[0],
                   MTX.vector_space[1]);
            fflush(stdout);
        }
    }
}

/* ====================== UI (<= 80 COLUNAS) ==================== */

static void _render_dashboard(void) {
    _normalize_zipraf();

    printf("\033[2J\033[H");
    printf(C_CYN "=== RAFAELIA GOD CORE v1005 ==="
           C_RST "\n");

    printf("N=%" PRIu64 "^3  RAM=%" PRIu64
           "MB  lanes=%u\n",
           CFG.dim_n,
           CFG.phys_ram_mb,
           CFG.active_cores);

    printf("mask=%08X  crc=%08X\n",
           MTX.phys_mask,
           MTX.state_crc);

    printf("energy=%.2f  zeta=%.4f\n",
           MTX.global_energy,
           CFG.zeta_factor);

    printf("ops_tick=%" PRIu64
           "  ops_total=%" PRIu64 "\n",
           MTX.ops_tick,
           MTX.ops_total);

    printf("vec[0..3]=[%.2f %.2f %.2f %.2f]\n",
           MTX.vector_space[0],
           MTX.vector_space[1],
           MTX.vector_space[2],
           MTX.vector_space[3]);

    if (MTX.last_bench.score > 0) {
        printf("bench_score=%" PRIu64 "\n",
               MTX.last_bench.score);
        printf("iops=%.2fM/s  bw=%.2fGB/s\n",
               MTX.last_bench.iops / 1e6,
               MTX.last_bench.bandwidth_gb);
    } else {
        printf("bench=pending (press 'B')\n");
    }

    printf("--------------------------------\n");
    printf("[1] set N   [2] set RAM(MB)\n");
    printf("[3] sim 1000 steps\n");
    printf("[B] bench (1s)   [0] exit\n");
    printf("cmd> ");
}

static void _safe_pause(void) {
    int c;
    printf(C_DIM "\npress Enter..." C_RST);
    while ((c = getchar()) != '\n' && c != EOF) {}
}

/* ============================ MAIN ============================ */

int main(int argc, char *argv[]) {
    _genesis();

    if (argc > 1 &&
        strcmp(argv[1], "--bridge") == 0) {
        _bridge_mode();
        if (MTX.tensor) free(MTX.tensor);
        return 0;
    }

    char     cmd;
    uint64_t val;

    for (;;) {
        _render_dashboard();

        if (scanf(" %c", &cmd) != 1) cmd = '0';
        int c;
        while ((c = getchar()) != '\n' && c != EOF) {}

        if (cmd == '0') break;

        switch (cmd) {
        case '1':
            printf("new N: ");
            if (scanf("%" PRIu64, &val) == 1) {
                CFG.dim_n = val;
            }
            break;

        case '2':
            printf("new RAM(MB): ");
            if (scanf("%" PRIu64, &val) == 1) {
                _alloc_universe(val);
            }
            _safe_pause();
            break;

        case '3':
            printf("simulating 1000 steps...\n");
            for (int i = 0; i < 1000; i++) {
                uint32_t r = _rng(&MTX.rng_state);
                _inject_energy(r,
                               r >> 10,
                               r >> 20,
                               2.0f);
                _process_physics();
            }
            _safe_pause();
            break;

        case 'B':
        case 'b':
            _run_benchmark();
            _safe_pause();
            break;

        default:
            break;
        }
    }

    if (MTX.tensor) free(MTX.tensor);
    printf(C_RED "\n[system halted]\n" C_RST);
    return 0;
}
