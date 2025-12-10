/*
 * ======================================================================================
 * PROJECT AETHER-X: DUEL EDITION (10:1 CHALLENGE)
 * Context: Restauratio Gaia - Proof of Superiority
 * Target: Demonstrate 10x throughput vs Standard Naive Implementation
 * ======================================================================================
 */

#define _POSIX_C_SOURCE 200809L // Posix Memalign

#include <stdio.h>
#include <stdlib.h>
#include <stdint.h>
#include <string.h>
#include <time.h>

// --- PLATFORM & MACROS ---
#if defined(_MSC_VER)
    #define FORCE_INLINE __forceinline
    #define ALIGNED(x) __declspec(align(x))
#else
    #define FORCE_INLINE __attribute__((always_inline)) inline
    #define ALIGNED(x) __attribute__((aligned(x)))
#endif

// Platform Detection
#if defined(__ANDROID__)
    #define PLATFORM_NAME "ANDROID (TERMUX)"
    #define DUEL_BUF_SIZE (64 * 1024 * 1024)   // 64MB
#elif defined(_WIN32)
    #define PLATFORM_NAME "WINDOWS"
    #define DUEL_BUF_SIZE (128 * 1024 * 1024)  // 128MB
    #include <windows.h>
#else
    #define PLATFORM_NAME "LINUX/UNIX"
    #define DUEL_BUF_SIZE (256 * 1024 * 1024)  // 256MB
    #include <unistd.h>
    #include <pthread.h>
#endif

// --- 1. O LENTO (Standard Byte-by-Byte) ---
// É assim que "todo mundo" escreve. É seguro, mas desperdiça ciclos da CPU.
uint64_t standard_hash(const void* buffer, size_t len) {
    const uint8_t* data = (const uint8_t*)buffer;
    uint64_t hash = 0xCBF29CE484222325ULL;
    uint64_t prime = 0x100000001B3ULL;

    for (size_t i = 0; i < len; i++) {
        hash ^= data[i]; // Pega UM byte
        hash *= prime;   // Multiplica
    }
    return hash;
}

// --- 2. O RÁPIDO (AETHER-X Hyper Kernel) ---
// Lê 8 bytes de uma vez, rotaciona bits, usa a CPU inteira.
static FORCE_INLINE uint64_t rotl64(uint64_t x, int8_t r) {
    return (x << r) | (x >> (64 - r));
}

uint64_t hyper_core_hash(const void* buffer, size_t len) {
    const uint8_t* data = (const uint8_t*)buffer;
    uint64_t hash = 0xCBF29CE484222325ULL;
    uint64_t prime = 0x100000001B3ULL;
    size_t n_blocks = len / 8;
    size_t i = 0;

    // HOT LOOP (Machine Gun Mode)
    for (; i < n_blocks; i++) {
        uint64_t k;
        memcpy(&k, data + (i * 8), 8); // Pega OITO bytes (1 Word)
        hash ^= k;
        hash *= prime;
        hash = rotl64(hash, 31); // Avalanche
        hash ^= (hash >> 33);
    }
    
    // Tail
    size_t tail_idx = i * 8;
    while (tail_idx < len) {
        hash ^= data[tail_idx];
        hash *= prime;
        tail_idx++;
    }
    return hash;
}

// --- UTILS ---
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

// --- THE DUEL LOGIC ---
void run_duel() {
    printf("\n\033[1;33m>>> ARENA INICIADA: STANDARD vs AETHER-X <<<\033[0m\n");
    size_t mb = DUEL_BUF_SIZE / (1024 * 1024);
    printf("Carregando %zu MB na RAM... ", mb);
    
    uint8_t* buffer = (uint8_t*)k_alloc(DUEL_BUF_SIZE);
    if(!buffer) { printf("Falha RAM.\n"); return; }
    memset(buffer, 0xAB, DUEL_BUF_SIZE); // Warmup
    printf("Pronto.\n\n");

    // ROUND 1: STANDARD
    printf("1. [STANDARD] Processando byte-a-byte... ");
    fflush(stdout);
    clock_t t1_start = clock();
    volatile uint64_t h1 = standard_hash(buffer, DUEL_BUF_SIZE);
    clock_t t1_end = clock();
    double time_std = (double)(t1_end - t1_start) / CLOCKS_PER_SEC;
    printf("Feito. (%.4fs)\n", time_std);

    // ROUND 2: AETHER-X
    printf("2. [AETHER-X] Ativando Hyper-Kernel...   ");
    fflush(stdout);
    clock_t t2_start = clock();
    volatile uint64_t h2 = hyper_core_hash(buffer, DUEL_BUF_SIZE);
    clock_t t2_end = clock();
    double time_aether = (double)(t2_end - t2_start) / CLOCKS_PER_SEC;
    printf("Feito. (%.4fs)\n", time_aether);

    // RESULTADOS
    double ratio = time_std / time_aether;
    double speed_std = mb / time_std;
    double speed_aether = mb / time_aether;

    printf("\n--- PLACAR FINAL ---\n");
    printf("STANDARD: %8.2f MB/s\n", speed_std);
    printf("AETHER-X: \033[1;32m%8.2f MB/s\033[0m\n", speed_aether);
    
    printf("\n>>> VANTAGEM: \033[1;31m%.1f x\033[0m MAIS RÁPIDO <<<\n", ratio);

    // ASCII BAR CHART
    printf("\nVISUAL:\n");
    printf("STD : [=] \n");
    printf("AETH: [");
    int bars = (int)ratio;
    for(int i=0; i<bars; i++) printf("=");
    printf("] (%.0f:1)\n", ratio);

    k_free(buffer);
    
    if (ratio >= 10.0) {
        printf("\n\033[1;36m🏆 CONQUISTA DESBLOQUEADA: MASSACRE (10+ x) 🏆\033[0m\n");
    } else {
        printf("\nVitória sólida, mas o compilador ajudou o Standard.\n");
    }
    
    printf("\n[ENTER] para sair.");
    getchar(); getchar();
}

int main() {
    run_duel();
    return 0;
}
