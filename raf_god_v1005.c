/*
 * ======================================================================================
 * 🌌 RAFAELIA GOD CORE (v1005 - BENCHMARK EDITION)
 * ======================================================================================
 * ARCH: C11 Native | Monolithic | Zero Dependency
 * UI:   Cyberpunk BBS | ANSI Dashboard | Dynamic ASCII
 * FEAT: Non-Blocking Stats | Phantom Benchmark | Zeta Physics
 * ======================================================================================
 */

/* [1] FEATURE MACROS */
#define _POSIX_C_SOURCE 200809L

/* [2] HEADERS */
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <math.h>
#include <time.h>
#include <inttypes.h>

/* [3] HIPER-CONSTANTES */
#define MAX_CORES 256
#define CACHE_LINE 64
#define VEC_DIM 64
#define FIB_DEPTH 42

/* ANSI COLORS & STYLES */
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
#define C_REV  "\x1b[7m"

/* [4] ESTRUTURAS */

typedef struct {
    uint64_t dim_n;          
    uint64_t phys_ram_mb;    
    uint32_t active_cores;   
    float    decay_rate;     
    float    inject_power;   
    float    threshold;      
    float    zeta_factor;    
} RafConfig;

typedef struct {
    double iops;             /* Integer Ops / Sec */
    double flops;            /* Float Ops / Sec */
    double bandwidth_gb;     /* Memory Bandwidth */
    uint64_t score;          /* RAF-SCORE */
} RafBench;

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
    uint64_t ops_total;      /* Total acumulado */
    uint64_t ops_tick;       /* Ops no último tick */
    float    global_energy;

    uint64_t fib_seq[FIB_DEPTH];
    RafBench last_bench;     /* Último Benchmark */
} GodMatrix;

static GodMatrix MTX;
static RafConfig CFG;

/* [5] KERNEL MATEMÁTICO (Math is Law) */

static inline uint32_t _mix32(uint32_t h) {
    h ^= h >> 16; h *= 0x85ebca6b;
    h ^= h >> 13; h *= 0xc2b2ae35;
    h ^= h >> 16;
    return h;
}

static inline uint32_t _holo_map(uint64_t x, uint64_t y, uint64_t z) {
    uint32_t h = (uint32_t)x ^ (uint32_t)y ^ (uint32_t)z;
    h = _mix32(h); 
    return h & MTX.phys_mask;
}

static inline uint32_t _crc(uint32_t prev, const void *data, size_t len) {
    const uint8_t *p = (const uint8_t *)data;
    uint32_t c = ~prev;
    while (len--) {
        c ^= *p++;
        for (int k = 0; k < 4; k++) c = (c >> 1) ^ (0xEDB88320 & -(int32_t)(c & 1));
    }
    return ~c;
}

static inline uint32_t _rng(uint32_t *state) {
    uint32_t x = *state;
    x ^= x << 13; x ^= x >> 17; x ^= x << 5;
    *state = x;
    return x;
}

static inline float _swiglu(float x) {
    return x / (1.0f + expf(-x));
}

/* [6] GESTÃO DE MEMÓRIA & GÊNESE */

static void _alloc_universe(uint64_t mb_target) {
    if (MTX.tensor) { free(MTX.tensor); MTX.tensor = NULL; }
    if (mb_target < 1) mb_target = 1;
    
    uint64_t bytes = mb_target * 1024ULL * 1024ULL;
    uint64_t cells = bytes / sizeof(float);
    uint64_t pow2 = 1;
    while (pow2 < cells) pow2 <<= 1;
    
    size_t final_bytes = (size_t)(pow2 * sizeof(float));
    if (posix_memalign((void **)&MTX.tensor, CACHE_LINE, final_bytes) != 0) {
        fprintf(stderr, C_RED "FATAL: Alloc Fail.\n" C_RST); exit(1);
    }
    
    memset(MTX.tensor, 0, final_bytes);
    MTX.phys_cells = (uint32_t)pow2;
    MTX.phys_mask  = (uint32_t)(pow2 - 1U);
    CFG.phys_ram_mb = final_bytes / (1024*1024);
    memset(MTX.vector_space, 0, sizeof(MTX.vector_space));
}

static void _genesis(void) {
    memset(&MTX, 0, sizeof(GodMatrix));
    MTX.state_crc = 0x52414641;
    MTX.rng_state = 0xCAFEBABE;
    CFG.dim_n        = 1000;
    CFG.active_cores = 4;
    CFG.decay_rate   = 0.99f;
    CFG.inject_power = 5.0f;
    CFG.threshold    = 4.0f;
    CFG.zeta_factor  = 0.001f;
    _alloc_universe(64);
    MTX.fib_seq[0] = 1; MTX.fib_seq[1] = 1;
    for(int i=2; i<FIB_DEPTH; i++) MTX.fib_seq[i] = MTX.fib_seq[i-1] + MTX.fib_seq[i-2];
}

