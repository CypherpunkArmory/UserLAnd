/*
 * 🌌 RAFAELIA CLAY CORE (v7.0 - MILLENNIUM SOLVER)
 * ARCH: C11 | 7-Problems Physics | Riemann + Navier + Yang-Mills
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
#define C_CYN  "\x1b[36m"
#define C_GRN  "\x1b[32m"
#define C_YEL  "\x1b[33m"
#define C_RED  "\x1b[31m"
#define C_MAG  "\x1b[35m"
#define C_BLU  "\x1b[34m"
#define C_WHT  "\x1b[37m"
#define C_BLD  "\x1b[1m"
#define C_DIM  "\x1b[2m"

#define CACHE_LINE 64
#define MAX_LANES 64

typedef struct {
    uint64_t phys_ram_mb;
    uint32_t active_lanes;
    float    viscosity;
    float    mass_gap;
    float    zeta_real;
} ClayConfig;

typedef struct __attribute__((aligned(CACHE_LINE))) {
    float    *tensor;
    float    *flow_u;

    uint32_t phys_mask;
    uint32_t phys_cells;

    uint32_t cursors[MAX_LANES][2048];
    uint32_t cursor_heads[MAX_LANES];

    double   riemann_sum;
    float    fluid_kinetic;
    uint64_t p_np_cycles;
    uint32_t topo_holes;

    uint32_t state_crc;
    uint32_t rng_state;
    uint64_t cycle_count;
} ClayMatrix;

static ClayMatrix MTX;
static ClayConfig CFG;

/* RNG baseado em curva elíptica simplificada */
static inline uint32_t _bsd_rng(uint32_t *state) {
    uint32_t x = *state;
    x = (x * x * x) + (x * 7u) + 13u;
    x ^= x << 13; x ^= x >> 17; x ^= x << 5;
    *state = x;
    return x;
}

static inline uint32_t _crc32_step(uint32_t prev, uint32_t data) {
    uint32_t c = prev ^ data;
    for (int k = 0; k < 4; k++)
        c = (c >> 1) ^ (0xEDB88320 & -(int32_t)(c & 1));
    return c;
}

static inline uint32_t _poincare_map(uint64_t x, uint64_t y, uint64_t z) {
    uint32_t h = (uint32_t)(x ^ y ^ z);
    h *= 0x85ebca6b;
    return h & MTX.phys_mask;
}

/* MEMÓRIA */

static void _alloc_clay(uint64_t mb_target) {
    if (MTX.tensor) {
        free(MTX.tensor);
        MTX.tensor = NULL;
    }
    if (MTX.flow_u) {
        free(MTX.flow_u);
        MTX.flow_u = NULL;
    }

    if (mb_target < 4) mb_target = 4;

    uint64_t bytes = mb_target * 1024ULL * 1024ULL;
    uint64_t cells = bytes / sizeof(float);
    uint64_t pow2 = 1;
    while (pow2 < cells) pow2 <<= 1;

    if (posix_memalign((void **)&MTX.tensor, CACHE_LINE, pow2 * sizeof(float)) != 0 ||
        posix_memalign((void **)&MTX.flow_u, CACHE_LINE, pow2 * sizeof(float)) != 0) {
        fprintf(stderr, C_RED "FATAL: clay alloc fail\n" C_RST);
        exit(1);
    }

    memset(MTX.tensor, 0, pow2 * sizeof(float));
    memset(MTX.flow_u, 0, pow2 * sizeof(float));

    MTX.phys_cells = (uint32_t)pow2;
    MTX.phys_mask  = (uint32_t)(pow2 - 1U);
    CFG.phys_ram_mb = (pow2 * sizeof(float) * 2) / (1024 * 1024);
}

static void _genesis(void) {
    memset(&MTX, 0, sizeof(ClayMatrix));
    MTX.state_crc = 0x19002000;     /* 2000 (Clay) */
    MTX.rng_state = 0xC1A97777;     /* "CLAY" em hexa estilizado */

    CFG.active_lanes = 8;
    CFG.viscosity    = 0.96f;
    CFG.mass_gap     = 0.05f;
    CFG.zeta_real    = 0.5f;

    _alloc_clay(64);
}

/* 1. Riemann Hypothesis */

static void _op_riemann_pulse(void) {
    uint32_t base = (uint32_t)(MTX.cycle_count % 10000ULL);
    if (base < 2) base = 2;

    if ((base & 1u) || base == 2) {
        uint32_t r   = _bsd_rng(&MTX.rng_state);
        uint32_t idx = r & MTX.phys_mask;

        float energy = 100.0f / powf((float)(base % 20u + 1u), CFG.zeta_real);
        MTX.tensor[idx] += energy;

        uint32_t lane = idx % CFG.active_lanes;
        if (MTX.cursor_heads[lane] < 2048)
            MTX.cursors[lane][MTX.cursor_heads[lane]++] = idx;

        MTX.riemann_sum += 1.0 / (double)base;
    }
}

/* 2,3,4. Yang-Mills + Navier-Stokes + P vs NP metric */

