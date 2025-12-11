/*
 * ======================================================================================
 * PROJECT AETHER-X: DUEL EDITION v2.0
 * Context: Restauratio Gaia - Proof of Superiority (10:1 Challenge)
 * Target: Benchmark Engine (Interactive + Scriptable + Loggable)
 * ======================================================================================
 */

#define _POSIX_C_SOURCE 200809L

#include <stdio.h>
#include <stdlib.h>
#include <stdint.h>
#include <string.h>
#include <time.h>
#include <inttypes.h>

/* ---------------- PLATFORM & MACROS ---------------- */

#if defined(_MSC_VER)
    #define FORCE_INLINE __forceinline
    #define ALIGNED(x) __declspec(align(x))
#else
    #define FORCE_INLINE __attribute__((always_inline)) inline
    #define ALIGNED(x) __attribute__((aligned(x)))
#endif

#if defined(__ANDROID__)
    #define PLATFORM_NAME "ANDROID (TERMUX)"
    #define DUEL_BUF_SIZE (64 * 1024 * 1024)   /* 64MB */
#elif defined(_WIN32) || defined(_WIN64)
    #define PLATFORM_NAME "WINDOWS"
    #define DUEL_BUF_SIZE (128 * 1024 * 1024)  /* 128MB */
    #include <windows.h>
#else
    #define PLATFORM_NAME "LINUX/UNIX"
    #define DUEL_BUF_SIZE (256 * 1024 * 1024)  /* 256MB */
    #include <unistd.h>
#endif

/* Garantia de semântica (múltiplo de 8) */
#if (DUEL_BUF_SIZE % 8) != 0
    #error "DUEL_BUF_SIZE must be multiple of 8 bytes."
#endif

/* ---------------- HASH ENGINES ---------------- */

/* 1. Padrão (byte-a-byte) */
uint64_t standard_hash(const void* buffer, size_t len) {
    const uint8_t* data = (const uint8_t*) buffer;
    uint64_t hash  = 0xCBF29CE484222325ULL;
    uint64_t prime = 0x100000001B3ULL;

    for (size_t i = 0; i < len; i++) {
        hash ^= data[i];
        hash *= prime;
    }
    return hash;
}

/* 2. AETHER-X (8 bytes + rotação) */

static FORCE_INLINE uint64_t rotl64(uint64_t x, int8_t r) {
    return (uint64_t)((x << r) | (x >> (64 - r)));
}

uint64_t hyper_core_hash(const void* buffer, size_t len) {
    const uint8_t* data = (const uint8_t*) buffer;
    uint64_t hash  = 0xCBF29CE484222325ULL;
    uint64_t prime = 0x100000001B3ULL;

    size_t n_blocks = len / 8;
    size_t i = 0;

    for (; i < n_blocks; i++) {
        uint64_t k;
        memcpy(&k, data + (i * 8), 8); /* Seguro p/ desalinhado, otimizado pelo compilador */

        hash ^= k;
        hash *= prime;
        hash = rotl64(hash, 31);
        hash ^= (hash >> 33);
    }

    /* Tail */
    size_t tail_idx = i * 8;
    while (tail_idx < len) {
        hash ^= data[tail_idx];
        hash *= prime;
        tail_idx++;
    }

    return hash;
}

/* ---------------- MEM & UTIL ---------------- */

