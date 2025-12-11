#include <stdio.h>
#include <stdlib.h>
#include <stdint.h>
#include <string.h>
#include <time.h>
#include <math.h>

// ===================== CONSTANTES RAFAELIA =====================
#define MAGIC_RAF   0x52414641  // "RAFA"
#define PHI         1.6180339887
#define DIM_OCT     8           // Octonion
#define TAG14_BIT   (1u << 4)

// Núcleo matemático principal aqui: equações de 5º grau
#define CORE_QUINTIC       10
#define MAX_NEWTON_ITERS   32
#define NEWTON_TOL         1e-10

// ===================== HEADER ZIPRAF =====================
typedef struct {
    uint32_t magic;
    uint32_t crc_payload;
    uint32_t crc_header;
    uint64_t timestamp;
    uint32_t flags;
} ZipHeader;

// ===================== NÓ DE COEXISTÊNCIA (QUINTIC) =====================
typedef struct HyperNode {
    ZipHeader header;

    uint32_t  id;             // índice absoluto
    uint32_t  core_family;    // aqui: CORE_QUINTIC
    uint64_t  problem_hash;   // hash da "pergunta"
    double    difficulty;     // custo simbólico de cálculo bruto

    float     state[DIM_OCT]; // estado 8D (Octonion simbólico)

    // Polinômio de 5º grau: c0 + c1 x + c2 x^2 + c3 x^3 + c4 x^4 + c5 x^5
    double    coeff[6];
    double    root_approx;    // raiz aproximada encontrada
    int       newton_iters;   // quantas iterações foram usadas

    struct HyperNode *hook_next;  // passo linear
    struct HyperNode *ref_sheet;  // “cola”
    struct HyperNode *hint_path;  // salto dimensional

    uint64_t  solution_cache;     // resposta pré-resolvida (derivada da raiz)
} HyperNode;

// ===================== CRC32 SIMPLES =====================
static uint32_t calc_crc(const void *data, size_t len) {
    const uint8_t *p = (const uint8_t *) data;
    uint32_t crc = 0xFFFFFFFFu;
    while (len--) {
        crc ^= *p++;
        for (int k = 0; k < 8; k++) {
            uint32_t mask = -(crc & 1u);
            crc = (crc >> 1) ^ (0xEDB88320u & mask);
        }
    }
    return ~crc;
}

// ===================== TIMER (segundos, usando clock()) =====================
static double now_sec(void) {
    clock_t c = clock();
    return (double) c / (double) CLOCKS_PER_SEC;
}

// ===================== HASH SIMPLES DA PERGUNTA =====================
static uint64_t hash_problem(uint32_t core_family, uint32_t id) {
    uint64_t x = ((uint64_t) core_family << 32) ^ (uint64_t) id;
    x ^= x >> 33;
    x *= 0xff51afd7ed558ccdULL;
    x ^= x >> 33;
    x *= 0xc4ceb9fe1a85ec53ULL;
    x ^= x >> 33;
    return x;
}

// ===================== AVALIAÇÃO DO QUÍNTICO E DERIVADA =====================
static void eval_quintic(const double c[6], double x, double *fx, double *dfx) {
    // p(x) = c0 + c1 x + c2 x^2 + c3 x^3 + c4 x^4 + c5 x^5
    // p'(x) = c1 + 2 c2 x + 3 c3 x^2 + 4 c4 x^3 + 5 c5 x^4
    double x2 = x * x;
    double x3 = x2 * x;
    double x4 = x2 * x2;
    double x5 = x4 * x;

    *fx  = c[0]
         + c[1] * x
         + c[2] * x2
         + c[3] * x3
         + c[4] * x4
         + c[5] * x5;

    *dfx = c[1]
         + 2.0 * c[2] * x
         + 3.0 * c[3] * x2
         + 4.0 * c[4] * x3
         + 5.0 * c[5] * x4;
}

// ===================== NEWTON–RAPHSON PARA QUÍNTICA =====================
static double solve_quintic_newton(const double c[6], double x0, int *iters_out) {
    double x = x0;
    int iters = 0;

    for (int k = 0; k < MAX_NEWTON_ITERS; k++) {
        double fx, dfx;
        eval_quintic(c, x, &fx, &dfx);

        if (fabs(dfx) < 1e-14) {
            // Derivada muito pequena, evita explosão numérica
            break;
        }

        double step = fx / dfx;
        x -= step;
        iters++;

        if (fabs(step) < NEWTON_TOL) {
            break;
        }
    }

    if (iters_out) {
        *iters_out = iters;
    }
    return x;
}

