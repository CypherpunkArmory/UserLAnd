/*
 * ======================================================================================
 * 💎 RAFAELIA HYPER-CORE v800 (BRIDGE EDITION)
 * ======================================================================================
 * MODULES: TORO_SOC + BITRAF + ZIPRAF + FIB_42 + INGEST_BRIDGE
 * ARCH: C11 Native | Stdin Streaming | CRC-Driven Physics | Zero-malloc
 * INTEGRATION: Compatible with raf_ingest_bridge.py (pipe/bridge)
 * ======================================================================================
 */

#include <stdio.h>
#include <stdint.h>
#include <math.h>
#include <string.h>
#include <stdlib.h>
#include <time.h>

// --- 1. HIPER-PARÂMETROS ---
#define GRID_DIM    100
#define TOTAL_CELLS (GRID_DIM * GRID_DIM * GRID_DIM)
#define VEC_DIM     64
#define FIB_DEPTH   42
#define CHUNK_SIZE  8192  // alinhado com buffer típico de pipe

// Cores ANSI
#define C_RST  "\x1b[0m"
#define C_CYN  "\x1b[36m"
#define C_GRN  "\x1b[32m"
#define C_MAG  "\x1b[35m"

// --- 2. MATRIX DE MEMÓRIA UNIFICADA ---
typedef struct __attribute__((aligned(64))) {
    // Física
    float tensor[TOTAL_CELLS];
    float vector_space[VEC_DIM];

    // Lógica Atemporal
    uint64_t fib_sequence[FIB_DEPTH];
    uint32_t state_crc;
    uint32_t cycle_count;
    uint64_t bytes_ingested;

    // Dinâmica
    float global_energy;
    float threshold_dynamic;

    // Log Buffer
    char   log_buffer[8][64];
    uint8_t log_head;
} MatrixCore;

static MatrixCore MATRIX;

// --- 3. MATH KERNEL (Low-Latency / CRC como tempo) ---

// CRC "rápido" (mistura de word-wise + bitwise; foco em avalanche de bits)
static inline uint32_t _fast_crc(uint32_t prev, const void* data, size_t len) {
    const uint8_t* p = (const uint8_t*) data;
    uint32_t c = ~prev;

    // mistura em blocos de 4 bytes (atenção a alinhamento em arquiteturas estritas)
    while (len >= 4) {
        uint32_t w;
        memcpy(&w, p, sizeof(uint32_t));
        c ^= w;
        c = (c << 13) | (c >> 19);
        c *= 0x5BD1E995u;
        p   += 4;
        len -= 4;
    }
    // resto byte a byte
    while (len--) {
        c ^= *p++;
        c = (c >> 1) ^ (0xEDB88320u & (-(int32_t)(c & 1u)));
    }
    return ~c;
}

// --- 4. MÓDULOS DO NÚCLEO ---

// Gênese: inicializa tudo e constrói um "núcleo quente" no centro
static void _op_genesis(void) {
    memset(&MATRIX, 0, sizeof(MatrixCore));
    MATRIX.state_crc = 0x52414641u; // "RAFA"

    float cx = GRID_DIM * 0.5f;
    float* t = MATRIX.tensor;

    for (int i = 0; i < TOTAL_CELLS; i++, t++) {
        int z = i % GRID_DIM;
        int y = (i / GRID_DIM) % GRID_DIM;
        int x = i / (GRID_DIM * GRID_DIM);

        float dx = x - cx;
        float dy = y - cx;
        float dz = z - cx;
        float d2 = dx*dx + dy*dy + dz*dz;

        if (d2 < 400.0f) {
            *t = 1.0f;
            MATRIX.global_energy += 1.0f;
        } else {
            *t = 0.0f;
        }
    }

    MATRIX.state_crc = _fast_crc(MATRIX.state_crc, &MATRIX.global_energy, sizeof(MATRIX.global_energy));
}

// Modulação Fibonacci (usa CRC como semente simbólica)
static void _op_fib_modulation(void) {
    MATRIX.fib_sequence[0] = (uint64_t)((MATRIX.state_crc % 99u) + 1u);
    MATRIX.fib_sequence[1] = 1u;

    for (int i = 2; i < FIB_DEPTH; i++) {
        MATRIX.fib_sequence[i] = MATRIX.fib_sequence[i - 1] + MATRIX.fib_sequence[i - 2];
    }
}

// SOC Physics: um passo de avalanche + dissipação + recálculo de energia
static void _op_soc_physics(void) {
    // usa energia do ciclo anterior para definir limiar
    float mean_prev = MATRIX.global_energy / (float) TOTAL_CELLS;
    MATRIX.threshold_dynamic = mean_prev * 1.5f;
    if (MATRIX.threshold_dynamic < 0.001f) {
        MATRIX.threshold_dynamic = 0.001f;
    }

    _op_fib_modulation(); // modula de forma atemporal

    float* t = MATRIX.tensor;
    double sum = 0.0;
    int avalanches = 0;

    for (int i = 0; i < TOTAL_CELLS; i++) {
        float val = t[i];

        if (val > MATRIX.threshold_dynamic) {
            float excess = val * 0.5f;
            val -= excess;

            float give = excess * 0.4f;

            if (i < TOTAL_CELLS - 1)   t[i + 1] += give;
            if (i > 0)                 t[i - 1] += give;

            avalanches++;
        }

        val *= 0.98f;  // dissipação natural
        t[i] = val;
        sum += (double) val;
    }

    MATRIX.global_energy = (float) sum;
    MATRIX.cycle_count++;
    MATRIX.state_crc = _fast_crc(MATRIX.state_crc, &avalanches, sizeof(avalanches));
}

