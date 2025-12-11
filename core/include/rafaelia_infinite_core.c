/*
 * ======================================================================================
 * 🌌 RAFAELIA INFINITE CORE (v100k - HOLOGRAPHIC MATRIX)
 * ======================================================================================
 * VIRTUAL DIM: 100,000^3 (1 Quadrillion Cells)
 * PHYSICAL MEM: Fixed Buffer (~256MB de floats)
 * LOGIC: Spatial Hashing + SOC Physics (sparse) + CRC Time
 * ======================================================================================
 */

#include <stdio.h>
#include <stdint.h>
#include <math.h>
#include <string.h>
#include <stdlib.h>
#include <time.h>

// --- 1. HIPER-DIMENSÕES ---
// Universo virtual (onde a lógica "acha" que está)
#define VIRTUAL_DIM 100000ULL

// Realidade física: número de células físicas (potência de 2 para usar máscara)
#define PHYS_CELLS (64u * 1024u * 1024u)   // 64M floats ≈ 256 MB
#define PHYS_MASK  (PHYS_CELLS - 1u)

// --- 2. MATRIX HOLOGRÁFICA ---
typedef struct __attribute__((aligned(64))) {
    float    tensor[PHYS_CELLS];     // Memória física linear
    uint32_t active_cursors[1024];   // Índices físicos onde a energia vive
    uint32_t cursor_head;

    uint32_t state_crc;
    uint32_t cycle_count;
    float    global_energy;
} InfiniteMatrix;

static InfiniteMatrix MATRIX;

// --- 3. MATH KERNEL (ATEMPORAL & HASHING) ---

// Mapeia coord virtual (x,y,z) -> índice físico linear
// Hash estilo FNV-1a modificado (espalha vizinhos virtuais)
static inline uint32_t _holographic_map(uint64_t x, uint64_t y, uint64_t z) {
    uint32_t hash = 2166136261u;
    hash ^= (uint32_t) x; hash *= 16777619u;
    hash ^= (uint32_t) y; hash *= 16777619u;
    hash ^= (uint32_t) z; hash *= 16777619u;
    return hash & PHYS_MASK; // 0..PHYS_CELLS-1
}

static inline uint32_t _fast_crc(uint32_t prev, const void *data, size_t len) {
    const uint8_t *p = (const uint8_t *) data;
    uint32_t c = ~prev;
    while (len--) {
        c ^= *p++;
        for (int k = 0; k < 8; k++) {
            c = (c >> 1) ^ (0xEDB88320u & (-(int32_t)(c & 1u)));
        }
    }
    return ~c;
}

// --- 4. OPERAÇÕES DE NÚCLEO ---

static void _op_genesis(void) {
    memset(&MATRIX, 0, sizeof(InfiniteMatrix));
    MATRIX.state_crc = 0x52414641u; // "RAFA"
    MATRIX.cursor_head = 0u;

    printf("GENESIS: Virtual %llu^3 mapped to Physical %u cells (~%.2f MB)\n",
           (unsigned long long) VIRTUAL_DIM,
           (unsigned int) PHYS_CELLS,
           (double) (PHYS_CELLS * sizeof(float)) / (1024.0 * 1024.0));
}

// Injeta energia em uma coordenada virtual
static void _op_inject_virtual(uint64_t vx, uint64_t vy, uint64_t vz, float energy) {
    uint32_t p_idx = _holographic_map(vx, vy, vz);

    // Injeção física
    MATRIX.tensor[p_idx] += energy;
    MATRIX.global_energy += energy;

    // Tempo = evento (CRC do índice físico)
    MATRIX.state_crc = _fast_crc(MATRIX.state_crc, &p_idx, sizeof(uint32_t));

    // Marca cursor para SOC sparse
    MATRIX.active_cursors[MATRIX.cursor_head] = p_idx;
    MATRIX.cursor_head = (MATRIX.cursor_head + 1u) % 1024u;
}

// Física SOC (Avalanche) apenas nas regiões ativas (sparse)
static void _op_soc_sparse(void) {
    int avalanches = 0;

    for (int k = 0; k < 1024; k++) {
        uint32_t idx = MATRIX.active_cursors[k];

        float val = MATRIX.tensor[idx];

        // Threshold fixo de demo; poderia ser adaptativo
        if (val > 4.0f) {
            float excess = val * 0.5f;
            val -= excess;

            uint32_t left  = (idx - 1u) & PHYS_MASK;
            uint32_t right = (idx + 1u) & PHYS_MASK;

            MATRIX.tensor[left]  += excess * 0.25f;
            MATRIX.tensor[right] += excess * 0.25f;

            avalanches++;
        }

        // Decaimento local
        val *= 0.95f;
        MATRIX.tensor[idx] = val;
    }

    MATRIX.cycle_count++;
    MATRIX.state_crc = _fast_crc(MATRIX.state_crc, &avalanches, sizeof(avalanches));
}