/* [7] FÍSICA & SIMULAÇÃO */

static void _process_physics(void) {
    MTX.ops_tick = 0;
    for (uint32_t c = 0; c < CFG.active_cores; c++) {
        uint32_t head = MTX.cursor_heads[c];
        for (uint32_t k = 0; k < head; k++) {
            uint32_t idx = MTX.cursors[c][k] & MTX.phys_mask;
            float val = MTX.tensor[idx];
            if (val > CFG.threshold) {
                float excess = val * 0.5f;
                MTX.tensor[idx] -= excess;
                uint32_t l = (idx - 1u) & MTX.phys_mask;
                uint32_t r = (idx + 1u) & MTX.phys_mask;
                MTX.tensor[l] += excess * 0.25f;
                MTX.tensor[r] += excess * 0.25f;
                MTX.ops_tick++;
            }
            MTX.tensor[idx] = (MTX.tensor[idx] * CFG.decay_rate) + CFG.zeta_factor;
        }
        MTX.cursor_heads[c] = 0;
    }
    MTX.ops_total += MTX.ops_tick;
    MTX.cycle_count++;
}

static void _inject_energy(uint64_t vx, uint64_t vy, uint64_t vz, float energy) {
    uint32_t p_idx = _holo_map(vx, vy, vz);
    uint32_t core = p_idx % CFG.active_cores;
    uint32_t head = MTX.cursor_heads[core];
    if (head < 4096u) {
        MTX.cursors[core][head] = p_idx;
        MTX.cursor_heads[core]++;
    }
    MTX.tensor[p_idx] += energy;
    MTX.global_energy += energy;
    MTX.state_crc = _crc(MTX.state_crc, &p_idx, sizeof(uint32_t));
    uint32_t slot = p_idx & (VEC_DIM - 1u);
    MTX.vector_space[slot] += _swiglu(energy);
}

/* [8] BENCHMARK ENGINE (O "PHANTOM") */

static void _run_benchmark(void) {
    printf(C_CYN "\n >> INITIATING PHANTOM BENCHMARK PROTOCOL...\n" C_RST);
    
    // Setup
    clock_t start = clock();
    uint64_t ops_start = MTX.ops_total;
    uint64_t cycles = 0;
    double elapsed = 0.0;
    
    // Burst Mode: Executa física intensa por ~1 segundo (tempo real)
    // Não usamos sleep, usamos loop de CPU
    while (elapsed < 1.0) {
        // Injeta ruído pesado
        for(int k=0; k<100; k++) {
            uint32_t r = _rng(&MTX.rng_state);
            _inject_energy(r, r>>10, r>>20, 10.0f);
        }
        _process_physics();
        cycles++;
        elapsed = (double)(clock() - start) / CLOCKS_PER_SEC;
    }
    
    uint64_t ops_delta = MTX.ops_total - ops_start;
    
    // Cálculo de Métricas
    MTX.last_bench.iops = (double)ops_delta / elapsed; // Interações de células
    MTX.last_bench.flops = (double)cycles * 100.0 * 20.0; // Estimativa de FLOPs no kernel
    
    // Largura de banda estimada: (Floats Lidos + Escritos) * 4 bytes * Ops
    // SOC toca 3 células por op (Self, Left, Right)
    MTX.last_bench.bandwidth_gb = (MTX.last_bench.iops * 3.0 * 4.0) / (1024.0*1024.0*1024.0);
    
    // RAF-SCORE: Métrica sintética proprietária
    MTX.last_bench.score = (uint64_t)(MTX.last_bench.iops / 1000.0) + (uint64_t)(MTX.last_bench.bandwidth_gb * 1000.0);
}

/* [9] VISUALIZAÇÃO & UI (BBS STYLE) */

static void _draw_bar(float val, float max, int width, const char* color) {
    int fill = (int)((val / max) * width);
    if(fill > width) fill = width;
    printf("%s[", C_DIM);
    printf("%s", color);
    for(int i=0; i<fill; i++) printf("=");
    printf("%s", C_DIM);
    for(int i=fill; i<width; i++) printf(".");
    printf("]" C_RST);
}

