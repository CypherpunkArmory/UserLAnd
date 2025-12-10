/*
 * ======================================================================================
 * PROJECT AETHER-X: PRO CONFIGURABLE EDITION (v2.1)
 * Context: Restauratio Gaia - Persistence, Poly-Width Hashing & Blake-like CLI
 *
 * FEATURES:
 * - Persistent Config: Load/Save defaults to 'aether.cfg' automatically.
 * - Poly-Width Output: 64, 128, 256, 512, 1024-bit Hash generation.
 * - Smart Memory: Aligned allocation with adaptive limits.
 * - IOPS/Bandwidth Metrics (RAM benchmark).
 * - Blake-like CLI:
 *     ./aether_pro              -> modo interativo (menu)
 *     ./aether_pro arquivo.bin  -> hash com largura configurada (ex: 1024 bits)
 *     ./aether_pro -w 256 file  -> força 256-bit
 *     cat file | ./aether_pro -w 512 -  -> lê de stdin
 *
 * IMPORTANTE: NÃO CRIPTOGRÁFICO. É um hash de integridade / benchmark,
 * inspirado em FNV+rotate, NÃO substitui BLAKE3/SHA3 para segurança real.
 * ======================================================================================
 */

#define _POSIX_C_SOURCE 200809L

#include <stdio.h>
#include <stdlib.h>
#include <stdint.h>
#include <string.h>
#include <time.h>
#include <ctype.h>

/* --- MACROS & PLATFORM DETECTION --- */

#if defined(_WIN32) || defined(_WIN64)
    #include <windows.h>
    #define SLEEP_MS(x) Sleep(x)
    #define PLATFORM "WIN32"
#else
    #include <unistd.h>
    #define SLEEP_MS(x) usleep((x) * 1000)
    #if defined(__ANDROID__)
        #define PLATFORM "ANDROID"
    #else
        #define PLATFORM "LINUX/UNIX"
    #endif
#endif

#define CONFIG_FILE "aether.cfg"
#define STREAM_CHUNK (4 * 1024 * 1024) /* 4MB chunk para modo arquivo */

/* --- CONFIG STRUCTURE --- */

typedef struct {
    int buffer_mb;      /* 128, 256, 512, 1024 */
    int hash_width;     /* 64, 128, 256, 512, 1024 bits */
    int show_art;       /* 1 = Yes, 0 = No */
} AetherConfig;

/* Defaults */
static AetherConfig g_config = { 256, 64, 1 };

/* --- PERSISTENCE LAYER (Save/Load) --- */

static void save_config(void) {
    FILE* f = fopen(CONFIG_FILE, "w");
    if (f) {
        fprintf(f, "BUFFER_MB=%d\n", g_config.buffer_mb);
        fprintf(f, "HASH_WIDTH=%d\n", g_config.hash_width);
        fprintf(f, "SHOW_ART=%d\n", g_config.show_art);
        fclose(f);
        printf("\n\033[1;32m[SUCCESS] Configuration saved to '%s'.\033[0m\n", CONFIG_FILE);
    } else {
        printf("\n\033[1;31m[ERROR] Could not write config file.\033[0m\n");
    }
    SLEEP_MS(1000);
}

static void load_config(void) {
    FILE* f = fopen(CONFIG_FILE, "r");
    if (!f) return;
    char line[128];
    while (fgets(line, sizeof(line), f)) {
        if (strncmp(line, "BUFFER_MB=", 10) == 0)
            g_config.buffer_mb = atoi(line + 10);
        else if (strncmp(line, "HASH_WIDTH=", 11) == 0)
            g_config.hash_width = atoi(line + 11);
        else if (strncmp(line, "SHOW_ART=", 9) == 0)
            g_config.show_art = atoi(line + 9);
    }
    fclose(f);
}

/* --- CORE: HYPER ENGINE (Streaming-capable) --- */

static inline uint64_t rotl64(uint64_t x, unsigned int r) {
    return (x << r) | (x >> (64U - r));
}

