/*
 * ======================================================================================
 * PROJECT AETHER-X: PRO CONFIGURABLE EDITION (v2.0)
 * Context: Restauratio Gaia - Persistence & Poly-Width Hashing
 * * FEATURES:
 * - Persistent Config: Load/Save defaults to 'aether.cfg' automatically.
 * - Poly-Width Output: 64, 128, 256, 512, 1024-bit Hash generation.
 * - Smart Memory: Aligned allocation with adaptive limits.
 * - IOPS/Bandwidth Metrics included.
 * ======================================================================================
 */

#define _POSIX_C_SOURCE 200809L

#include <stdio.h>
#include <stdlib.h>
#include <stdint.h>
#include <string.h>
#include <time.h>
#include <ctype.h>

// --- MACROS & PLATFORM ---
#if defined(_WIN32)
    #include <windows.h>
    #define SLEEP_MS(x) Sleep(x)
    #define PLATFORM "WIN32"
#else
    #include <unistd.h>
    #include <pthread.h>
    #define SLEEP_MS(x) usleep((x) * 1000)
    #if defined(__ANDROID__)
        #define PLATFORM "ANDROID"
    #else
        #define PLATFORM "LINUX/UNIX"
    #endif
#endif

#define CONFIG_FILE "aether.cfg"

// --- CONFIG STRUCTURE ---
typedef struct {
    int buffer_mb;      // 128, 256, 512, 1024
    int hash_width;     // 64, 128, 256, 512, 1024 bits
    int show_art;       // 1 = Yes, 0 = No
} AetherConfig;

// Default Settings
AetherConfig g_config = { 256, 64, 1 }; 

// --- 1. PERSISTENCE LAYER (Save/Load) ---

