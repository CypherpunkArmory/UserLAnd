/*
 * ======================================================================================
 * 🌌 RAFAELIA OMNI CORE (v999 - GOD MODE TUNER + BENCH)
 * ======================================================================================
 * ARCH: Dynamic Aligned Memory | Elastic Dimension | Virtual Cores
 * CONTROL: Virtual Size (N^3) + Physical RAM (MB) + Logic Lanes
 * LOGIC: Dynamic Holographic Masking + Sparse Physics
 * ======================================================================================
 */

#define _POSIX_C_SOURCE 200809L

#include <stdio.h>
#include <stdint.h>
#include <math.h>
#include <string.h>
#include <stdlib.h>
#include <time.h>

#define MAX_CORES 128
#define CACHE_LINE 64

// Cores ANSI
#define C_RST  "\x1b[0m"
#define C_CYN  "\x1b[36m"
#define C_GRN  "\x1b[32m"
#define C_YEL  "\x1b[33m"
#define C_RED  "\x1b[31m"
#define C_MAG  "\x1b[35m"

typedef struct {
    uint64_t dim_n;          // Dimensão Virtual
    uint64_t phys_ram_mb;    // RAM Física em MB (intenção)
    uint32_t active_cores;   // Cores Lógicos
    float idle_factor;       // % de Ócio usado
    float decay_rate;        // Decaimento térmico
} RafConfig;

typedef struct {
    // Tensor Físico dinâmico (heap alinhado)
    float *tensor;
    uint32_t phys_size_cells;
    uint32_t phys_mask;

    // Cursores por core
    uint32_t active_cursors[MAX_CORES][4096];
    uint32_t cursor_heads[MAX_CORES];

    // Estado
    uint32_t state_crc;
    uint32_t cycle_count;
    float    global_energy;
    uint64_t ops_metric;
} OmniMatrix;

static OmniMatrix MATRIX;
static RafConfig  CFG;

// -------------------- KERNEL BÁSICO -------------------- //

static inline uint32_t _fast_crc(uint32_t prev, const void* data, size_t len) {
    const uint8_t* p = (const uint8_t*)data;
    uint32_t c = ~prev;
    while (len--) {
        c ^= *p++;
        for (int k = 0; k < 8; k++) {
            c = (c >> 1) ^ (0xEDB88320u & (-(c & 1u)));
        }
    }
    return ~c;
}

// Mapeamento holográfico dinâmico
static inline uint32_t _holographic_map(uint64_t x, uint64_t y, uint64_t z) {
    if (CFG.dim_n == 0) CFG.dim_n = 1000; // fallback
    x %= CFG.dim_n;
    y %= CFG.dim_n;
    z %= CFG.dim_n;

    uint32_t hash = 2166136261u;
    hash ^= (uint32_t)x; hash *= 16777619u;
    hash ^= (uint32_t)y; hash *= 16777619u;
    hash ^= (uint32_t)z; hash *= 16777619u;

    return hash & MATRIX.phys_mask;
}

// -------------------- ALOCAÇÃO DINÂMICA -------------------- //

static void _realloc_universe(uint64_t target_mb) {
    if (MATRIX.tensor) {
        free(MATRIX.tensor);
        MATRIX.tensor = NULL;
    }

    if (target_mb < 4) target_mb = 4;       // mínimo 4MB
    if (target_mb > 16384) target_mb = 16384; // máximo 16GB (conceitual)

    uint64_t bytes = target_mb * 1024ull * 1024ull;
    uint64_t cells = bytes / sizeof(float);

    // arredonda pra potência de 2
    uint64_t pow2_cells = 1;
    while (pow2_cells < cells) pow2_cells <<= 1;

    size_t alloc_size = pow2_cells * sizeof(float);
    if (posix_memalign((void**)&MATRIX.tensor, CACHE_LINE, alloc_size) != 0) {
        printf(C_RED "CRITICAL: RAM ALLOC FAILED!\n" C_RST);
        exit(1);
    }
    memset(MATRIX.tensor, 0, alloc_size);

    MATRIX.phys_size_cells = (uint32_t)pow2_cells;
    MATRIX.phys_mask       = (uint32_t)(pow2_cells - 1u);
    CFG.phys_ram_mb        = target_mb;

    printf(C_GRN "SYS: Universe Reallocated. Cells: %lu (Mask: %08X)\n" C_RST,
           (unsigned long)pow2_cells, MATRIX.phys_mask);
}

// -------------------- FÍSICA DO NÚCLEO -------------------- //