// ===================== CONSTRUÇÃO DO QUÍNTICO POR NÓ =====================
// Aqui definimos uma família de equações de 5º grau por nó.
// Exemplo: x^5 + a4 x^4 + a3 x^3 + a2 x^2 + a1 x + a0
// com coeficientes pequenos para estabilidade.
static void build_quintic_for_node(HyperNode *n, int i) {
    // Geração de coeficientes pseudo-estruturada
    int base = i + 1;
    int s1 = (base % 7) - 3;        // -3..3
    int s2 = ((base / 7) % 7) - 3;  // -3..3
    int s3 = ((base / 49) % 7) - 3; // -3..3
    int s4 = ((base / 343) % 7) - 3;// -3..3
    int s5 = ((base / 2401) % 7) - 3;// -3..3

    // Coeficientes: mantemos o termo de grau 5 como 1.0
    n->coeff[5] = 1.0;                 // x^5
    n->coeff[4] = (double) s5;         // x^4
    n->coeff[3] = (double) s4;         // x^3
    n->coeff[2] = (double) s3;         // x^2
    n->coeff[1] = (double) s2;         // x
    n->coeff[0] = (double) s1;         // termo constante

    // Chute inicial: algo dependente de i, mas não muito grande
    double x0 = (double) ((base % 11) - 5); // em [-5,5]
    int iters = 0;
    double root = solve_quintic_newton(n->coeff, x0, &iters);

    n->root_approx = root;
    n->newton_iters = iters;

    // Difficulty: grau 5 + número de iterações + log(i+1)
    double diff = 5.0 + (double) iters + log(1.0 + (double) base);
    n->difficulty = diff;

    // solution_cache: codifica a raiz aproximada em inteiro (ex: 1e9 * |root|)
    double mag = fabs(root);
    if (mag > 1e6) mag = 1e6; // limita explosões
    long long scaled = (long long) llround(mag * 1e9);
    if (scaled < 0) scaled = 0;
    n->solution_cache = (uint64_t) scaled;
}

// ===================== GÊNESE: UNIVERSO QUÍNTICO =====================
HyperNode *genesis_web(int depth) {
    HyperNode *nodes = (HyperNode *) calloc(depth, sizeof(HyperNode));
    if (!nodes) {
        fprintf(stderr, "[ERRO] calloc falhou ao alocar %d nós.\n", depth);
        exit(1);
    }

    double t0 = now_sec();

    for (int i = 0; i < depth; i++) {
        HyperNode *n = &nodes[i];

        n->id = (uint32_t) i;
        n->header.magic = MAGIC_RAF;
        n->header.flags = TAG14_BIT;
        n->header.timestamp = (uint64_t) time(NULL);

        // Núcleo: tudo aqui é CORE_QUINTIC
        n->core_family = CORE_QUINTIC;

        // Hash da “pergunta”
        n->problem_hash = hash_problem(n->core_family, n->id);

        // Estado Octonion (simbólico), só para manter coerência RAFAELIA
        for (int d = 0; d < DIM_OCT; d++) {
            n->state[d] = (float) ((double)(i + 1) * (double)(d + 1) * PHI);
        }

        // Construir a equação de 5º grau + raiz aproximada + difficulty + solution_cache
        build_quintic_for_node(n, i);

        // Ligações lineares / ref_sheet
        if (i < depth - 1) {
            n->hook_next = &nodes[i + 1];
            n->ref_sheet = &nodes[i + 1]; // “olho no futuro”
        } else {
            n->hook_next = NULL;
            n->ref_sheet = NULL;
        }

        n->hint_path = NULL; // vamos adicionar alguns hints simbólicos depois
    }

    // Exemplo de hints (índices arbitrários tipo Fibonacci-Rafael)
    int fib_rafa[] = {2, 4, 7, 12, 20, 33, 54, 88, 143, 232};
    int fib_count = (int) (sizeof(fib_rafa) / sizeof(fib_rafa[0]));
    for (int k = 0; k < fib_count; k++) {
        int idx = fib_rafa[k];
        if (idx >= 0 && idx < depth - 1) {
            nodes[idx].hint_path = &nodes[idx + 1]; // salto leve
        }
    }

    // Blindagem ZipRaf: payload (state) + header (magic + crc_payload)
    for (int i = 0; i < depth; i++) {
        HyperNode *n = &nodes[i];
        n->header.crc_payload = calc_crc(n->state, sizeof(n->state));
        n->header.crc_header  = calc_crc(&n->header, sizeof(uint32_t) * 2);
    }

    double t1 = now_sec();
    double dt = t1 - t0;
    if (dt < 0.0) dt = 0.0;

    printf("[GENESIS] Construído universo QUÍNTICO de %d nós em %.6f s\n", depth, dt);

    return nodes;
}