static void _render_dashboard(void) {
    printf("\033[2J\033[H"); // Clear
    
    // Header
    printf(C_CYN  " ╔════════════════════════════════════════════════════════════════════╗\n");
    printf( " ║  " C_BLD C_WHT "RAFAELIA GOD CORE" C_RST C_CYN " :: " C_MAG "v1005 BENCHMARK EDITION" C_RST C_CYN "                    ║\n");
    printf( " ╠════════════════════════════════════════════════════════════════════╣\n");
    
    // System Stats (Left)
    printf( " ║ " C_BLD "SYSTEM METRICS" C_RST "                 │ " C_BLD "HOLOGRAPHIC STATUS" C_RST "             ║\n");
    printf( " ║ RAM Alloc : " C_GRN "%4" PRIu64 " MB" C_RST "           │ Dim Virtual : " C_YEL "%" PRIu64 "^3" C_RST "            ║\n", CFG.phys_ram_mb, CFG.dim_n);
    printf( " ║ Phys Mask : " C_DIM "%08X" C_RST "          │ State Hash  : " C_MAG "%08X" C_RST "            ║\n", MTX.phys_mask, MTX.state_crc);
    printf( " ║ Logic Lanes: " C_CYN "%-3u" C_RST "               │ Global Nrg  : " C_RED "%.2e" C_RST "            ║\n", CFG.active_cores, MTX.global_energy);
    
    // Physics Stats (Bars)
    printf( " ╠════════════════════════════════════════════════════════════════════╣\n");
    printf( " ║ " C_BLD "REALITY PHYSICS" C_RST "                                                    ║\n");
    printf( " ║ Entropy Decay : "); _draw_bar(CFG.decay_rate, 1.0f, 20, C_BLU); printf(" %.3f      ║\n", CFG.decay_rate);
    printf( " ║ Ops Intensity : "); _draw_bar((float)MTX.ops_tick, 5000.0f, 20, C_RED); printf(" %-6" PRIu64 "     ║\n", MTX.ops_tick);
    
    // Benchmark Results (Condicional)
    printf( " ╠════════════════════════════════════════════════════════════════════╣\n");
    if(MTX.last_bench.score > 0) {
        printf( " ║ " C_BLD "BENCHMARK RESULT (PHANTOM)" C_RST "                                         ║\n");
        printf( " ║ " C_WHT "RAF-SCORE : " C_REV C_YEL " %" PRIu64 " " C_RST "                                              ║\n", MTX.last_bench.score);
        printf( " ║ IOPS      : " C_GRN "%.2f M/s" C_RST "    Bandwidth : " C_CYN "%.2f GB/s" C_RST "           ║\n", MTX.last_bench.iops/1000000.0, MTX.last_bench.bandwidth_gb);
    } else {
        printf( " ║ " C_DIM "BENCHMARK : WAITING FOR TEST RUN...                              " C_RST "║\n");
        printf( " ║                                                                    ║\n");
    }
    
    // Menu
    printf( " ╠════════════════════════════════════════════════════════════════════╣\n");
    printf( " ║ " C_WHT "[1]" C_RST " Resize Uni  " C_WHT "[2]" C_RST " Pulse Inject  " C_WHT "[3]" C_RST " Simulation Loop            ║\n");
    printf( " ║ " C_WHT "[B]" C_RST C_YEL " RUN BENCHMARK (1s Burst)" C_RST "     " C_WHT "[0]" C_RST " Exit                       ║\n");
    printf( " ╚════════════════════════════════════════════════════════════════════╝\n");
    printf("\nCMD> ");
}

static void _safe_pause(void) {
    int c; while ((c = getchar()) != '\n' && c != EOF);
    printf("\n" C_DIM "Press Enter to continue..." C_RST); (void)getchar();
}

/* [10] MAIN & INPUT HANDLING */

int main(int argc, char *argv[]) {
    _genesis();
    
    // CLI Bridge Mode (Silent / JSON)
    if (argc > 1 && strcmp(argv[1], "--bridge") == 0) {
        _bridge_mode();
        if(MTX.tensor) free(MTX.tensor);
        return 0;
    }
    
    // Interactive BBS Mode
    char cmd;
    uint64_t val;
    
    for(;;) {
        _render_dashboard();
        
        if (scanf(" %c", &cmd) != 1) cmd = '0';
        // Consome newline que sobrou do scanf para não bugar o próximo getchar
        int c; while((c=getchar())!='\n' && c!=EOF); 

        if (cmd == '0') break;
        
        switch(cmd) {
            case '1':
                printf(C_CYN "New Virtual Dimension (N): " C_RST);
                if(scanf("%" PRIu64, &val)==1) CFG.dim_n = val;
                break;
                
            case '2': // Pulse
                _inject_energy(_rng(&MTX.rng_state), _rng(&MTX.rng_state), 
                               MTX.cycle_count, CFG.inject_power * 10.0f);
                _process_physics();
                break;
                
            case '3': // Simulação Visual
                printf(C_GRN "\nRunning Simulation (Ctrl+C to stop)..." C_RST "\n");
                for(int i=0; i<1000; i++) {
                    uint32_t r = _rng(&MTX.rng_state);
                    _inject_energy(r, r>>10, r>>20, 2.0f);
                    _process_physics();
                    if(i%50==0) { 
                        printf(C_MAG "." C_RST); fflush(stdout); 
                    }
                }
                _safe_pause();
                break;
                
            case 'B': // Benchmark
            case 'b':
                _run_benchmark();
                _safe_pause();
                break;
                
            default:
                break;
        }
    }
    
    if (MTX.tensor) free(MTX.tensor);
    printf(C_RED "\n[ SYSTEM HALTED ]\n" C_RST);
    return 0;
}
