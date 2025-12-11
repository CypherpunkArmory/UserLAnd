/*
 * ======================================================================================
 * 🌌 RAFAELIA MATRIX CORE (v500.1 - ATEMPORAL + METRICS)
 * ======================================================================================
 * ARCH: C11 | CRC Task Stacking | SwiGLU-C | ECC Memory | BBS Interface | Bench Mode
 * TARGET: High-Performance / Low-Footprint / Deterministic
 * AUTHOR: Rafael & Co-Simbiotic Engine
 * LICENSE: RAFCODE-Φ
 *
 * Notes (Normative / Safety):
 * - No dynamic allocation on hot path (predictable footprint).
 * - Benchmark mode is explicit (--bench) to avoid hidden user penalty.
 * - I/O benchmark writes to a temporary file and removes it afterwards.
 * ======================================================================================
 */

#include <stdio.h>
#include <stdlib.h>
#include <stdint.h>
#include <string.h>
#include <math.h>
#include <time.h>
#include <stdarg.h>

/* ---------------------------------------------------------------------------
 * 1. HYPER-CONSTANTS & CONFIGURATION
 * --------------------------------------------------------------------------- */

#define VERSION       "v500.1-MATRIX"
#define MAX_NODES     256      /* Logical neurons */
#define DIM_VEC       64       /* Vector dimension (cache-friendly) */
#define LANES         4        /* Simulated parallel lanes */
#define CRC_POLY      0xEDB88320
#define CONFIG_FILE   "raf_matrix.bin"
#define BENCH_FILE    "raf_matrix_bench.bin"

/* ANSI Colors (BBS style) */
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

/* State flags (can be extended to map to ISO/NIST semantic states) */
#define F_ACTIVE  0x01
#define F_ERROR   0x02
#define F_SYNC    0x04
#define F_AUDIT   0x08

/* ---------------------------------------------------------------------------
 * 2. DATA STRUCTURES (MATRIX-ALIGNED)
 * --------------------------------------------------------------------------- */

/* Configuration block (persistent) */
typedef struct {
    uint32_t magic;         /* Magic tag for validation ("RAFA") */
    float    learning_rate;
    float    entropy_threshold;
    uint32_t watchdog_limit; /* ms */
    uint8_t  color_mode;
    uint8_t  cluster_mode;  /* 0=Linear, 1=Parallel */
} RafConfig;

/* 64-dim vector */
typedef struct {
    float values[DIM_VEC];
} Vec64;

/* Node ("neuron") */
typedef struct {
    uint32_t id;
    uint32_t layer_idx;
    uint32_t parity_bit;    /* ECC placeholder */
    Vec64    weights;
    float    activation;
    float    bias;
} Node;

/* Global matrix state (single block, easy to dump) */
typedef struct {
    uint32_t state_crc;     /* Atemporal time (hash of state) */
    uint32_t cycle_count;
    uint32_t flags;
    clock_t  last_tick;     /* Watchdog heart-beat */

    Node     nodes[MAX_NODES];

    char     audit_log[8][64]; /* Ring buffer for last events */
    uint8_t  log_head;
} MatrixState;

/* Global instances (low-level style) */
static RafConfig   CFG;
static MatrixState MATRIX;

/* ---------------------------------------------------------------------------
 * 3. MATH KERNEL (CRC, SwiGLU, Dot Product, ECC)
 * --------------------------------------------------------------------------- */

/* CRC32 (table-less, memory-friendly) */
static uint32_t _calc_crc32(const void *data, size_t n, uint32_t prev_crc) {
    uint32_t crc = ~prev_crc;
    const uint8_t *p = (const uint8_t*)data;
    while (n--) {
        crc ^= *p++;
        for (int k = 0; k < 8; k++) {
            crc = (crc >> 1) ^ (0xEDB88320 & (-(crc & 1)));
        }
    }
    return ~crc;
}

/* SwiGLU-like activation: x * sigmoid(x) */
static float _swiglu_fast(float x) {
    float sig = 1.0f / (1.0f + expf(-x));
    return x * sig;
}

/* Dot product (not used now but kept for future extensions) */
static float _dot_prod(const Vec64 *a, const Vec64 *b) {
    float sum = 0.0f;
    for (int i = 0; i < DIM_VEC; i++) {
        sum += a->values[i] * b->values[i];
    }
    return sum;
}