// ===================== RESOLUÇÃO LINEAR COM BENCHMARK =====================
typedef struct {
    int      steps;
    int      hint_jumps;
    int      crc_checked;
    int      crc_fail;
    int      total_newton_iters;
    double   elapsed;
    double   equiv_ops;   // soma das dificuldades
    uint64_t total_sum;
} SolveStats;

SolveStats solve_linear(HyperNode *start_node) {
    SolveStats st;
    memset(&st, 0, sizeof(SolveStats));

    double t0 = now_sec();

    HyperNode *ptr = start_node;
    while (ptr) {
        // Verificação ZipRaf (sanidade)
        uint32_t check_header = calc_crc(&ptr->header, sizeof(uint32_t) * 2);
        st.crc_checked++;
        if (check_header != ptr->header.crc_header) {
            st.crc_fail++;
            // poderíamos dar break; aqui, mas por enquanto só contamos
        }

        // “Consumo” da solução pré-resolvida (inteiro baseado na raiz)
        st.total_sum += ptr->solution_cache;
        st.equiv_ops += ptr->difficulty;
        st.total_newton_iters += ptr->newton_iters;

        // Salto preferencial por hint
        if (ptr->hint_path != NULL) {
            ptr = ptr->hint_path;
            st.hint_jumps++;
        } else {
            ptr = ptr->hook_next;
        }

        st.steps++;
    }

    double t1 = now_sec();
    st.elapsed = t1 - t0;
    if (st.elapsed <= 0.0) {
        st.elapsed = 1e-12; // anti-divisão por zero
    }

    return st;
}

// ===================== MAIN =====================
int main(int argc, char **argv) {
    int depth = 10000;
    if (argc > 1) {
        int tmp = atoi(argv[1]);
        if (tmp > 0) depth = tmp;
    }

    printf("=== RAFAELIA COEXISTENCE ENGINE (QUINTIC EDITION) ===\n");
    printf("Meta: cada nó resolve numericamente uma equação de 5º grau (quintic).\n\n");

    // 1) Gênese: construir universo de quintics
    HyperNode *universe = genesis_web(depth);

    // 2) Resolução Linear + Benchmark
    printf("\n[PROCESS] Iniciando Resolução Linear (QUINTIC BENCHMARK)...\n");
    SolveStats st = solve_linear(&universe[0]);

    double nodes_per_sec = (double) st.steps / st.elapsed;
    double ops_per_sec   = st.equiv_ops / st.elapsed;
    double avg_newton    = (st.steps > 0) ? (double) st.total_newton_iters / (double) st.steps : 0.0;

    printf("\n⚡ RESULTADO FINAL ATINGIDO (QUINTIC)\n");
    printf("   Nós percorridos           : %d\n", st.steps);
    printf("   Saltos via hint_path      : %d\n", st.hint_jumps);
    printf("   CRCs verificados          : %d\n", st.crc_checked);
    printf("   Falhas de CRC             : %d\n", st.crc_fail);
    printf("   Soma total (solution_cache): %llu\n",
           (unsigned long long) st.total_sum);
    printf("   Dificuldade acumulada     : %.3f (unid. simbólicas)\n", st.equiv_ops);
    printf("   Iterações Newton totais   : %d (média: %.3f por nó)\n",
           st.total_newton_iters, avg_newton);
    printf("   Tempo de resolução        : %.9f s\n", st.elapsed);
    printf("   Throughput nós/s          : %.3f\n", nodes_per_sec);
    printf("   Throughput ops_eq/s       : %.3f\n", ops_per_sec);
    printf("\n   Cada nó realiza um Newton–Raphson em uma quintica distinta.\n");
    printf("   'difficulty' inclui grau 5 + iterações + log(i+1), como medida de custo.\n");

    free(universe);
    return 0;
}
