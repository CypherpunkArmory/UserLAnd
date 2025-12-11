/*
 * ======================================================================================
 * 💎 RAFAELIA HYPER-CORE (UNIFIED MATRIX ENGINE)
 * ======================================================================================
 * MODULES: TORO_SOC + BITRAF + ZIPRAF + FIB_42
 * ARCH: C11 Native | Flat Memory Model | CRC-Driven Time | Zero-Copy
 * OPTIMIZATION: Cache Locality L1/L2 | Branchless Logic
 * ======================================================================================
 */

#include <stdio.h>
#include <stdint.h>
#include <math.h>
#include <string.h>
#include <time.h>
#include <inttypes.h>

// --- 1. HIPER-PARÂMETROS (COMPILE-TIME) ---
#define GRID_DIM      100
#define TOTAL_CELLS   (GRID_DIM * GRID_DIM * GRID_DIM)
#define VEC_DIM       64
#define SOC_STEPS     30
#define FIB_DEPTH     42

// Constantes Físicas Toroidais
#define R_MAJOR  20.0f
#define R_MINOR  14.0f

// Cores ANSI (Cyberpunk)
#define C_RST  "\x1b[0m"
#define C_CYN  "\x1b[36m"
#define C_MAG  "\x1b[35m"
#define C_GRN  "\x1b[32m"

// --- 2. MATRIX DE MEMÓRIA UNIFICADA ---
// Toda a existência do programa vive aqui. Sem mallocs. Sem fragmentação.
typedef struct __attribute__((aligned(64))) {
    // [BLOCO 0] Estado Físico (Tensor Toroidal)
    float   tensor[TOTAL_CELLS];

    // [BLOCO 1] Estado Vetorial (ZIPRAF - Compressão)
    float   vector_space[VEC_DIM];

    // [BLOCO 2] Sequência Fibonacci Atemporal (TORO_FIB)
    uint64_t fib_sequence[FIB_DEPTH];

    // [BLOCO 3] Variáveis de Controle (BITRAF - Hash/Tempo)
    uint32_t state_crc;
    uint32_t cycle_count;
    float    global_energy;
    float    threshold_dynamic;
    int      last_avalanches;

    // [BLOCO 4] Buffer de Log (Ring Buffer)
    char     log_buffer[8][64];
    uint8_t  log_head;
} MatrixCore;

// Instância Global Estática (BSS Segment)
static MatrixCore MATRIX;

// --- 3. FUNÇÕES MATEMÁTICAS ATEMPORAIS ---

// CRC32 "In-Line" (Substitui tempo de relógio)
static inline uint32_t _fast_crc(uint32_t prev, const void* data, size_t len) {
    const uint8_t* p = (const uint8_t*) data;
    uint32_t c = ~prev;
    while (len--) {
        c ^= *p++;
        for (int k = 0; k < 8; k++) {
            c = (c >> 1) ^ (0xEDB88320u & (-(int32_t)(c & 1u)));
        }
    }
    return ~c;
}

// Cálculo de Distância Toroidal (Otimizado)
static inline float _torus_dist_sq(int x, int y, int z) {
    float cx = GRID_DIM * 0.5f;
    float dx = (float)x - cx;
    float dy = (float)y - cx; // Assume cubo
    float dz = (float)z - cx;

    float r_xy   = sqrtf(dx * dx + dy * dy);
    float d_tube = r_xy - R_MAJOR;
    return d_tube * d_tube + dz * dz;
}

// --- 4. MÓDULOS DO NÚCLEO ---

// [MODULE A] FIBONACCI MODULADO (TORO_RAFAEL_FIB)
// Gera sequência baseada no CRC atual para modular o limiar de avalanche
static void _op_fib_modulation(void) {
    MATRIX.fib_sequence[0] = (uint64_t)(MATRIX.state_crc % 42u); // Semente variável
    MATRIX.fib_sequence[1] = 1u;

    for (int i = 2; i < FIB_DEPTH; i++) {
        MATRIX.fib_sequence[i] = MATRIX.fib_sequence[i - 1] + MATRIX.fib_sequence[i - 2];
    }

    // Usa o Golden Ratio aproximado do último termo para ajustar o threshold
    if (MATRIX.fib_sequence[FIB_DEPTH - 2] != 0u) {
        float phi_approx = (float)MATRIX.fib_sequence[FIB_DEPTH - 1] /
                           (float)MATRIX.fib_sequence[FIB_DEPTH - 2];
        MATRIX.threshold_dynamic *= (phi_approx / 1.618f); // Modulação fina
    }
}