// Recalcula energia global (para métricas / bench)
// Custo O(PHYS_CELLS); usar só em bench ou snapshots
static void _recalc_energy(void) {
    double sum = 0.0;
    for (uint32_t i = 0; i < PHYS_CELLS; i++) {
        sum += (double) MATRIX.tensor[i];
    }
    MATRIX.global_energy = (float) sum;
}

// --- 5. VISUALIZAÇÃO ---

static void _render_status(void) {
    printf("\r[CYCLE %u] CRC:%08X | ENERGY: %.2f",
           MATRIX.cycle_count, MATRIX.state_crc, MATRIX.global_energy);
    fflush(stdout);
}

// --- 6. BENCHMARK ---
// steps           = quantos ciclos SOC
// injections_step = quantas injeções virtuais por passo
static void _run_bench(int steps, int injections_step) {
    if (steps <= 0) steps = 10000;
    if (injections_step <= 0) injections_step = 1;

    _op_genesis();

    uint64_t vx = VIRTUAL_DIM / 2;
    uint64_t vy = VIRTUAL_DIM / 2;
    uint64_t vz = VIRTUAL_DIM / 2;

    uint64_t total_injections = 0;

    clock_t start = clock();

    for (int s = 0; s < steps; s++) {
        for (int j = 0; j < injections_step; j++) {
            // caminhada pseudo-aleatória determinística no espaço virtual
            vx = (vx + (MATRIX.state_crc % 3u) + VIRTUAL_DIM - 1u) % VIRTUAL_DIM;
            vy = (vy + ((MATRIX.state_crc >> 4) % 3u) + VIRTUAL_DIM - 1u) % VIRTUAL_DIM;
            vz = (vz + ((MATRIX.state_crc >> 8) % 3u) + VIRTUAL_DIM - 1u) % VIRTUAL_DIM;

            _op_inject_virtual(vx, vy, vz, 1.5f);
            total_injections++;
        }
        _op_soc_sparse();
    }

    clock_t end = clock();
    double elapsed = (double) (end - start) / (double) CLOCKS_PER_SEC;

    // Recalcula energia para relatório final
    _recalc_energy();

    double steps_s        = (double) steps / elapsed;
    double inj_s          = (double) total_injections / elapsed;
    double soc_cells_s    = (double) steps * 1024.0 / elapsed; // 1024 cursores por passo

    printf("\n[BENCH] RAFAELIA INFINITE CORE\n");
    printf("[BENCH] Virtual Dim     : %llu^3 (1e15 cells conceituais)\n",
           (unsigned long long) VIRTUAL_DIM);
    printf("[BENCH] Physical Cells  : %u\n", (unsigned int) PHYS_CELLS);
    printf("[BENCH] Steps           : %d\n", steps);
    printf("[BENCH] Inj/step        : %d\n", injections_step);
    printf("[BENCH] Total Inj       : %llu\n", (unsigned long long) total_injections);
    printf("[BENCH] Elapsed         : %.6f s\n", elapsed);
    printf("[BENCH] Steps/s         : %.2f\n", steps_s);
    printf("[BENCH] Virtual events/s: %.2f (injeções)\n", inj_s);
    printf("[BENCH] SOC-cells/s     : %.2f (1024 cursores/step)\n", soc_cells_s);
    printf("[BENCH] Energy          : %.4f\n", MATRIX.global_energy);
    printf("[BENCH] Hash            : %08X\n", MATRIX.state_crc);
}

// --- 7. MAIN ---

int main(int argc, char *argv[]) {
    if (argc > 1 && strcmp(argv[1], "--bench") == 0) {
        int steps = 10000;
        int inj   = 1;
        if (argc > 2) steps = atoi(argv[2]);
        if (argc > 3) inj   = atoi(argv[3]);
        _run_bench(steps, inj);
        return 0;
    }

    // Modo demo original (10000 ciclos com 1 injeção/ciclo)
    _op_genesis();

    uint64_t vx = VIRTUAL_DIM / 2;
    uint64_t vy = VIRTUAL_DIM / 2;
    uint64_t vz = VIRTUAL_DIM / 2;

    for (int i = 0; i < 10000; i++) {
        vx = (vx + (MATRIX.state_crc % 3u) + VIRTUAL_DIM - 1u) % VIRTUAL_DIM;
        vy = (vy + ((MATRIX.state_crc >> 4) % 3u) + VIRTUAL_DIM - 1u) % VIRTUAL_DIM;
        vz = (vz + ((MATRIX.state_crc >> 8) % 3u) + VIRTUAL_DIM - 1u) % VIRTUAL_DIM;

        _op_inject_virtual(vx, vy, vz, 1.5f);
        _op_soc_sparse();

        if (i % 100 == 0) {
            _recalc_energy();
            _render_status();
        }
    }

    _recalc_energy();
    printf("\n\nFINAL HASH : %08X\n", MATRIX.state_crc);
    printf("FINAL ENERGY: %.4f\n", MATRIX.global_energy);
    return 0;
}