/* ECC integrity check (simple placeholder, always OK for demo)
 * In real-world, parity_bit would be validated here (Hamming/BCH/etc).
 */
static int _verify_node_integrity(Node *n) {
    (void)n; /* unused param for now */
    return 1;
}

/* ---------------------------------------------------------------------------
 * 4. CORE ENGINE (AUDIT, WATCHDOG, GENESIS, PROCESS)
 * --------------------------------------------------------------------------- */

/* Audit logger (stores only in local ring buffer, no disk) */
static void _audit(const char *fmt, ...) {
    va_list args;
    va_start(args, fmt);
    char buf[64];
    vsnprintf(buf, sizeof(buf), fmt, args);
    va_end(args);

    strncpy(MATRIX.audit_log[MATRIX.log_head], buf, 63);
    MATRIX.audit_log[MATRIX.log_head][63] = '\0';
    MATRIX.log_head = (MATRIX.log_head + 1) % 8;
}

/* Watchdog "pet" – keeps last_tick fresh and clears error flag */
static void _watchdog_pet(void) {
    MATRIX.last_tick = clock();
    MATRIX.flags &= ~F_ERROR;
}

/* System genesis – resets MATRIX and sets default configuration */
static void _genesis(void) {
    memset(&MATRIX, 0, sizeof(MatrixState));

    /* Default config */
    CFG.magic            = 0x52414641;  /* "RAFA" */
    CFG.learning_rate    = 0.01f;
    CFG.entropy_threshold = 0.5f;
    CFG.watchdog_limit   = 5000;        /* milliseconds */
    CFG.color_mode       = 1;
    CFG.cluster_mode     = 1;

    /* Deterministic weight initialization */
    uint32_t seed = 0xCAFEBABE;
    for (int i = 0; i < MAX_NODES; i++) {
        MATRIX.nodes[i].id        = (uint32_t)i;
        MATRIX.nodes[i].layer_idx = (uint32_t)(i % 4); /* 4 logical layers */
        MATRIX.nodes[i].bias      = 0.1f;
        for (int j = 0; j < DIM_VEC; j++) {
            seed = _calc_crc32(&seed, sizeof(seed), i);
            MATRIX.nodes[i].weights.values[j] =
                ((float)(seed % 100) / 100.0f) - 0.5f;
        }
    }

    MATRIX.state_crc = 0xFFFFFFFFu;
    MATRIX.flags     = F_ACTIVE;
    _audit("GENESIS: Matrix initialized.");
    _watchdog_pet();
}

/* Core signal processing – single logical "thought" ingestion
 * Input: short ASCII string
 * Effect: advances state_crc, propagates through all nodes, applies plasticity.
 */
static void _process_signal(const char *input) {
    if (!input) return;

    _watchdog_pet();

    /* 1. Atemporal task stacking: input influences the global CRC state */
    MATRIX.state_crc = _calc_crc32(input, strlen(input), MATRIX.state_crc);

    /* 2. Seed value derived from CRC (normalized to [0,1]) */
    float input_val = (float)(MATRIX.state_crc % 100) / 100.0f;

    /* 3. Clustered propagation over all nodes */
    for (int lane = 0; lane < LANES; lane++) {
        int start = lane * (MAX_NODES / LANES);
        int end   = start + (MAX_NODES / LANES);

        for (int i = start; i < end; i++) {
            Node *n = &MATRIX.nodes[i];

            if (!_verify_node_integrity(n)) {
                _audit("ERR: ECC fail at node %d. Resetting weights.", i);
                memset(&n->weights, 0, sizeof(Vec64));
                continue;
            }

            float signal = input_val *
                n->weights.values[MATRIX.state_crc % DIM_VEC];

            n->activation = _swiglu_fast(signal + n->bias);

            /* Simple plasticity rule: high activation → slight bias increase */
            if (n->activation > 0.8f) {
                n->bias += CFG.learning_rate;
            }
        }
    }

    MATRIX.cycle_count++;
    _audit("PROC: Input '%s' absorbed.", input);
}

/* ---------------------------------------------------------------------------
 * 5. PERSISTENCE (CONFIG + STATE)
 * --------------------------------------------------------------------------- */