static void _genesis(void) {
    memset(&MATRIX, 0, sizeof(OmniMatrix));
    MATRIX.state_crc = 0x52414641u; // "RAFA"
    CFG.dim_n        = 1000;
    CFG.active_cores = 4;
    CFG.idle_factor  = 0.5f;
    CFG.decay_rate   = 0.98f;
    _realloc_universe(64); // boot inicial: 64MB

    srand((unsigned) time(NULL));
}

static void _op_process_lanes(void) {
    MATRIX.ops_metric = 0;
    for (uint32_t core = 0; core < CFG.active_cores && core < MAX_CORES; core++) {
        uint32_t head = MATRIX.cursor_heads[core];
        for (uint32_t k = 0; k < head; k++) {
            uint32_t idx = MATRIX.active_cursors[core][k] & MATRIX.phys_mask;
            float val = MATRIX.tensor[idx];

            if (val > 4.0f) {
                float excess = val * 0.5f;
                MATRIX.tensor[idx] -= excess;

                uint32_t left  = (idx - 1u) & MATRIX.phys_mask;
                uint32_t right = (idx + 1u) & MATRIX.phys_mask;
                MATRIX.tensor[left]  += excess * 0.25f;
                MATRIX.tensor[right] += excess * 0.25f;
                MATRIX.ops_metric++;
            }
            MATRIX.tensor[idx] *= CFG.decay_rate;
        }
        MATRIX.cursor_heads[core] = 0;
    }
    MATRIX.cycle_count++;
}

static void _op_inject(uint64_t vx, uint64_t vy, uint64_t vz, float energy) {
    uint32_t p_idx = _holographic_map(vx, vy, vz);
    uint32_t core  = CFG.active_cores ? (p_idx % CFG.active_cores) : 0u;
    if (core >= MAX_CORES) core = core % MAX_CORES;

    uint32_t head = MATRIX.cursor_heads[core];
    if (head < 4096u) {
        MATRIX.active_cursors[core][head] = p_idx;
        MATRIX.cursor_heads[core]++;
    }

    MATRIX.tensor[p_idx] += energy;
    MATRIX.global_energy += energy;
    MATRIX.state_crc = _fast_crc(MATRIX.state_crc, &p_idx, sizeof(uint32_t));
}

// idle "sonhando"
static void _op_idle_dream(void) {
    if (MATRIX.global_energy > 5000.0f) return;

    uint32_t dream_size = 1024u * (uint32_t)(CFG.idle_factor * 10.0f);
    if (dream_size == 0) return;

    uint32_t start = MATRIX.state_crc & MATRIX.phys_mask;
    for (uint32_t i = 0; i < dream_size; i++) {
        uint32_t idx = (start + i) & MATRIX.phys_mask;
        if (MATRIX.tensor[idx] > 0.0001f) {
            MATRIX.tensor[idx] *= 0.99f;
        }
    }
    MATRIX.state_crc = _fast_crc(MATRIX.state_crc, &dream_size, sizeof(uint32_t));
}

// -------------------- MODO BENCHMARK -------------------- //

static void _run_bench(int steps, int inj_per_step) {
    if (steps <= 0) steps = 5000;
    if (inj_per_step <= 0) inj_per_step = 8;

    _genesis();

    uint64_t total_inj  = (uint64_t)steps * (uint64_t)inj_per_step;
    uint64_t total_ops  = 0;

    clock_t start = clock();
    for (int i = 0; i < steps; i++) {
        for (int j = 0; j < inj_per_step; j++) {
            _op_inject(
                (uint64_t)(MATRIX.state_crc * (uint32_t)(i + 1)) % CFG.dim_n,
                (uint64_t)((MATRIX.state_crc >> 5) + j) % CFG.dim_n,
                (uint64_t)((MATRIX.state_crc >> 10) + i + j) % CFG.dim_n,
                5.0f
            );
        }
        _op_process_lanes();
        total_ops += MATRIX.ops_metric;
        _op_idle_dream();
    }
    clock_t end = clock();

    double elapsed = (double)(end - start) / (double)CLOCKS_PER_SEC;
    if (elapsed <= 0.0) elapsed = 1e-9;

    double steps_s   = (double)steps / elapsed;
    double events_s  = (double)total_inj / elapsed;
    double soccells_s= (double)total_ops / elapsed;

    printf("[BENCH] RAFAELIA OMNI CORE\n");
    printf("[BENCH] Virtual Dim     : %llu^3\n", (unsigned long long)CFG.dim_n);
    printf("[BENCH] Physical Cells  : %u\n", MATRIX.phys_size_cells);
    printf("[BENCH] Steps           : %d\n", steps);
    printf("[BENCH] Inj/step        : %d\n", inj_per_step);
    printf("[BENCH] Total Inj       : %llu\n", (unsigned long long)total_inj);
    printf("[BENCH] Elapsed         : %.6f s\n", elapsed);
    printf("[BENCH] Steps/s         : %.2f\n", steps_s);
    printf("[BENCH] Virtual events/s: %.2f\n", events_s);
    printf("[BENCH] SOC-cells/s     : %.2f\n", soccells_s);
    printf("[BENCH] Energy          : %.4f\n", MATRIX.global_energy);
    printf("[BENCH] Hash            : %08X\n", MATRIX.state_crc);
}