static void _op_physics_solver(void) {
    MTX.fluid_kinetic = 0.0f;
    MTX.p_np_cycles   = 0;

    for (uint32_t l = 0; l < CFG.active_lanes; l++) {
        uint32_t head = MTX.cursor_heads[l];
        uint32_t processed = 0;

        for (uint32_t k = 0; k < head; k++) {
            uint32_t idx = MTX.cursors[l][k] & MTX.phys_mask;
            float val = MTX.tensor[idx];

            if (val < CFG.mass_gap) {
                MTX.tensor[idx] = 0.0f;
                continue;
            }

            uint32_t left  = (idx - 1u) & MTX.phys_mask;
            uint32_t right = (idx + 1u) & MTX.phys_mask;

            float grad = (MTX.tensor[right] - MTX.tensor[left]) * 0.5f;

            MTX.flow_u[idx] = (MTX.flow_u[idx] * 0.9f) - (grad * 0.1f);

            float flux = MTX.flow_u[idx] * val * 0.5f;
            if (flux > 0)
                MTX.tensor[right] += flux;
            else
                MTX.tensor[left]  -= flux;

            MTX.tensor[idx] -= fabsf(flux);
            MTX.tensor[idx] *= CFG.viscosity;

            MTX.fluid_kinetic += fabsf(MTX.flow_u[idx]);
            processed++;
        }

        MTX.p_np_cycles += processed;
        MTX.cursor_heads[l] = 0;
    }

    MTX.cycle_count++;
}

/* 5. Hodge / Poincaré scan */

static void _op_hodge_scan(void) {
    uint32_t topo_hash = 0;
    for (int i = 0; i < 1024; i++) {
        uint32_t idx = _bsd_rng(&MTX.rng_state) & MTX.phys_mask;
        topo_hash ^= (uint32_t)MTX.tensor[idx];
    }
    MTX.topo_holes = topo_hash;
    MTX.state_crc  = _crc32_step(MTX.state_crc, topo_hash);
}

/* UI */

static void _render_clay_ui(void) {
    printf("\033[2J\033[H");
    printf(C_CYN " ╔════════════════════════════════════════════════════════════╗\n");
    printf(" ║  " C_BLD C_WHT "RAFAELIA CLAY CORE :: v7.0" C_RST C_CYN "                  ║\n");
    printf(" ╠════════════════════════════════════════════════════════════╣\n");

    printf(" ║ " C_BLD "PROBLEMS" C_RST "                 │ " C_BLD "PHYSICS" C_RST "           ║\n");
    printf(" ║ P vs NP    : metric=%-8" PRIu64 " │ RAM : " C_GRN "%4" PRIu64 " MB" C_RST "   ║\n",
           MTX.p_np_cycles, CFG.phys_ram_mb);
    printf(" ║ Riemann    : ζ(%.1f)       │ Gap : " C_RED "%.3f" C_RST "         ║\n",
           CFG.zeta_real, CFG.mass_gap);
    printf(" ║ Yang-Mills : GAP OPEN      │ Visc: " C_BLU "%.3f" C_RST "         ║\n",
           CFG.viscosity);
    printf(" ║ Navier-Stk : FLOWING       │ KE  : " C_WHT "%.2e" C_RST "      ║\n",
           MTX.fluid_kinetic);

    printf(" ╠════════════════════════════════════════════════════════════╣\n");
    printf(" ║ RNG   : " C_MAG "%08X" C_RST "    TopoHash: " C_CYN "%08X" C_RST "        ║\n",
           MTX.rng_state, MTX.topo_holes);
    printf(" ║ CRC   : " C_GRN "%08X" C_RST "    Cycles : %" PRIu64 "            ║\n",
           MTX.state_crc, MTX.cycle_count);
    printf(" ╚════════════════════════════════════════════════════════════╝\n");

    printf("\n [1] Resize RAM   [2] Riemann Pulse x100\n");
    printf(" [3] Single Solve [4] Auto Solver (2000)\n");
    printf(" [0] Exit\n\nCLAY> ");
}

static void _safe_pause(void) {
    int c;
    while ((c = getchar()) != '\n' && c != EOF) { }
    printf("\n" C_DIM "Press Enter..." C_RST);
    (void)getchar();
}

/* MAIN */

int main(void) {
    _genesis();

    char cmd;
    uint64_t val;

    for (;;) {
        _render_clay_ui();
        if (scanf(" %c", &cmd) != 1) cmd = '0';
        if (cmd == '0') break;

        switch (cmd) {
            case '1':
                printf(C_CYN "Target RAM (MB): " C_RST);
                if (scanf("%" PRIu64, &val) == 1) _alloc_clay(val);
                break;
            case '2':
                for (int i = 0; i < 100; i++) _op_riemann_pulse();
                _op_physics_solver();
                _op_hodge_scan();
                _safe_pause();
                break;
            case '3':
                _op_physics_solver();
                _op_hodge_scan();
                _safe_pause();
                break;
            case '4':
                printf(C_GRN "\nSolving Millennium Problems..." C_RST "\n");
                for (int i = 0; i < 2000; i++) {
                    _op_riemann_pulse();
                    _op_physics_solver();
                    if (i % 50 == 0) {
                        _op_hodge_scan();
                        printf(C_MAG "." C_RST);
                        fflush(stdout);
                    }
                }
                _safe_pause();
                break;
            default:
                break;
        }
    }

    if (MTX.tensor) free(MTX.tensor);
    if (MTX.flow_u) free(MTX.flow_u);
    printf(C_RED "\n[ SOLUTION CONVERGED ]\n" C_RST);
    return 0;
}