// ZIPRAF: comprime o tensor 1M → 64 floats de assinatura vetorial
static void _op_zipraf(void) {
    memset(MATRIX.vector_space, 0, sizeof(MATRIX.vector_space));

    const float* t = MATRIX.tensor;
    int stride = TOTAL_CELLS / VEC_DIM;

    for (int v = 0; v < VEC_DIM; v++) {
        double acc = 0.0;
        int base = v * stride;
        for (int k = 0; k < stride; k++) {
            acc += (double) t[base + k];
        }
        MATRIX.vector_space[v] = (float) (acc / (double) stride);
    }

    snprintf(MATRIX.log_buffer[MATRIX.log_head], sizeof(MATRIX.log_buffer[0]),
             "ZIP: Cycle %u | CRC %08X",
             MATRIX.cycle_count, MATRIX.state_crc);
    MATRIX.log_head = (uint8_t)((MATRIX.log_head + 1u) % 8u);
}

// --- 5. INGEST BRIDGE (Stdin → Física + Vetor + JSON) ---

static void _op_ingest_bridge(void) {
    uint8_t buffer[CHUNK_SIZE];
    size_t bytes_read;

    snprintf(MATRIX.log_buffer[MATRIX.log_head], sizeof(MATRIX.log_buffer[0]),
             "BRIDGE: Listening on stdin...");
    MATRIX.log_head = (uint8_t)((MATRIX.log_head + 1u) % 8u);

    while ((bytes_read = fread(buffer, 1u, CHUNK_SIZE, stdin)) > 0u) {
        MATRIX.bytes_ingested += (uint64_t) bytes_read;

        // 1) Atualiza o "tempo" atemporal
        MATRIX.state_crc = _fast_crc(MATRIX.state_crc, buffer, bytes_read);

        // 2) Injeta energia em um ponto escolhido pelo hash
        uint32_t idx = MATRIX.state_crc % (uint32_t) TOTAL_CELLS;
        float energy_boost = (float) (buffer[0] % 10u) * 0.5f;
        MATRIX.tensor[idx] += energy_boost;

        // 3) Passo de física + compressão
        _op_soc_physics();
        _op_zipraf();

        // 4) Batimento cardíaco para o Python (raf_ingest_bridge.py)
        if (MATRIX.cycle_count % 10u == 0u) {
            printf(
                "{\"crc\":\"%08X\",\"cycles\":%u,\"bytes\":%llu,\"energy\":%.2f}\n",
                MATRIX.state_crc,
                MATRIX.cycle_count,
                (unsigned long long) MATRIX.bytes_ingested,
                (double) MATRIX.global_energy
            );
            fflush(stdout);
        }
    }
}

// --- 6. VISUALIZAÇÃO (MODO STANDALONE) ---

static void _render_cli(void) {
    printf("\033[2J\033[H");
    printf(C_CYN "RAFAELIA HYPER-CORE [BRIDGE MODE]\n" C_RST);
    printf("CRC   : %08X\n", MATRIX.state_crc);
    printf("BYTES : %llu\n", (unsigned long long) MATRIX.bytes_ingested);
    printf("ENERGY: %.4f\n", MATRIX.global_energy);

    printf(C_GRN "VEC[0..7]: " C_RST "[ ");
    for (int i = 0; i < 8; i++) {
        printf("%.3f ", MATRIX.vector_space[i]);
    }
    printf("... ]\n");
}

// --- 7. BENCHMARK MODE ---

static void _run_bench(int steps) {
    if (steps <= 0) steps = 20;

    _op_genesis();

    clock_t start = clock();
    for (int i = 0; i < steps; i++) {
        _op_soc_physics();
        _op_zipraf();
    }
    clock_t end = clock();

    double elapsed = (double)(end - start) / (double) CLOCKS_PER_SEC;
    double steps_s = (double) steps / elapsed;
    double cellupd_s = ((double) steps * (double) TOTAL_CELLS) / elapsed;

    printf("[BENCH] RAFAELIA HYPER-CORE v800\n");
    printf("[BENCH] Grid       : %dx%dx%d (N=%d)\n", GRID_DIM, GRID_DIM, GRID_DIM, TOTAL_CELLS);
    printf("[BENCH] Steps      : %d\n", steps);
    printf("[BENCH] Elapsed    : %.6f s\n", elapsed);
    printf("[BENCH] Steps/s    : %.2f\n", steps_s);
    printf("[BENCH] CellUpd/s  : %.2f\n", cellupd_s);
    printf("[BENCH] Energy     : %.4f\n", MATRIX.global_energy);
    printf("[BENCH] Hash       : %08X\n", MATRIX.state_crc);
}

// --- 8. MAIN ---

int main(int argc, char *argv[]) {
    _op_genesis();

    if (argc > 1) {
        if (strcmp(argv[1], "--bridge") == 0) {
            _op_ingest_bridge();
            return 0;
        } else if (strcmp(argv[1], "--bench") == 0) {
            int steps = 20;
            if (argc > 2) {
                steps = atoi(argv[2]);
            }
            _run_bench(steps);
            return 0;
        }
    }

    // Modo demo standalone (sem bridge / sem bench)
    printf("Iniciando Modo Standalone (use --bridge para integrar, --bench para medir)...\n");
    for (int i = 0; i < 60; i++) {
        _op_soc_physics();
        _op_zipraf();
        if (i % 10 == 0) {
            _render_cli();
        }
    }
    printf("Concluído. Final CRC: %08X\n", MATRIX.state_crc);
    return 0;
}