// [MODULE B] GÊNESE TOROIDAL (RAFAELIA_TOROIDAL)
// Inicializa o campo tensor com geometria sagrada
static void _op_genesis(void) {
    memset(&MATRIX, 0, sizeof(MatrixCore));
    MATRIX.state_crc = 0x52414641u; // "RAFA" seed
    MATRIX.global_energy = 0.0f;
    MATRIX.threshold_dynamic = 0.0f;
    MATRIX.last_avalanches = 0;

    float inv_sigma = 1.0f / (2.0f * R_MINOR * R_MINOR);

    for (int i = 0; i < TOTAL_CELLS; i++) {
        int z = i % GRID_DIM;
        int y = (i / GRID_DIM) % GRID_DIM;
        int x = i / (GRID_DIM * GRID_DIM);

        float d2   = _torus_dist_sq(x, y, z);
        float val  = expf(-d2 * inv_sigma);
        MATRIX.tensor[i] = val;
        MATRIX.global_energy += val;
    }

    MATRIX.state_crc = _fast_crc(MATRIX.state_crc,
                                 &MATRIX.global_energy,
                                 sizeof(MATRIX.global_energy));
}

// [MODULE C] SOC ENGINE (AVALANCHES & VIZINHANÇA)
// Self-Organized Criticality com bit-stacking
static void _op_soc_cycle(void) {
    // Threshold baseado na energia do ciclo anterior
    float mean = MATRIX.global_energy / (float)TOTAL_CELLS;
    MATRIX.threshold_dynamic = mean * 1.8f;

    // Modulação Fibonacci (atemporal)
    _op_fib_modulation();

    int   avalanches = 0;
    float *t = MATRIX.tensor;

    MATRIX.global_energy = 0.0f; // Será recomputada neste ciclo

    for (int i = 0; i < TOTAL_CELLS; i++) {
        if (t[i] > MATRIX.threshold_dynamic) {
            float excess = t[i] * 0.5f;   // Dissipa 50%
            t[i] -= excess;
            float give = excess * 0.03f;  // Distribui parte aos vizinhos

            // Vizinhança linear (aproximação de tunelamento)
            if (i > 0)                 t[i - 1]          += give;
            if (i < TOTAL_CELLS - 1)   t[i + 1]          += give;
            if (i >= GRID_DIM)         t[i - GRID_DIM]   += give;
            if (i < TOTAL_CELLS - GRID_DIM) t[i + GRID_DIM] += give;

            avalanches++;
        }

        // Dissipação natural
        t[i] *= 0.99f;
        MATRIX.global_energy += t[i];
    }

    MATRIX.last_avalanches = avalanches;

    // Atualiza CRC com o número de avalanches (o "tempo" avança)
    MATRIX.state_crc = _fast_crc(MATRIX.state_crc,
                                 &avalanches,
                                 sizeof(avalanches));
    MATRIX.cycle_count++;
}

// [MODULE D] ZIPRAF & BITRAF (VETORIZAÇÃO & HASH)
// Comprime o tensor 1M -> 64 floats (Assinatura Vetorial)
static void _op_zipraf(void) {
    memset(MATRIX.vector_space, 0, VEC_DIM * sizeof(float));

    int stride = TOTAL_CELLS / VEC_DIM;

    for (int v = 0; v < VEC_DIM; v++) {
        float local_sum = 0.0f;
        int base = v * stride;
        for (int k = 0; k < stride; k++) {
            local_sum += MATRIX.tensor[base + k];
        }
        MATRIX.vector_space[v] = local_sum / (float)stride; // Média do bloco
    }

    // BITRAF Log
    (void)snprintf(MATRIX.log_buffer[MATRIX.log_head],
                   sizeof(MATRIX.log_buffer[MATRIX.log_head]),
                   "ZIP: Cycle %u | CRC %08X",
                   MATRIX.cycle_count,
                   MATRIX.state_crc);
    MATRIX.log_head = (uint8_t)((MATRIX.log_head + 1u) % 8u);
}