/* Save configuration and matrix state to disk (binary blob) */
static void _save_state(void) {
    FILE *f = fopen(CONFIG_FILE, "wb");
    if (f) {
        (void)fwrite(&CFG, sizeof(RafConfig), 1, f);
        (void)fwrite(&MATRIX, sizeof(MatrixState), 1, f);
        fclose(f);
        _audit("IO: Config saved to %s.", CONFIG_FILE);
    } else {
        _audit("IO: Error saving config.");
    }
}

/* Load configuration and matrix state (if valid); otherwise re-genesis */
static void _load_state(void) {
    FILE *f = fopen(CONFIG_FILE, "rb");
    if (f) {
        RafConfig tmp_cfg;
        MatrixState tmp_matrix;

        if (fread(&tmp_cfg, sizeof(RafConfig), 1, f) == 1 &&
            tmp_cfg.magic == 0x52414641 &&
            fread(&tmp_matrix, sizeof(MatrixState), 1, f) == 1) {

            CFG    = tmp_cfg;
            MATRIX = tmp_matrix;
            _audit("IO: Config loaded from %s.", CONFIG_FILE);
        } else {
            _audit("IO: Invalid config. Re-Genesis.");
            _genesis();
        }
        fclose(f);
    } else {
        _audit("IO: No config file. Using Genesis defaults.");
        _genesis();
    }
}

/* ---------------------------------------------------------------------------
 * 6. BBS INTERFACE (ASCII UI)
 * --------------------------------------------------------------------------- */

static void _draw_logo(void) {
    printf(C_CYN);
    printf(" .--.      .--.    .---.    .---.  .-. .-.  .--.  \n");
    printf(": .--'    : .--'   : .; :   : .; : : : : : : .--' \n");
    printf(": : _     : : _    :  _.'   :   .' : : : : : : _  \n");
    printf(": :; :    : :; :   : :      : :.`. : :_: : : :; : \n");
    printf("`.__.'... `.__.'   :_;      :_;:_;  `.__.' `.__.' \n");
    printf(C_MAG "  MATRIX CORE %s " C_RST "\n", VERSION);
}

static void _render_bbs(void) {
    printf("\033[2J\033[H"); /* Clear screen */
    _draw_logo();

    printf("\n" C_BLD "SYSTEM STATUS:" C_RST "\n");
    printf(" > STATE HASH : " C_YEL "%08X" C_RST "\n", MATRIX.state_crc);
    printf(" > CYCLES     : %u\n", MATRIX.cycle_count);
    printf(" > NODES      : %d (Active)\n", MAX_NODES);
    printf(" > INTEGRITY  : " C_GRN "OK (ECC placeholder)" C_RST "\n");

    printf("\n" C_BLD "AUDIT LOG:" C_RST "\n");
    for (int i = 0; i < 8; i++) {
        int idx = (MATRIX.log_head + i) % 8;
        if (MATRIX.audit_log[idx][0] != '\0') {
            printf(" [%d] %s\n", i, MATRIX.audit_log[idx]);
        }
    }

    printf("\n" C_BLD "COMMANDS:" C_RST "\n");
    printf(" [" C_GRN "1" C_RST "] INJECT DATA (String)\n");
    printf(" [" C_GRN "2" C_RST "] SYNC/SAVE CONFIG\n");
    printf(" [" C_GRN "3" C_RST "] RUN DIAGNOSTIC (ECC Test)\n");
    printf(" [" C_GRN "4" C_RST "] SET LEARNING RATE\n");
    printf(" [" C_GRN "5" C_RST "] SHOW MATRIX REPORT\n");
    printf(" [" C_RED "0" C_RST "] EXIT MATRIX\n");
    printf("\nRAF> ");
}

/* Safe pause to flush stdin and avoid skipped prompts */
static void _safe_pause(const char *msg) {
    int c;
    if (msg) printf("%s", msg);
    while ((c = getchar()) != '\n' && c != EOF) { /* flush */ }
}

/* ---------------------------------------------------------------------------
 * 7. MATRIX REPORT (METRICS / AUDIT STYLE)
 * --------------------------------------------------------------------------- */