/* Atualiza um estado de hash com um chunk arbitrário (streaming-friendly) */
static void engine_core_update(uint64_t *hash, const uint8_t *data, size_t len) {
    const uint64_t prime = 0x100000001B3ULL;

    size_t n_blocks = len / 8;
    size_t i = 0;

    for (; i < n_blocks; i++) {
        uint64_t k;
        memcpy(&k, data + (i * 8), 8);
        *hash ^= k;
        *hash *= prime;
        *hash = rotl64(*hash, 31);
        *hash ^= (*hash >> 33);
    }

    /* Tail (bytes restantes) */
    size_t tail_idx = i * 8;
    while (tail_idx < len) {
        *hash ^= data[tail_idx];
        *hash *= prime;
        tail_idx++;
    }
}

/* One-shot helper (para benchmarks de RAM) */
static uint64_t engine_core_oneshot(const void* buffer, size_t len, uint64_t seed_offset) {
    const uint8_t* data = (const uint8_t*)buffer;
    uint64_t hash = 0xCBF29CE484222325ULL ^ seed_offset;
    engine_core_update(&hash, data, len);
    return hash;
}

/* --- POLY-WIDTH LAYER (128/256/512/1024 Generation) --- */

static void run_poly_hash_memory(void* buffer, size_t size, int width_bits) {
    int passes = width_bits / 64;
    if (passes < 1) passes = 1;
    if (passes > 16) passes = 16; /* 16 * 64 = 1024 */

    printf(" >> Generating %d-bit Digest (RAM)... ", width_bits);
    fflush(stdout);

    uint64_t results[16];
    clock_t start = clock();

    for (int i = 0; i < passes; i++) {
        uint64_t seed = (uint64_t)i * 0x9E3779B97F4A7C15ULL;
        results[i] = engine_core_oneshot(buffer, size, seed);
    }

    clock_t end = clock();
    double time_s = (double)(end - start) / CLOCKS_PER_SEC;
    double throughput = ((double)size * passes / (1024.0 * 1024.0)) / time_s;

    printf("\n    [ ");
    for (int i = 0; i < passes; i++) {
        printf("%016llx", (unsigned long long)results[i]);
    }
    printf(" ]\n");

    if (passes > 1) {
        printf("    (Multi-pass throughput: %.2f MB/s)\n", throughput);
    }
}

/* Poly-hash em streaming (arquivo ou stdin), tipo BLAKE CLI */
static int run_poly_hash_stream(FILE *fp, int width_bits, const char *label) {
    int passes = width_bits / 64;
    if (passes < 1) passes = 1;
    if (passes > 16) passes = 16;

    uint64_t state[16];
    uint8_t *buf = (uint8_t*)malloc(STREAM_CHUNK);
    if (!buf) {
        fprintf(stderr, "[ERROR] stream buffer alloc failed\n");
        return 1;
    }

    for (int i = 0; i < passes; i++) {
        uint64_t seed = (uint64_t)i * 0x9E3779B97F4A7C15ULL;
        state[i] = 0xCBF29CE484222325ULL ^ seed;
    }

    clock_t start = clock();
    size_t total_bytes = 0;

    for (;;) {
        size_t n = fread(buf, 1, STREAM_CHUNK, fp);
        if (n == 0) break;
        total_bytes += n;
        for (int i = 0; i < passes; i++) {
            engine_core_update(&state[i], buf, n);
        }
    }

    clock_t end = clock();
    free(buf);

    if (ferror(fp)) {
        fprintf(stderr, "[ERROR] read error in stream\n");
        return 1;
    }

    double time_s = (double)(end - start) / CLOCKS_PER_SEC;
    double mb = (double)total_bytes / (1024.0 * 1024.0);
    double mb_s = (time_s > 0.0) ? (mb / time_s) : 0.0;

    if (label)
        printf("%s: ", label);

    /* print digest em formato “blake-like”: hex contínuo */
    for (int i = 0; i < passes; i++) {
        printf("%016llx", (unsigned long long)state[i]);
    }
    printf("\n");

    fprintf(stderr, "[INFO] %d-bit, %.2f MB lidos, %.2f MB/s\n",
            width_bits, mb, mb_s);

    return 0;
}