// --- 5. INTERFACE & RELATÓRIO ---

static void _interface_render(void) {
    printf("\033[2J\033[H"); // Clear
    printf(C_CYN "=== RAFAELIA HYPER-CORE [v777] ===\n" C_RST);
    printf("CRC STATE : " C_MAG "%08X" C_RST " (Atemporal Tick)\n", MATRIX.state_crc);
    printf("ENERGY    : %.4f\n", MATRIX.global_energy);
    printf("AVALANCHES: %d (Last Cycle)\n", MATRIX.last_avalanches);
    printf("CYCLES    : %u\n", MATRIX.cycle_count);
    printf("FIBONACCI : %" PRIu64 " (Modulation)\n",
           MATRIX.fib_sequence[FIB_DEPTH - 1]);

    printf("\n" C_GRN "[VECTOR SPACE - ZIPRAF]" C_RST "\n");
    printf("[ ");
    for (int i = 0; i < 8; i++) {
        printf("%.2f ", MATRIX.vector_space[i]);
    }
    printf("... ]\n");

    printf("\n" C_CYN "[LOG RING]" C_RST "\n");
    for (int i = 0; i < 8; i++) {
        int idx = (MATRIX.log_head + i) % 8;
        if (MATRIX.log_buffer[idx][0] != '\0') {
            printf(" > %s\n", MATRIX.log_buffer[idx]);
        }
    }
}

// --- 6. BENCHMARK MODE ---

static void _run_benchmark(int steps) {
    _op_genesis();

    clock_t start = clock();
    for (int step = 0; step < steps; step++) {
        _op_soc_cycle();
        _op_zipraf();
    }
    clock_t end = clock();

    double elapsed = (double)(end - start) / (double)CLOCKS_PER_SEC;
    if (elapsed <= 0.0) elapsed = 1e-9; // evita div. por zero

    double steps_s    = (double)steps / elapsed;
    double cellupd_s  = ((double)steps * (double)TOTAL_CELLS) / elapsed;

    printf("[BENCH] RAFAELIA HYPER-CORE\n");
    printf("[BENCH] Grid       : %dx%dx%d (N=%d)\n",
           GRID_DIM, GRID_DIM, GRID_DIM, TOTAL_CELLS);
    printf("[BENCH] Steps      : %d\n", steps);
    printf("[BENCH] Elapsed    : %.6f s\n", elapsed);
    printf("[BENCH] Steps/s    : %.2f\n", steps_s);
    printf("[BENCH] CellUpd/s  : %.2f\n", cellupd_s);
    printf("[BENCH] Energy     : %.4f\n", MATRIX.global_energy);
    printf("[BENCH] Hash       : %08X\n", MATRIX.state_crc);
}

// --- 7. MAIN LOOP ---

int main(int argc, char *argv[]) {
    // Modo benchmark: ./hyper_core --bench [steps]
    if (argc > 1 && strcmp(argv[1], "--bench") == 0) {
        int steps = SOC_STEPS;
        if (argc > 2) {
            int tmp = atoi(argv[2]);
            if (tmp > 0) {
                steps = tmp;
            }
        }
        _run_benchmark(steps);
        return 0;
    }

    // Modo interativo simples (pipeline visual)
    _op_genesis();

    for (int step = 0; step < SOC_STEPS; step++) {
        _op_soc_cycle();
        _op_zipraf();

        if ((step % 5) == 0 || step == (SOC_STEPS - 1)) {
            _interface_render();
        }
    }

    printf("\n" C_MAG "FINAL STATE HASH: %08X" C_RST "\n", MATRIX.state_crc);
    return 0;
}