/* High-level state report: useful for ISO-style audits and quick checks */
static void _matrix_report(void) {
    printf("\n[--- RAFAELIA MATRIX AUDIT ---]\n");
    printf("Version        : %s\n", VERSION);
    printf("State CRC      : 0x%08X\n", MATRIX.state_crc);
    printf("Cycles         : %u\n", MATRIX.cycle_count);
    printf("Nodes          : %d\n", MAX_NODES);
    printf("Flags          : 0x%02X\n", MATRIX.flags);

    int plastic_nodes = 0;
    for (int i = 0; i < MAX_NODES; i++) {
        if (MATRIX.nodes[i].bias != 0.1f) plastic_nodes++;
    }
    printf("Plastic Nodes  : %d / %d\n", plastic_nodes, MAX_NODES);
    printf("Watchdog (ms)  : %u\n", CFG.watchdog_limit);
    printf("----------------------------------------\n");
}

/* ---------------------------------------------------------------------------
 * 8. BENCHMARK MODE (TOKENS/s, OPS/s, BYTES/s, IOPS, LATENCY)
 * --------------------------------------------------------------------------- */

/*
 * Benchmark goals:
 *  - Measure CPU throughput for _process_signal() (tokens/s and ops/s).
 *  - Measure basic disk throughput for write/read of CFG + MATRIX (bytes/s, IOPS).
 *  - Keep completely opt-in: only executed when '--bench' is passed.
 *  - Do not touch the persistent production state (backup and restore).
 */
static void _bench_matrix(void) {
    /* Backup current state (to avoid polluting live brain) */
    RafConfig   cfg_backup   = CFG;
    MatrixState matrix_backup = MATRIX;

    const char *bench_input = "RAFAELIA_MATRIX_BENCHMARK_SIGNAL";
    size_t      tokens_per_call = strlen(bench_input);

    const int   bench_iters = 10000; /* CPU benchmark iterations */
    clock_t     t0, t1;
    double      dt;

    /* --- CPU benchmark: _process_signal speed --- */
    _genesis(); /* fresh deterministic state */
    t0 = clock();
    for (int i = 0; i < bench_iters; i++) {
        _process_signal(bench_input);
    }
    t1 = clock();
    dt = (double)(t1 - t0) / (double)CLOCKS_PER_SEC;

    double total_tokens     = (double)bench_iters * (double)tokens_per_call;
    double ops_per_sec      = dt > 0.0 ? (double)bench_iters / dt : 0.0;
    double tokens_per_sec   = dt > 0.0 ? total_tokens / dt : 0.0;
    double avg_latency_ms   = bench_iters > 0 ? (dt * 1000.0) / (double)bench_iters : 0.0;

    /* --- I/O benchmark: write + read RAW state --- */
    size_t block_size = sizeof(RafConfig) + sizeof(MatrixState);
    const int io_iters = 256;
    size_t total_bytes = block_size * (size_t)io_iters;

    /* Write test */
    t0 = clock();
    FILE *fw = fopen(BENCH_FILE, "wb");
    if (fw) {
        for (int i = 0; i < io_iters; i++) {
            (void)fwrite(&CFG, sizeof(RafConfig), 1, fw);
            (void)fwrite(&MATRIX, sizeof(MatrixState), 1, fw);
        }
        fclose(fw);
    }
    t1 = clock();
    double dt_write = (double)(t1 - t0) / (double)CLOCKS_PER_SEC;

    /* Read test */
    t0 = clock();
    FILE *fr = fopen(BENCH_FILE, "rb");
    if (fr) {
        RafConfig   tmp_cfg;
        MatrixState tmp_matrix;
        for (int i = 0; i < io_iters; i++) {
            (void)fread(&tmp_cfg, sizeof(RafConfig), 1, fr);
            (void)fread(&tmp_matrix, sizeof(MatrixState), 1, fr);
        }
        fclose(fr);
    }
    t1 = clock();
    double dt_read = (double)(t1 - t0) / (double)CLOCKS_PER_SEC;

    /* Clean up temporary benchmark file (minimize footprint/wear) */
    (void)remove(BENCH_FILE);

    double write_bytes_sec = dt_write > 0.0 ? (double)total_bytes / dt_write : 0.0;
    double write_iops      = dt_write > 0.0 ? (double)io_iters / dt_write   : 0.0;
    double read_bytes_sec  = dt_read  > 0.0 ? (double)total_bytes / dt_read  : 0.0;
    double read_iops       = dt_read  > 0.0 ? (double)io_iters / dt_read    : 0.0;

    /* Restore previous state to avoid side-effects */
    CFG    = cfg_backup;
    MATRIX = matrix_backup;

    /* --- Print results (human-readable, no external tooling required) --- */
    printf("\n[--- RAFAELIA MATRIX BENCHMARK ---]\n");
    printf("CPU Benchmark:\n");
    printf("  Ops          : %d\n", bench_iters);
    printf("  Tokens/op    : %zu\n", tokens_per_call);
    printf("  Total tokens : %.0f\n", total_tokens);
    printf("  Elapsed      : %.6f s\n", dt);
    printf("  Ops/s        : %.2f\n", ops_per_sec);
    printf("  Tokens/s     : %.2f\n", tokens_per_sec);
    printf("  Avg latency  : %.4f ms/op\n", avg_latency_ms);

    printf("\nI/O Benchmark (CFG+MATRIX, %zu bytes per op):\n", block_size);
    printf("  IO ops       : %d\n", io_iters);
    printf("  Total bytes  : %zu\n", total_bytes);
    printf("  Write time   : %.6f s\n", dt_write);
    printf("  Write B/s    : %.2f\n", write_bytes_sec);
    printf("  Write IOPS   : %.2f\n", write_iops);
    printf("  Read time    : %.6f s\n", dt_read);
    printf("  Read B/s     : %.2f\n", read_bytes_sec);
    printf("  Read IOPS    : %.2f\n", read_iops);
    printf("----------------------------------------\n");
}

