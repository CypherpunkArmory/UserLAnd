/*
 * 🌌 RAFAELIA OMEGA CORE: Σ-ULTIMATE (v999.0)
 * ARCH: C11 | Holographic Buffer | SOC Physics | HDC Vector
 */

#define _POSIX_C_SOURCE 200809L

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <math.h>
#include <time.h>
#include <inttypes.h>
#include <stdint.h>

/* ANSI COLORS */
#define C_RST  "\x1b[0m"
#define C_BLK  "\x1b[30m"
#define C_RED  "\x1b[31m"
#define C_GRN  "\x1b[32m"
#define C_YEL  "\x1b[33m"
#define C_BLU  "\x1b[34m"
#define C_MAG  "\x1b[35m"
#define C_CYN  "\x1b[36m"
#define C_WHT  "\x1b[37m"
#define C_BLD  "\x1b[1m"
#define C_DIM  "\x1b[2m"

/* CONSTANTES */
#define MAX_LANES 128
#define CACHE_LINE 64
#define VEC_DIM 1024
#define FIB_DEPTH 42

typedef struct {
    uint64_t dim_n;
    uint64_t phys_ram_mb;
    uint32_t active_lanes;
    float    decay_rate;
    float    inject_power;
    float    threshold;
    float    zeta_factor;
} RafConfig;

typedef struct __attribute__((aligned(CACHE_LINE))) {
    float    *tensor;
    float    hdc_vector[VEC_DIM];

    uint32_t phys_mask;
    uint32_t phys_cells;

    uint32_t cursors[MAX_LANES][4096];
    uint32_t cursor_heads[MAX_LANES];

    uint32_t state_crc;
    uint32_t rng_state;
    uint64_t cycle_count;
    uint64_t iops_metric;
    uint64_t bytes_ingested;
    float    global_energy;

    uint64_t fib_seq[FIB_DEPTH];
} OmegaMatrix;

static OmegaMatrix MTX;
static RafConfig   CFG;

/* RNG / MIX / CRC */

static inline uint32_t _rng(uint32_t *state) {
    uint32_t x = *state;
    x ^= x << 13; x ^= x >> 17; x ^= x << 5;
    *state = x;
    return x;
}

static inline uint32_t _murmur_mix(uint32_t h) {
    h ^= h >> 16;
    h *= 0x85ebca6b;
    h ^= h >> 13;
    h *= 0xc2b2ae35;
    h ^= h >> 16;
    return h;
}

static inline uint32_t _holo_map(uint64_t x, uint64_t y, uint64_t z) {
    uint32_t h = (uint32_t)x ^ (uint32_t)y ^ (uint32_t)z;
    h = _murmur_mix(h);
    return h & MTX.phys_mask;
}

static inline uint32_t _crc_time(uint32_t prev, const void *data, size_t len) {
    const uint8_t *p = (const uint8_t *)data;
    uint32_t c = ~prev;
    while (len--) {
        c ^= *p++;
        c = (c >> 1) ^ (0xEDB88320 & -(int32_t)(c & 1));
    }
    return ~c;
}

static inline float _swiglu(float x) {
    return x / (1.0f + expf(-x));
}

/* MEMÓRIA */

static void _alloc_universe(uint64_t mb_target) {
    if (MTX.tensor) {
        free(MTX.tensor);
        MTX.tensor = NULL;
    }

    if (mb_target < 4) mb_target = 4;

    uint64_t bytes = mb_target * 1024ULL * 1024ULL;
    uint64_t cells = bytes / sizeof(float);
    uint64_t pow2 = 1;
    while (pow2 < cells) pow2 <<= 1;

    size_t final_bytes = pow2 * sizeof(float);

    if (posix_memalign((void **)&MTX.tensor, CACHE_LINE, final_bytes) != 0) {
        fprintf(stderr, C_RED "FATAL: Memory Allocation Failed.\n" C_RST);
        exit(1);
    }

    memset(MTX.tensor, 0, final_bytes);
    MTX.phys_cells = (uint32_t)pow2;
    MTX.phys_mask  = (uint32_t)(pow2 - 1U);
    CFG.phys_ram_mb = final_bytes / (1024 * 1024);

    memset(MTX.hdc_vector, 0, sizeof(MTX.hdc_vector));
}

/* GÊNESE */

static void _genesis(void) {
    memset(&MTX, 0, sizeof(OmegaMatrix));
    MTX.state_crc = 0x52414641; /* "RAFA" */
    MTX.rng_state = 0xCAFEBABE;

    CFG.dim_n        = 1000;
    CFG.active_lanes = 4;
    CFG.decay_rate   = 0.99f;
    CFG.inject_power = 5.0f;
    CFG.threshold    = 4.0f;
    CFG.zeta_factor  = 0.001f;

    _alloc_universe(64);

    MTX.fib_seq[0] = 1; MTX.fib_seq[1] = 1;
    for (int i = 2; i < FIB_DEPTH; i++)
        MTX.fib_seq[i] = MTX.fib_seq[i-1] + MTX.fib_seq[i-2];
}

/* FÍSICA */

static void _process_physics(void) {
    MTX.iops_metric = 0;

    for (uint32_t l = 0; l < CFG.active_lanes; l++) {
        uint32_t head = MTX.cursor_heads[l];

        for (uint32_t k = 0; k < head; k++) {
            uint32_t idx = MTX.cursors[l][k] & MTX.phys_mask;
            float val = MTX.tensor[idx];

            if (val > CFG.threshold) {
                float excess = val * 0.5f;
                MTX.tensor[idx] -= excess;

                uint32_t left  = (idx - 1u) & MTX.phys_mask;
                uint32_t right = (idx + 1u) & MTX.phys_mask;

                MTX.tensor[left]  += excess * 0.25f;
                MTX.tensor[right] += excess * 0.25f;
                MTX.iops_metric++;
            }

            MTX.tensor[idx] = (MTX.tensor[idx] * CFG.decay_rate) + CFG.zeta_factor;
        }
        MTX.cursor_heads[l] = 0;
    }

    MTX.cycle_count++;
}