/* --- MEMORY ALIGNMENT --- */

static void* k_alloc(size_t size) {
    void* ptr = NULL;
#if defined(_WIN32) || defined(_WIN64)
    ptr = _aligned_malloc(size, 64);
#else
    if (posix_memalign(&ptr, 64, size) != 0)
        ptr = malloc(size);
#endif
    return ptr;
}

static void k_free(void* ptr) {
#if defined(_WIN32) || defined(_WIN64)
    _aligned_free(ptr);
#else
    free(ptr);
#endif
}

/* --- UI HELPERS --- */

static void clear_screen(void) {
    printf("\033[2J\033[H");
}

static void wait_enter(void) {
    int ch;
    printf("\n[ENTER] to return.");
    fflush(stdout);
    /* consome até newline */
    while ((ch = getchar()) != '\n' && ch != EOF) { }
}

/* --- UI & MENUS (INTERACTIVE MODE) --- */

static void show_header(void) {
    clear_screen();
    if (g_config.show_art) {
        printf("\033[1;36m");
        printf("      .          .           . \n");
        printf("    /' \\        /`\\         /`\\ \n");
        printf("   /   |  ____  |   \\  ____ |   \\ \n");
        printf("  |    | |    | |    ||    ||    |\n");
        printf("   \\   | |____| |___/ |____||___/ \n");
        printf("    \\./              (AETHER-X)\n");
        printf("  [PRO CONFIGURABLE] [v2.1]\n");
        printf("\033[0m");
    } else {
        printf("[ AETHER-X PRO v2.1 ]\n");
    }
    printf(" ----------------------------------------\n");
    printf(" PLATFORM: %-10s | CFG: %s\n", PLATFORM, CONFIG_FILE);
    printf(" ----------------------------------------\n");
}

static void run_benchmark_suite(void) {
    size_t bytes = (size_t)g_config.buffer_mb * 1024U * 1024U;

#if defined(__ANDROID__)
    if (bytes > (size_t)1024 * 1024 * 1024) {
        printf("\n[WARN] Mobile limit: capping to 1GB.\n");
        bytes = (size_t)1024 * 1024 * 1024;
    }
#endif

    printf("\n[INIT] Allocating %d MB... ", g_config.buffer_mb);
    fflush(stdout);

    void* buf = k_alloc(bytes);
    if (!buf) {
        printf("\033[1;31mFAILED (OOM)\033[0m\n");
        SLEEP_MS(2000);
        return;
    }
    memset(buf, 0xFE, bytes);
    printf("OK.\n");

    printf("[TEST] Measuring Raw Engine Bandwidth...\n");
    clock_t start = clock();
    volatile uint64_t dummy = engine_core_oneshot(buf, bytes, 0);
    (void)dummy;
    clock_t end = clock();

    double time_s = (double)(end - start) / CLOCKS_PER_SEC;
    double mb = (double)bytes / (1024.0 * 1024.0);
    double speed_mb = mb / time_s;
    double iops = (bytes / 4096.0) / time_s;

    printf(" >> Raw Speed:  \033[1;32m%.2f MB/s\033[0m\n", speed_mb);
    printf(" >> Est. IOPS:  %.0f (4k blocks)\n", iops);

    run_poly_hash_memory(buf, bytes, g_config.hash_width);

    k_free(buf);
    wait_enter();
}