/* ---------------------------------------------------------------------------
 * 9. MAIN LOOP (CLI + BBS)
 * --------------------------------------------------------------------------- */

int main(int argc, char *argv[]) {
    _genesis();
    _load_state(); /* Try to load persistent state; fallback to Genesis */

    /* --- CLI modes ------------------------------------------------------- */

    /* 1) Benchmark mode (no user penalty unless explicitly requested) */
    if (argc > 1 && strcmp(argv[1], "--bench") == 0) {
        _bench_matrix();
        return 0;
    }

    /* 2) Inline command mode: ./raf_matrix "some input text ..." */
    if (argc > 1) {
        char buffer[1024] = "";
        for (int i = 1; i < argc; i++) {
            strncat(buffer, argv[i], sizeof(buffer) - strlen(buffer) - 1);
            strncat(buffer, " ", sizeof(buffer) - strlen(buffer) - 1);
        }
        _process_signal(buffer);
        _save_state();
        _matrix_report();
        return 0;
    }

    /* --- BBS interactive mode ------------------------------------------- */

    char cmd;
    char input_buf[128];

    do {
        _render_bbs();

        if (scanf(" %c", &cmd) != 1) cmd = '0';

        switch (cmd) {
            case '1':
                printf("\nDATA INPUT: ");
                if (scanf("%127s", input_buf) == 1) {
                    _process_signal(input_buf);
                }
                _safe_pause("\nPress Enter to continue...");
                break;
            case '2':
                _save_state();
                _safe_pause("Config saved. Press Enter to continue...");
                break;
            case '3': {
                _audit("DIAG: Checking %d nodes...", MAX_NODES);
                int errs = 0;
                for (int i = 0; i < MAX_NODES; i++) {
                    if (!_verify_node_integrity(&MATRIX.nodes[i])) errs++;
                }
                _audit("DIAG: Complete. %d errors.", errs);
                _safe_pause("Diagnostic done. Press Enter to continue...");
                break;
            }
            case '4':
                printf("Current learning rate: %.4f. New value: ", CFG.learning_rate);
                if (scanf("%f", &CFG.learning_rate) == 1) {
                    _audit("CFG: Learning rate updated.");
                }
                _safe_pause(NULL);
                break;
            case '5':
                _matrix_report();
                _safe_pause("Press Enter to continue...");
                break;
            case '0':
                _audit("SHUTDOWN: Matrix halted.");
                _save_state();
                break;
            default:
                _safe_pause(NULL);
                break;
        }

        /* Watchdog: simple stall detection (simulated) */
        if ((clock() - MATRIX.last_tick) > (CLOCKS_PER_SEC * 5)) {
            printf(C_RED "\n[WATCHDOG] SYSTEM STALL DETECTED. REBOOTING...\n" C_RST);
            _genesis();
        }

    } while (cmd != '0');

    return 0;
}
