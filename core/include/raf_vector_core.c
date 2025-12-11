/*
 * ======================================================================================
 * 🧬 RAFAELIA VECTOR CORE (VSA ENGINE / C-NATIVE)
 * ======================================================================================
 * ORIGEM: ia_vetor.py -> Refatorado para Bare Metal C11
 * LÓGICA: Hyperdimensional Computing (HDC) + Associative Memory
 * MATH: Cosine Sim | Orthogonal Gen | Bundling | Normalization
 * ======================================================================================
 */

#include <stdio.h>
#include <stdlib.h>
#include <stdint.h>
#include <string.h>
#include <math.h>
#include <time.h>

// --- 1. HIPER-DIMENSÕES ---
#define DIM 1024            // Compatível com Trinity Core
#define MEM_CAPACITY 256    // Capacidade da Memória Associativa
#define TOLERANCE 0.0001f

// Cores
#define C_RST "\x1b[0m"
#define C_CYN "\x1b[36m"
#define C_GRN "\x1b[32m"
#define C_YEL "\x1b[33m"

// --- 2. ESTRUTURA MATRIX (Memória Unificada) ---

typedef struct {
    float v[DIM];
} Vec;

typedef struct {
    uint32_t id_hash;       // Assinatura do conceito (ex: hash("amor"))
    char label[32];         // Rótulo legível
    Vec data;               // Hipervetor
    uint8_t active;
} Engram;

typedef struct {
    Engram memory[MEM_CAPACITY];
    Vec workspace;          // Registrador de acumulação (Bundling)
    uint32_t head;
    uint32_t ops_count;
} VectorMatrix;

static VectorMatrix MATRIX;

// --- 3. MATH KERNEL (ATEMPORAL) ---

// Hashing Semântico (FNV-1a)
static inline uint32_t _hash(const char *str) {
    uint32_t hash = 2166136261u;
    while (*str) {
        hash ^= (uint8_t)*str++;
        hash *= 16777619u;
    }
    return hash;
}

// PRNG Determinístico (Xorshift)
static inline uint32_t _rand_det(uint32_t *state) {
    uint32_t x = *state;
    x ^= x << 13; x ^= x >> 17; x ^= x << 5;
    *state = x;
    return x;
}

// Geração Ortogonal (Seed -> Vetor Bipolar -1/+1)
void _gen_ortho(uint32_t seed, Vec *out) {
    uint32_t r = seed ? seed : 0x52414641u;
    for(int i = 0; i < DIM; i++) {
        out->v[i] = (_rand_det(&r) & 1u) ? 1.0f : -1.0f;
    }
}

// Similaridade de Cosseno (Linear Loop)
float _sim(const Vec *a, const Vec *b) {
    float dot = 0.0f, mA = 0.0f, mB = 0.0f;
    for(int i = 0; i < DIM; i++) {
        float av = a->v[i];
        float bv = b->v[i];
        dot += av * bv;
        mA  += av * av;
        mB  += bv * bv;
    }
    if(mA < TOLERANCE || mB < TOLERANCE) return 0.0f;
    return dot / (sqrtf(mA) * sqrtf(mB));
}

// Empacotamento (Bundling): acc = normalize(acc + input)
void _bundle(Vec *acc, const Vec *input) {
    float mag = 0.0f;
    for(int i = 0; i < DIM; i++) {
        acc->v[i] += input->v[i];
        mag += acc->v[i] * acc->v[i];
    }
    if(mag > TOLERANCE) {
        float inv_mag = 1.0f / sqrtf(mag);
        for(int i = 0; i < DIM; i++) acc->v[i] *= inv_mag;
    }
}

// --- 4. OPERAÇÕES DE MEMÓRIA ---

void _genesis() {
    memset(&MATRIX, 0, sizeof(VectorMatrix));
}

// Aprender: Gera vetor do texto e salva na memória (se não existir)
void _learn(const char *text) {
    uint32_t h = _hash(text);

    for(int i = 0; i < MEM_CAPACITY; i++) {
        if(MATRIX.memory[i].active && MATRIX.memory[i].id_hash == h) {
            // Já existe, não duplica
            return;
        }
    }

    int idx = MATRIX.head;
    MATRIX.memory[idx].id_hash = h;
    strncpy(MATRIX.memory[idx].label, text, 31);
    MATRIX.memory[idx].label[31] = '\0';
    _gen_ortho(h, &MATRIX.memory[idx].data);
    MATRIX.memory[idx].active = 1;

    MATRIX.head = (MATRIX.head + 1u) % MEM_CAPACITY;
    MATRIX.ops_count++;
}

// Consultar: Compara vetor de entrada com toda a memória
void _query(const char *text) {
    Vec q;
    uint32_t h = _hash(text);
    _gen_ortho(h, &q);

    printf("\n" C_CYN "QUERY: '%s' [%08X]" C_RST "\n", text, h);
    printf("--------------------------------------\n");

    for(int i = 0; i < MEM_CAPACITY; i++) {
        if(!MATRIX.memory[i].active) continue;

        float score = _sim(&q, &MATRIX.memory[i].data);
        if(score > 0.99f) {
            printf(" > %s [MATCH EXATO] (%.4f)\n", MATRIX.memory[i].label, score);
        } else if(score > 0.10f) {
            printf(" > %s (%.4f)\n", MATRIX.memory[i].label, score);
        }
    }
}