static void menu_settings(void) {
    int submenu = 1;
    while (submenu) {
        show_header();
        printf("\n [ SETTINGS EDITOR ]\n");
        printf(" 1. Buffer Size: [\033[1;33m%d MB\033[0m]\n", g_config.buffer_mb);
        printf(" 2. Hash Width:  [\033[1;33m%d bits\033[0m]\n", g_config.hash_width);
        printf(" 3. ASCII Art:   [%s]\n", g_config.show_art ? "ON" : "OFF");
        printf(" 4. Save as Default\n");
        printf(" 0. Back\n");
        printf("\n OPT > ");

        char inp[16];
        if (scanf("%15s", inp) != 1) continue;

        if (inp[0] == '1') {
            printf(" Enter MB (128, 256, 512, 1024): ");
            int val;
            if (scanf("%d", &val) == 1) {
                if (val > 0) g_config.buffer_mb = val;
            }
        } else if (inp[0] == '2') {
            printf(" Enter Width (64, 128, 256, 512, 1024): ");
            int val;
            if (scanf("%d", &val) == 1) {
                if (val >= 64 && val <= 1024) g_config.hash_width = val;
            }
        } else if (inp[0] == '3') {
            g_config.show_art = !g_config.show_art;
        } else if (inp[0] == '4') {
            save_config();
        } else if (inp[0] == '0') {
            submenu = 0;
        }
    }
}

/* --- CLI ESTILO BLAKE (NON-INTERACTIVE) --- */

static void print_cli_usage(const char *prog) {
    fprintf(stderr,
        "Uso:\n"
        "  %s                    -> modo interativo (menu)\n"
        "  %s arquivo.bin        -> hash com largura do config (ex: 1024-bit)\n"
        "  %s -w 256 arquivo.bin -> força largura 256-bit\n"
        "  cat file | %s -w 512 - -> lê da stdin\n",
        prog, prog, prog, prog);
}

int main(int argc, char **argv) {
    load_config();

    /* Modo CLI tipo blake: argumentos -> hash e sai */
    if (argc > 1) {
        int width = g_config.hash_width;
        const char *path = NULL;
        int argi = 1;

        if (strcmp(argv[argi], "-h") == 0 || strcmp(argv[argi], "--help") == 0) {
            print_cli_usage(argv[0]);
            return 0;
        }

        if (strcmp(argv[argi], "-w") == 0 && argi + 2 <= argc) {
            width = atoi(argv[argi + 1]);
            argi += 2;
        }

        if (argi < argc) {
            path = argv[argi];
        } else {
            print_cli_usage(argv[0]);
            return 1;
        }

        FILE *fp = NULL;
        if (strcmp(path, "-") == 0) {
            fp = stdin;
        } else {
            fp = fopen(path, "rb");
            if (!fp) {
                fprintf(stderr, "[ERROR] cannot open '%s'\n", path);
                return 1;
            }
        }

        int rc = run_poly_hash_stream(fp, width, (strcmp(path, "-") == 0) ? "stdin" : path);

        if (fp != stdin) fclose(fp);
        return rc;
    }

    /* Modo interativo (menu) */
    while (1) {
        show_header();
        printf("\n CONFIG: %d MB | %d-bit output\n", g_config.buffer_mb, g_config.hash_width);
        printf("\n 1. START BENCHMARK\n");
        printf(" 2. CONFIGURE (Size/Width)\n");
        printf(" 3. Info / Help\n");
        printf(" 0. Exit\n");
        printf("\n CMD > ");

        char input[16];
        if (scanf("%15s", input) != 1) continue;

        if (input[0] == '0') break;
        else if (input[0] == '1') run_benchmark_suite();
        else if (input[0] == '2') menu_settings();
        else if (input[0] == '3') {
            printf("\n INFO:\n");
            printf(" - Defaults saved to aether.cfg\n");
            printf(" - 1024-bit usa 16 lanes empilhadas (16x64-bit).\n");
            printf(" - Use 128MB/256MB em mobile, 512MB/1024MB em desktop.\n");
            printf(" - CLI tipo BLAKE disponível com -w e arquivo ou '-'.\n\n");
            wait_enter();
        }
    }
    return 0;
}