static void _inject_energy(uint64_t vx, uint64_t vy, uint64_t vz, float energy) {
    uint32_t p_idx = _holo_map(vx, vy, vz);

    uint32_t lane = p_idx % CFG.active_lanes;
    uint32_t head = MTX.cursor_heads[lane];

    if (head < 4096u) {
        MTX.cursors[lane][head] = p_idx;
        MTX.cursor_heads[lane]++;
    }

    MTX.tensor[p_idx]   += energy;
    MTX.global_energy   += energy;
    MTX.state_crc        = _crc_time(MTX.state_crc, &p_idx, sizeof(uint32_t));

    uint32_t vec_slot = p_idx & (VEC_DIM - 1u);
    MTX.hdc_vector[vec_slot] += _swiglu(energy);
}

/* UI */

static void _draw_logo(void) {
    printf(C_CYN "   .::. " C_YEL "RAFAELIA OMEGA CORE" C_CYN " .::.\n" C_RST);
    printf(C_MAG "   (Φ)  / HYPER / (Ω)\n" C_RST);
    printf(C_GRN "    Σ·HDC · SOC · CRC\n" C_RST);
}

static void _render_dashboard(void) {
    printf("\033[2J\033[H");
    _draw_logo();

    printf("\n" C_BLD " SYSTEM " C_RST "----------------------------------------\n");
    printf(" N^3      : " C_YEL "%" PRIu64 C_RST "\n", CFG.dim_n);
    printf(" RAM(MB)  : " C_GRN "%" PRIu64 C_RST "  mask=%08X\n",
           CFG.phys_ram_mb, MTX.phys_mask);
    printf(" CRC      : " C_MAG "%08X" C_RST "  cycles=%" PRIu64 "\n",
           MTX.state_crc, MTX.cycle_count);
    printf(" IOPS     : %" PRIu64 "  energy=%.2f\n",
           MTX.iops_metric, MTX.global_energy);
    printf(" ZETA     : %.4f\n", CFG.zeta_factor);

    printf("\n" C_BLD " HDC[0..7] " C_RST "-----------------------------------\n");
    printf(" [ ");
    for (int i = 0; i < 8; i++)
        printf("%.2f ", MTX.hdc_vector[i]);
    printf("... ]\n");

    printf("\n" C_BLD " MENU " C_RST "-----------------------------------------\n");
    printf(" [1] set N^3      [2] set RAM(MB)\n");
    printf(" [3] pulse        [4] simulate 2000\n");
    printf(" [0] exit\n\n");
    printf(C_CYN "OMEGA> " C_RST);
}

static void _safe_pause(void) {
    int c;
    while ((c = getchar()) != '\n' && c != EOF) { }
    printf("\n" C_DIM "Press Enter..." C_RST);
    (void)getchar();
}

/* BRIDGE MODE */

static void _bridge_mode(void) {
    uint8_t buffer[8192];
    size_t n;

    while ((n = fread(buffer, 1, sizeof(buffer), stdin)) > 0) {
        MTX.bytes_ingested += n;
        uint32_t h = _crc_time(0, buffer, n);

        _inject_energy(h & 0xFFFF, (h >> 16) & 0xFFFF,
                       MTX.cycle_count, (float)(buffer[0] % 10));

        _process_physics();

        if (MTX.cycle_count % 100 == 0) {
            printf("{\"crc\":\"%08X\",\"iops\":%" PRIu64
                   ",\"nrg\":%.2f,\"vec0\":%.3f}\n",
                   MTX.state_crc, MTX.iops_metric,
                   MTX.global_energy, MTX.hdc_vector[0]);
            fflush(stdout);
        }
    }
}

/* MAIN */

int main(int argc, char *argv[]) {
    _genesis();

    if (argc > 1 && strcmp(argv[1], "--bridge") == 0) {
        _bridge_mode();
        if (MTX.tensor) free(MTX.tensor);
        return 0;
    }

    char cmd;
    uint64_t val;

    for (;;) {
        _render_dashboard();
        if (scanf(" %c", &cmd) != 1) cmd = '0';
        if (cmd == '0') break;

        switch (cmd) {
            case '1':
                printf(C_CYN "Virtual N (N^3): " C_RST);
                if (scanf("%" PRIu64, &val) == 1) CFG.dim_n = val;
                break;
            case '2':
                printf(C_CYN "Physical RAM (MB): " C_RST);
                if (scanf("%" PRIu64, &val) == 1) _alloc_universe(val);
                _safe_pause();
                break;
            case '3':
                _inject_energy(_rng(&MTX.rng_state),
                               _rng(&MTX.rng_state),
                               MTX.cycle_count,
                               CFG.inject_power * 10.0f);
                _process_physics();
                break;
            case '4':
                printf(C_GRN "\nRunning OMEGA Simulation..." C_RST);
                for (int i = 0; i < 2000; i++) {
                    uint32_t r = _rng(&MTX.rng_state);
                    _inject_energy(r, r >> 10, r >> 20, 2.0f);
                    _process_physics();
                }
                printf(" Done.\n");
                _safe_pause();
                break;
            default:
                break;
        }
    }

    if (MTX.tensor) free(MTX.tensor);
    printf(C_RED "\n[ CORE SHUTDOWN ]\n" C_RST);
    return 0;
}