void* k_alloc(size_t size) {
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

void k_free(void* ptr) {
#if defined(_WIN32) || defined(_WIN64)
    _aligned_free(ptr);
#else
    free(ptr);
#endif
}

/* Esperar ENTER de forma robusta */
static void wait_enter(void) {
    int ch;
    /* consome até o próximo '\n' */
    while ((ch = getchar()) != '\n' && ch != EOF) { }
}

/* ---------------- RESULT STRUCT & LOG ---------------- */

typedef struct {
    size_t   bytes;
    double   time_std;
    double   time_aether;
    double   ratio;
    uint64_t hash_std;
    uint64_t hash_aether;
} DuelResult;

static const char* LOG_FILE = "aether_duel_log.csv";

void log_result_csv(const DuelResult* r) {
    FILE* f = fopen(LOG_FILE, "a");
    if (!f) return;

    time_t now = time(NULL);
    struct tm* tm_info = localtime(&now);
    char ts[32];
    if (tm_info) {
        strftime(ts, sizeof(ts), "%Y-%m-%d %H:%M:%S", tm_info);
    } else {
        snprintf(ts, sizeof(ts), "unknown");
    }

    double mb = (double) r->bytes / (1024.0 * 1024.0);
    fprintf(f,
            "%s,%s,%.2f,%.6f,%.6f,%.3f,%" PRIx64 ",%" PRIx64 "\n",
            ts,
            PLATFORM_NAME,
            mb,
            r->time_std,
            r->time_aether,
            r->ratio,
            r->hash_std,
            r->hash_aether);

    fclose(f);
}

/* ---------------- CORE DUEL FUNCTION ---------------- */

int run_duel_once(size_t bytes, DuelResult* out) {
    if (bytes == 0) return -1;

    printf("\n\033[1;33m>>> ARENA: STANDARD vs AETHER-X <<<\033[0m\n");
    double mb = (double) bytes / (1024.0 * 1024.0);
    printf("Carregando %.2f MB na RAM... ", mb);

    uint8_t* buffer = (uint8_t*) k_alloc(bytes);
    if (!buffer) {
        printf("\033[1;31mFalha RAM.\033[0m\n");
        return -1;
    }
    memset(buffer, 0xAB, bytes);
    printf("Pronto.\n\n");

    /* ROUND 1: STANDARD */
    printf("1. [STANDARD] Processando byte-a-byte... ");
    fflush(stdout);
    clock_t t1_start = clock();
    volatile uint64_t h1 = standard_hash(buffer, bytes);
    clock_t t1_end   = clock();
    double time_std  = (double)(t1_end - t1_start) / CLOCKS_PER_SEC;
    printf("Feito. (%.4fs)\n", time_std);

    /* ROUND 2: AETHER-X */
    printf("2. [AETHER-X] Ativando Hyper-Kernel...   ");
    fflush(stdout);
    clock_t t2_start = clock();
    volatile uint64_t h2 = hyper_core_hash(buffer, bytes);
    clock_t t2_end   = clock();
    double time_aeth = (double)(t2_end - t2_start) / CLOCKS_PER_SEC;
    printf("Feito. (%.4fs)\n", time_aeth);

    double ratio       = time_std / time_aeth;
    double speed_std   = mb / time_std;
    double speed_aeth  = mb / time_aeth;

    printf("\n--- PLACAR FINAL ---\n");
    printf("STANDARD: %8.2f MB/s\n", speed_std);
    printf("AETHER-X: \033[1;32m%8.2f MB/s\033[0m\n", speed_aeth);

    printf("\n>>> VANTAGEM: \033[1;31m%.1f x\033[0m MAIS RÁPIDO <<<\n", ratio);

    printf("\nVISUAL:\n");
    printf("STD : [=]\n");
    printf("AETH: [");
    int bars = (int) (ratio < 1.0 ? 1 : ratio);
    for (int i = 0; i < bars; i++) printf("=");
    printf("] (%.1f:1)\n", ratio);

    if (out) {
        out->bytes       = bytes;
        out->time_std    = time_std;
        out->time_aether = time_aeth;
        out->ratio       = ratio;
        out->hash_std    = (uint64_t) h1;
        out->hash_aether = (uint64_t) h2;
    }

    k_free(buffer);

    if (ratio >= 10.0) {
        printf("\n\033[1;36m🏆 CONQUISTA: MASSACRE (10+ x) 🏆\033[0m\n");
    } else if (ratio >= 3.0) {
        printf("\n\033[1;33mVitória clara, mas ainda há espaço para tuning.\033[0m\n");
    } else {
        printf("\n\033[1;31mCompilador ajudou o STANDARD. Ajuste flags ou tamanho.\033[0m\n");
    }

    return 0;
}

/* ---------------- INFO & MENU ---------------- */

void show_info(void) {
    printf("\n=== INFO AETHER-X DUEL ENGINE ===\n");
    printf(" Plataforma : %s\n", PLATFORM_NAME);
    printf(" Buffer Padrão: %zu MB\n", (size_t)(DUEL_BUF_SIZE / (1024 * 1024)));
    printf(" Log CSV   : %s\n", LOG_FILE);
    printf(" Núcleos   : Standard (byte) + Hyper (word + rotate)\n");
    printf(" Uso       : Comparar implementações, tuning de flags (-O3, -march=native).\n");
}

void interactive_menu(void) {
    int running = 1;
    char cmd[16];

    while (running) {
        printf("\n=========================================\n");
        printf(" AETHER-X DUEL v2.0  [%s]\n", PLATFORM_NAME);
        printf("=========================================\n");
        printf(" 1. Duelo Rápido (Buffer padrão)\n");
        printf(" 2. Duelo Custom (MB)\n");
        printf(" 3. Série de N Duelos (loop)\n");
        printf(" 4. Informações do Sistema\n");
        printf(" 5. Mostrar Nome do Arquivo de Log\n");
        printf(" 0. Sair\n");
        printf("-----------------------------------------\n");
        printf(" CMD > ");

        if (scanf("%15s", cmd) != 1) {
            return;
        }
        wait_enter(); /* limpa resto da linha */

        if (cmd[0] == '0') {
            running = 0;
        } else if (cmd[0] == '1') {
            DuelResult r;
            if (run_duel_once(DUEL_BUF_SIZE, &r) == 0) {
                log_result_csv(&r);
            }
            printf("\n[ENTER] para continuar...");
            wait_enter();
        } else if (cmd[0] == '2') {
            printf("\nTamanho em MB: ");
            size_t mb = 0;
            if (scanf("%zu", &mb) == 1 && mb > 0) {
                wait_enter();
                size_t bytes = mb * 1024 * 1024;
                DuelResult r;
                if (run_duel_once(bytes, &r) == 0) {
                    log_result_csv(&r);
                }
            } else {
                wait_enter();
                printf("Entrada inválida.\n");
            }
            printf("\n[ENTER] para continuar...");
            wait_enter();
        } else if (cmd[0] == '3') {
            printf("\nNúmero de duelos (N): ");
            int n = 0;
            if (scanf("%d", &n) == 1 && n > 0) {
                wait_enter();
                for (int i = 0; i < n; i++) {
                    printf("\n--- DUEL %d/%d ---\n", i + 1, n);
                    DuelResult r;
                    if (run_duel_once(DUEL_BUF_SIZE, &r) == 0) {
                        log_result_csv(&r);
                    } else {
                        break;
                    }
                }
            } else {
                wait_enter();
                printf("Entrada inválida.\n");
            }
            printf("\n[ENTER] para continuar...");
            wait_enter();
        } else if (cmd[0] == '4') {
            show_info();
            printf("\n[ENTER] para continuar...");
            wait_enter();
        } else if (cmd[0] == '5') {
            printf("\nArquivo de log atual: %s\n", LOG_FILE);
            printf("Formato: timestamp,plataforma,MB,t_std,t_aether,ratio,hash_std,hash_aether\n");
            printf("\n[ENTER] para continuar...");
            wait_enter();
        } else {
            printf("Comando inválido.\n");
        }
    }
}

/* ---------------- MAIN (INTERACTIVE + AUTO) ---------------- */

int main(int argc, char** argv) {
    /* Modo automático: aether_duel auto <MB> <N> */
    if (argc >= 2 && strcmp(argv[1], "auto") == 0) {
        size_t mb = (argc >= 3) ? (size_t) strtoull(argv[2], NULL, 10)
                                : (DUEL_BUF_SIZE / (1024 * 1024));
        int runs = (argc >= 4) ? atoi(argv[3]) : 1;
        if (mb == 0) mb = DUEL_BUF_SIZE / (1024 * 1024);
        if (runs < 1) runs = 1;

        size_t bytes = mb * 1024 * 1024;
        for (int i = 0; i < runs; i++) {
            DuelResult r;
            if (run_duel_once(bytes, &r) == 0) {
                log_result_csv(&r);
            } else {
                break;
            }
        }
        return 0;
    }

    /* Caso contrário, modo interativo */
    interactive_menu();
    printf("\n[SHUTDOWN] AETHER-X Duel Engine finalizado.\n");
    return 0;
}