// -------------------- UI INTERATIVA -------------------- //

static void _render_ui(void) {
    printf("\033[2J\033[H");
    printf(C_CYN "=== RAFAELIA OMNI CORE [v999] ===\n" C_RST);
    printf("VIRTUAL  : " C_YEL "%llu^3" C_RST " (Holographic)\n",
           (unsigned long long)CFG.dim_n);
    printf("PHYSICAL : " C_GRN "%lu MB" C_RST " (Aligned Heap)\n",
           (unsigned long)((MATRIX.phys_size_cells * 4ull) / 1024ull / 1024ull));
    printf("CORES    : " C_MAG "%u" C_RST " Lanes\n", CFG.active_cores);
    printf("----------------------------------\n");
    printf("CRC      : %08X\n", MATRIX.state_crc);
    printf("ENERGY   : %.2f\n", MATRIX.global_energy);
    printf("OPS/TICK : %lu\n", (unsigned long)MATRIX.ops_metric);
    printf("----------------------------------\n");
    printf("[1] SET VIRTUAL DIM (100 - 100k)\n");
    printf("[2] SET PHYSICAL RAM (16MB - 16GB)\n");
    printf("[3] SET CORES (1 - 128)\n");
    printf("[4] INJECT PULSE\n");
    printf("[5] AUTO RUN (Simulate)\n");
    printf("[0] EXIT\n");
    printf("\nOMNI> ");
}

static void _safe_read(void) {
    int c;
    while ((c = getchar()) != '\n' && c != EOF) { /* drain */ }
    printf("Press Enter...");
    (void)getchar(); // consome ENTER final
}

// -------------------- MAIN -------------------- //

int main(int argc, char *argv[]) {
    // Modo bench: ./raf_omni_core --bench [steps] [inj_per_step]
    if (argc > 1 && strcmp(argv[1], "--bench") == 0) {
        int steps = 5000;
        int inj   = 8;
        if (argc > 2) steps = atoi(argv[2]);
        if (argc > 3) inj   = atoi(argv[3]);
        _run_bench(steps, inj);
        if (MATRIX.tensor) free(MATRIX.tensor);
        return 0;
    }

    _genesis();

    char cmd;
    for (;;) {
        _render_ui();
        if (scanf(" %c", &cmd) != 1) {
            cmd = '0';
        }

        if (cmd == '0') break;

        if (cmd == '1') {
            unsigned long long tmp;
            printf("Virtual Dim N (N^3): ");
            if (scanf("%llu", &tmp) == 1) {
                if (tmp < 100ull) tmp = 100ull;
                if (tmp > 100000ull) tmp = 100000ull;
                CFG.dim_n = (uint64_t)tmp;
            }
        } else if (cmd == '2') {
            unsigned long long tmp;
            printf("Target RAM (MB): ");
            if (scanf("%llu", &tmp) == 1) {
                _realloc_universe((uint64_t)tmp);
            }
            _safe_read();
        } else if (cmd == '3') {
            unsigned int tmp;
            printf("Logic Cores: ");
            if (scanf("%u", &tmp) == 1) {
                if (tmp < 1u)  tmp = 1u;
                if (tmp > MAX_CORES) tmp = MAX_CORES;
                CFG.active_cores = tmp;
            }
        } else if (cmd == '4') {
            _op_inject(rand(), rand(), rand(), 50.0f);
            _op_process_lanes();
        } else if (cmd == '5') {
            printf("Running simulation...\n");
            for (int i = 0; i < 5000; i++) {
                _op_inject(
                    (uint64_t)(MATRIX.state_crc * (uint32_t)(i + 1)) % CFG.dim_n,
                    (uint64_t)((MATRIX.state_crc >> 5) + i) % CFG.dim_n,
                    (uint64_t)((MATRIX.state_crc >> 10) + i) % CFG.dim_n,
                    5.0f
                );
                _op_process_lanes();
                _op_idle_dream();
                if (i % 100 == 0) printf(".");
            }
            printf(" DONE.\n");
            _safe_read();
        }
    }

    if (MATRIX.tensor) free(MATRIX.tensor);
    return 0;
}