void save_config() {
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

void load_config() {
    FILE* f = fopen(CONFIG_FILE, "r");
    if (f) {
        char line[128];
        while (fgets(line, sizeof(line), f)) {
            if (strstr(line, "BUFFER_MB=")) g_config.buffer_mb = atoi(line + 10);
            if (strstr(line, "HASH_WIDTH=")) g_config.hash_width = atoi(line + 11);
            if (strstr(line, "SHOW_ART=")) g_config.show_art = atoi(line + 9);
        }
        fclose(f);
    }
}

// --- 2. CORE: HYPER ENGINE (Single Lane) ---

static inline uint64_t rotl64(uint64_t x, int8_t r) {
    return (x << r) | (x >> (64 - r));
}

// The native 64-bit engine (Fastest possible throughput)
uint64_t engine_core(const void* buffer, size_t len, uint64_t seed_offset) {
    const uint8_t* data = (const uint8_t*)buffer;
    uint64_t hash = 0xCBF29CE484222325ULL ^ seed_offset; // Seeding allows stacking
    uint64_t prime = 0x100000001B3ULL;
    size_t n_blocks = len / 8;
    size_t i = 0;

    for (; i < n_blocks; i++) {
        uint64_t k;
        memcpy(&k, data + (i*8), 8);
        hash ^= k;
        hash *= prime;
        hash = rotl64(hash, 31);
        hash ^= (hash >> 33);
    }
    // Tail omitted for benchmark purity, added in production integrity
    return hash;
}

// --- 3. POLY-WIDTH LAYER (128/256/512/1024 Generation) ---
// Generates wider hashes by running the engine with orthogonal seeds.
void run_poly_hash(void* buffer, size_t size, int width_bits) {
    int passes = width_bits / 64;
    if (passes < 1) passes = 1;

    printf(" >> Generating %d-bit Digest... ", width_bits);
    fflush(stdout);

    uint64_t results[16]; // Max 1024 bits (16 * 64)
    
    clock_t start = clock();
    
    // Multi-Lane Execution
    for (int i = 0; i < passes; i++) {
        // Different seed for each pass to ensure bit independence
        // 0x9E37... is Golden Ratio constant
        uint64_t lane_seed = i * 0x9E3779B97F4A7C15ULL; 
        results[i] = engine_core(buffer, size, lane_seed);
    }

    clock_t end = clock();
    double time_s = (double)(end - start) / CLOCKS_PER_SEC;
    double throughput = ((double)size * passes / (1024*1024)) / time_s;

    // Display Hex String
    printf("\n    [ ");
    for (int i = 0; i < passes; i++) {
        printf("%016llx", (unsigned long long)results[i]);
    }
    printf(" ]\n");
    
    if (passes > 1) {
        printf("    (Multi-pass throughput: %.2f MB/s)\n", throughput);
    }
}

// --- 4. MEMORY ALIGNMENT ---
void* k_alloc(size_t size) {
    void* ptr = NULL;
#if defined(_WIN32)
    ptr = _aligned_malloc(size, 64);
#else
    if (posix_memalign(&ptr, 64, size) != 0) ptr = malloc(size);
#endif
    return ptr;
}

void k_free(void* ptr) {
#if defined(_WIN32)
    _aligned_free(ptr);
#else
    free(ptr);
#endif
}

// --- 5. UI & MENUS ---

void clear() { printf("\033[2J\033[H"); }

void show_header() {
    clear();
    if (g_config.show_art) {
        printf("\033[1;36m");
        printf("      .          .           . \n");
        printf("    /' \\        /`\\         /`\\ \n");
        printf("   /   |  ____  |   \\  ____ |   \\ \n");
        printf("  |    | |    | |    ||    ||    |\n");
        printf("   \\   | |____| |___/ |____||___/ \n");
        printf("    \\./              (AETHER-X)\n");
        printf("  [PRO CONFIGURABLE] [v2.0]\n");
        printf("\033[0m");
    } else {
        printf("[ AETHER-X PRO ]\n");
    }
    printf(" ----------------------------------------\n");
    printf(" PLATFORM: %-10s | CFG: %s\n", PLATFORM, CONFIG_FILE);
    printf(" ----------------------------------------\n");
}

void run_benchmark_suite() {
    size_t bytes = (size_t)g_config.buffer_mb * 1024 * 1024;
    
    // Safety limit for Android
    #if defined(__ANDROID__)
    if (bytes > 1024 * 1024 * 1024) {
        printf("\n[WARN] Mobile limit: Capping to 1GB.\n");
        bytes = 1024 * 1024 * 1024;
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
    memset(buf, 0xFE, bytes); // Warmup
    printf("OK.\n");

    // 1. Raw Speed Test (Single Lane 64-bit)
    printf("[TEST] Measuring Raw Engine Bandwidth...\n");
    clock_t start = clock();
    volatile uint64_t dummy = engine_core(buf, bytes, 0);
    clock_t end = clock();
    
    double time_s = (double)(end - start) / CLOCKS_PER_SEC;
    double speed_mb = ((double)bytes / (1024*1024)) / time_s;
    double iops = (bytes / 4096.0) / time_s;

    printf(" >> Raw Speed:  \033[1;32m%.2f MB/s\033[0m\n", speed_mb);
    printf(" >> Est. IOPS:  %.0f (4k blocks)\n", iops);

    // 2. Poly-Width Generation
    run_poly_hash(buf, bytes, g_config.hash_width);

    k_free(buf);
    printf("\n[ENTER] to return.");
    getchar(); getchar();
}

void menu_settings() {
    int submenu = 1;
    while(submenu) {
        show_header();
        printf("\n [ SETTINGS EDITOR ]\n");
        printf(" 1. Buffer Size: [\033[1;33m%d MB\033[0m]\n", g_config.buffer_mb);
        printf(" 2. Hash Width:  [\033[1;33m%d bits\033[0m]\n", g_config.hash_width);
        printf(" 3. ASCII Art:   [%s]\n", g_config.show_art ? "ON" : "OFF");
        printf(" 4. Save as Default\n");
        printf(" 0. Back\n");
        printf("\n OPT > ");
        
        char inp[10];
        if (scanf("%s", inp) != 1) continue;

        if (inp[0] == '1') {
            printf(" Enter MB (128, 256, 512, 1024): ");
            int val;
            if (scanf("%d", &val) == 1) {
                if(val > 0) g_config.buffer_mb = val;
            }
        }
        else if (inp[0] == '2') {
            printf(" Enter Width (64, 128, 256, 512, 1024): ");
            int val;
            if (scanf("%d", &val) == 1) {
                if(val >= 64 && val <= 1024) g_config.hash_width = val;
            }
        }
        else if (inp[0] == '3') g_config.show_art = !g_config.show_art;
        else if (inp[0] == '4') save_config();
        else if (inp[0] == '0') submenu = 0;
    }
}

int main() {
    load_config(); // Auto-load on startup

    while(1) {
        show_header();
        printf("\n CONFIG: %d MB | %d-bit output\n", g_config.buffer_mb, g_config.hash_width);
        printf("\n 1. START BENCHMARK\n");
        printf(" 2. CONFIGURE (Size/Width)\n");
        printf(" 3. Info / Help\n");
        printf(" 0. Exit\n");
        printf("\n CMD > ");

        char input[10];
        if (scanf("%s", input) != 1) continue;

        if (input[0] == '0') break;
        if (input[0] == '1') run_benchmark_suite();
        if (input[0] == '2') menu_settings();
        if (input[0] == '3') {
            printf("\n INFO:\n - Default saved to aether.cfg\n - 1024-bit uses 16-lane stacking.\n - Use 128MB/256MB on Mobile.\n\n[ENTER]");
            getchar(); getchar();
        }
    }
    return 0;
}
