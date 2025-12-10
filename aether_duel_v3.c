/*
 * PROJECT AETHER-X: DUEL EDITION v3.0
 * Compara STANDARD x HYPER usando o núcleo de aether_core.h
 * Modo interativo e modo automático:
 *
 *  - ./aether_duel           -> um duelo, buffer padrão
 *  - ./aether_duel auto MB N -> N duelos de MB MB, sem interação
 */

#define _POSIX_C_SOURCE 200809L

#include <stdio.h>
#include <stdlib.h>
#include <stdint.h>
#include <string.h>
#include <time.h>

#include "aether_core.h"   /* STANDARD + HYPER */

/* --- PLATFORM DETECTION --- */

#if defined(__ANDROID__)
    #define PLATFORM_NAME "ANDROID (TERMUX)"
    #define DUEL_BUF_DEFAULT (64 * 1024 * 1024ULL)
#elif defined(_WIN32) || defined(_WIN64)
    #define PLATFORM_NAME "WINDOWS"
    #define DUEL_BUF_DEFAULT (128 * 1024 * 1024ULL)
    #include <windows.h>
#else
    #define PLATFORM_NAME "LINUX/UNIX"
    #define DUEL_BUF_DEFAULT (256 * 1024 * 1024ULL)
#endif

/* --- ALLOC HELPERS --- */

static void* aether_alloc(size_t size) {
    void* ptr = NULL;
#if defined(_WIN32) || defined(_WIN64)
    ptr = _aligned_malloc(size, 64);
#else
    if (posix_memalign(&ptr, 64, size) != 0) {
        ptr = malloc(size);
    }
#endif
    return ptr;
}

static void aether_free(void* ptr) {
#if defined(_WIN32) || defined(_WIN64)
    _aligned_free(ptr);
#else
    free(ptr);
#endif
}

/* --- DUEL CORE --- */

static double aether_duel_once(size_t bytes, int quiet) {
    size_t mb = bytes / (1024 * 1024);
    if (!quiet) {
        printf("\n\033[1;33m>>> ARENA: STANDARD vs AETHER-X <<<\033[0m\n");
        printf("Plataforma: %s | Buffer: %zu MB\n", PLATFORM_NAME, mb);
        printf("Carregando %zu MB na RAM... ", mb);
        fflush(stdout);
    }

    uint8_t* buffer = (uint8_t*)aether_alloc(bytes);
    if (!buffer) {
        fprintf(stderr, "\n[ERRO] Falha na alocação de RAM.\n");
        return 0.0;
    }
    memset(buffer, 0xAB, bytes);
    if (!quiet) printf("Pronto.\n\n");

    /* ROUND 1: STANDARD */
    if (!quiet) {
        printf("1. [STANDARD] Processando byte-a-byte... ");
        fflush(stdout);
    }
    clock_t t1_start = clock();
    volatile uint64_t h1 = aether_standard_hash(buffer, bytes);
    clock_t t1_end = clock();
    double time_std = (double)(t1_end - t1_start) / CLOCKS_PER_SEC;
    if (!quiet) printf("Feito. (%.4fs)\n", time_std);

    /* ROUND 2: HYPER */
    if (!quiet) {
        printf("2. [AETHER-X] Ativando Hyper-Kernel...   ");
        fflush(stdout);
    }
    clock_t t2_start = clock();
    volatile uint64_t h2 = aether_hyper_hash(buffer, bytes);
    clock_t t2_end = clock();
    double time_aeth = (double)(t2_end - t2_start) / CLOCKS_PER_SEC;
    if (!quiet) printf("Feito. (%.4fs)\n", time_aeth);

    if (time_aeth <= 0.0) time_aeth = 1e-9; /* Proteção */

    double ratio        = time_std / time_aeth;
    double speed_std    = (double)mb / time_std;
    double speed_aether = (double)mb / time_aeth;

    if (!quiet) {
        printf("\n--- PLACAR FINAL ---\n");
        printf("STANDARD: %8.2f MB/s (hash=%016llx)\n",
               speed_std, (unsigned long long)h1);
        printf("AETHER-X: \033[1;32m%8.2f MB/s\033[0m (hash=%016llx)\n",
               speed_aether, (unsigned long long)h2);

        printf("\n>>> VANTAGEM: \033[1;31m%.1f x\033[0m MAIS RÁPIDO <<<\n", ratio);

        printf("\nVISUAL:\n");
        printf("STD : [=]\n");
        printf("AETH: [");
        int bars = (int)ratio;
        if (bars < 1) bars = 1;
        if (bars > 60) bars = 60;
        for (int i = 0; i < bars; i++) printf("=");
        printf("] (%.1f:1)\n", ratio);

        if (ratio >= 10.0) {
            printf("\n\033[1;36m🏆 CONQUISTA DESBLOQUEADA: MASSACRE (10+ x) 🏆\033[0m\n");
        } else {
            printf("\nVitória sólida, mas o compilador ajudou o Standard.\n");
        }
    }

    aether_free(buffer);
    return ratio;
}

/* --- MAIN --- */

static void usage(const char* prog) {
    printf("Uso:\n");
    printf("  %s               -> Um duelo com buffer padrão\n", prog);
    printf("  %s auto MB N     -> N duelos automáticos de MB MB (sem interação)\n", prog);
}

int main(int argc, char** argv) {
    if (argc > 1 && strcmp(argv[1], "auto") == 0) {
        /* MODO AUTOMÁTICO: ./aether_duel auto MB N */
        if (argc < 4) {
            usage(argv[0]);
            return 1;
        }
        size_t mb  = (size_t)strtoull(argv[2], NULL, 10);
        int    runs = atoi(argv[3]);
        if (mb == 0 || runs <= 0) {
            fprintf(stderr, "Parâmetros inválidos.\n");
            return 1;
        }

        size_t bytes = mb * 1024ULL * 1024ULL;
        printf("AUTO: %d duelos de %zu MB em %s\n",
               runs, mb, PLATFORM_NAME);

        double best = 0.0, worst = 1e9, sum = 0.0;
        for (int i = 0; i < runs; i++) {
            double r = aether_duel_once(bytes, /*quiet=*/1);
            if (r <= 0.0) continue;
            if (r > best) best = r;
            if (r < worst) worst = r;
            sum += r;
            printf("Run %d: ratio=%.2f x\n", i+1, r);
        }
        double avg = (runs > 0) ? (sum / runs) : 0.0;

        printf("\n=== RESUMO AUTO ===\n");
        printf("Plataforma : %s\n", PLATFORM_NAME);
        printf("Buffer     : %zu MB\n", mb);
        printf("Runs       : %d\n", runs);
        printf("Melhor     : %.2f x\n", best);
        printf("Pior       : %.2f x\n", worst);
        printf("Média      : %.2f x\n", avg);

        return 0;
    }

    /* MODO INTERATIVO SIMPLES */
    size_t bytes = DUEL_BUF_DEFAULT;
    aether_duel_once(bytes, /*quiet=*/0);
    printf("\n[ENTER] para sair.");
    int ch;
    while ((ch = getchar()) != '\n' && ch != EOF) { }
    return 0;
}