// Superposição: Cria um conceito composto (A + B) e mostra similaridade
void _superpose(const char *concept_a, const char *concept_b) {
    Vec va, vb, res;
    _gen_ortho(_hash(concept_a), &va);
    _gen_ortho(_hash(concept_b), &vb);

    memset(&res, 0, sizeof(Vec));
    _bundle(&res, &va);
    _bundle(&res, &vb);

    float s1 = _sim(&res, &va);
    float s2 = _sim(&res, &vb);

    printf("\n" C_YEL "SUPERPOSIÇÃO: '%s' + '%s'" C_RST "\n", concept_a, concept_b);
    printf(" > Sim(Composto, %s) = %.4f\n", concept_a, s1);
    printf(" > Sim(Composto, %s) = %.4f\n", concept_b, s2);
}

// --- 5. INTERFACE / UTILITÁRIOS ---

void _pause_safe() {
    int c;
    while((c = getchar()) != '\n' && c != EOF) {}
}

// --- 6. MAIN ---

int main(int argc, char *argv[]) {
    _genesis();

    // Modo benchmark: ./raf_vec --bench 10000
    if(argc == 3 && strcmp(argv[1], "--bench") == 0) {
        int loops = atoi(argv[2]);
        if(loops <= 0) loops = 1;

        // Preenche memória com conceitos sintéticos
        for(int i = 0; i < MEM_CAPACITY; i++) {
            char label[32];
            snprintf(label, sizeof(label), "concept_%03d", i);
            _learn(label);
        }

        // Vetor de consulta fixo
        const char *qtext = "RAFAELIA_CORE";
        Vec q;
        _gen_ortho(_hash(qtext), &q);

        clock_t start = clock();
        double sims = 0.0;

        for(int i = 0; i < loops; i++) {
            // Consulta "manual": geramos vetor e comparamos com toda memória
            for(int m = 0; m < MEM_CAPACITY; m++) {
                if(!MATRIX.memory[m].active) continue;
                (void)_sim(&q, &MATRIX.memory[m].data);
                sims += 1.0;
            }
        }

        clock_t end = clock();
        double elapsed = (double)(end - start) / (double)CLOCKS_PER_SEC;

        double queries = (double)loops;
        printf("[BENCH] RAFAELIA VECTOR CORE\n");
        printf("[BENCH] DIM        : %d\n", DIM);
        printf("[BENCH] MEM_SLOTS  : %d\n", MEM_CAPACITY);
        printf("[BENCH] Queries    : %.0f\n", queries);
        printf("[BENCH] Cosines    : %.0f\n", sims);
        printf("[BENCH] Elapsed    : %.6f s\n", elapsed);
        printf("[BENCH] Queries/s  : %.2f\n", queries / elapsed);
        printf("[BENCH] Cosines/s  : %.2f\n", sims / elapsed);
        return 0;
    }

    // Modo CLI rápido: ./raf_vec "texto"
    if(argc == 2) {
        const char *txt = argv[1];
        _learn(txt);
        _query(txt);
        return 0;
    }

    // Modo Interativo simples
    char cmd;
    char buf1[64], buf2[64];

    do {
        printf("\033[2J\033[H");
        printf(C_GRN "=== RAFAELIA VECTOR CORE (VSA) ===\n" C_RST);
        printf("MEM HEAD : %u/%d\n", MATRIX.head, MEM_CAPACITY);
        printf("OPS      : %u\n\n", MATRIX.ops_count);

        printf("[1] APRENDER (Texto -> Vetor)\n");
        printf("[2] CONSULTAR (Similaridade)\n");
        printf("[3] SUPERPOSIÇÃO (Mix de Conceitos)\n");
        printf("[0] SAIR\n");
        printf("\nRAF> ");

        if(scanf(" %c", &cmd) != 1) cmd = '0';

        switch(cmd) {
            case '1':
                printf("Texto: ");
                if(scanf("%63s", buf1) == 1) {
                    _learn(buf1);
                    printf("Memorizado. Vetor Ortogonal Gerado.\n");
                }
                _pause_safe();
                break;
            case '2':
                printf("Query: ");
                if(scanf("%63s", buf1) == 1) {
                    _query(buf1);
                }
                _pause_safe();
                break;
            case '3':
                printf("Conceito A: ");
                if(scanf("%63s", buf1) != 1) { cmd = '0'; break; }
                printf("Conceito B: ");
                if(scanf("%63s", buf2) != 1) { cmd = '0'; break; }
                _superpose(buf1, buf2);
                _pause_safe();
                break;
            case '0':
            default:
                break;
        }

    } while(cmd != '0');

    return 0;
}
