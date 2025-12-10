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

#define MAX_NEWTON_ITERS   40
#define NEWTON_TOL         1e-10

// Tipos de problema
#define TYPE_POLY2  1
#define TYPE_POLY3  2
#define TYPE_POLY4  3
#define TYPE_POLY5  4
#define TYPE_POLY6  5
#define TYPE_TRIG1  6
#define TYPE_TRIG2  7
#define TYPE_TRIG3  8
#define TYPE_MAX    8

// ===================== HEADER ZIPRAF =====================
typedef struct {
    uint32_t magic;
    uint32_t crc_payload;
    uint32_t crc_header;
    uint64_t timestamp;
    uint32_t flags;
} ZipHeader;

// ===================== NÓ DE COEXISTÊNCIA (MISTO) =====================
typedef struct HyperNode {
    ZipHeader header;

    uint32_t  id;
    uint32_t  core_family;    // aqui vamos usar como TYPE_*
    uint64_t  problem_hash;
    double    difficulty;     // custo simbólico

    float     state[DIM_OCT]; // estado octonion simbólico

    int       problem_type;   // TYPE_POLY2..TYPE_TRIG3
    int       degree;         // 2..6 para polinômios, 0 para trig

    double    coeff[7];       // até grau 6: c0..c6
    double    root_approx;    // raiz aproximada
    int       newton_iters;
    int       converged;      // 1 = convergiu; 0 = não
    double    final_fx;       // |f(root)| final

    struct HyperNode *hook_next;
    struct HyperNode *ref_sheet;
    struct HyperNode *hint_path;

    uint64_t  solution_cache; // codificação inteira da solução
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

// ===================== TIMER (segundos) =====================
static double now_sec(void) {
    clock_t c = clock();
    return (double) c / (double) CLOCKS_PER_SEC;
}

// ===================== AETL RNG (pseudo-aleatório determinístico) =====================
static uint64_t aetl_seed_from_id(int id) {
    uint64_t x = 0x9E3779B97F4A7C15ULL;
    x ^= (uint64_t) id * 144000ULL;
    x ^= 0xC2B2AE3D27D4EB4FULL;
    return x ? x : 0xDEADBEEFCAFEBABEULL;
}

static uint32_t aetl_next_u32(uint64_t *state) {
    uint64_t x = *state;
    x ^= x >> 12;
    x ^= x << 25;
    x ^= x >> 27;
    *state = x;
    return (uint32_t) ((x * 2685821657736338717ULL) >> 32);
}

static int aetl_rand_int(uint64_t *state, int min, int max) {
    uint32_t r = aetl_next_u32(state);
    int span = max - min + 1;
    return min + (int) (r % (uint32_t) span);
}

static double aetl_rand_uniform(uint64_t *state, double min, double max) {
    uint32_t r = aetl_next_u32(state);
    double u = (double) r / (double) 4294967295.0;
    return min + (max - min) * u;
}

// ===================== HASH DA PERGUNTA =====================
static uint64_t hash_problem(uint32_t core_family, uint32_t id) {
    uint64_t x = ((uint64_t) core_family << 32) ^ (uint64_t) id;
    x ^= x >> 33;
    x *= 0xff51afd7ed558ccdULL;
    x ^= x >> 33;
    x *= 0xc4ceb9fe1a85ec53ULL;
    x ^= x >> 33;
    return x;
}

// ===================== AVALIAÇÃO DO PROBLEMA (f, f') =====================
static void eval_problem(const HyperNode *n, double x, double *fx, double *dfx) {
    int t = n->problem_type;

    if (t >= TYPE_POLY2 && t <= TYPE_POLY6) {
        int d = n->degree;
        const double *c = n->coeff;
        double f = c[0];
        for (int k = 1; k <= d; k++) {
            f += c[k] * pow(x, (double) k);
        }
        double df = 0.0;
        for (int k = 1; k <= d; k++) {
            df += (double) k * c[k] * pow(x, (double) (k - 1));
        }
        *fx = f;
        *dfx = df;
        return;
    }

    // Trigonometria (três formas)
    if (t == TYPE_TRIG1) {
        // f(x) = sin(x) - 0.5
        double s = sin(x);
        double c = cos(x);
        *fx = s - 0.5;
        *dfx = c;
        return;
    } else if (t == TYPE_TRIG2) {
        // f(x) = sin(x) + cos(2x)
        double s = sin(x);
        double c2 = cos(2.0 * x);
        double s2 = sin(2.0 * x);
        *fx = s + c2;
        *dfx = cos(x) - 2.0 * s2;
        return;
    } else if (t == TYPE_TRIG3) {
        // f(x) = sin(x) + 0.3*cos(3x) - 0.1
        double s = sin(x);
        double c3 = cos(3.0 * x);
        double s3 = sin(3.0 * x);
        *fx = s + 0.3 * c3 - 0.1;
        *dfx = cos(x) - 0.9 * s3;
        return;
    }

    // fallback
    *fx = 0.0;
    *dfx = 1.0;
}

// ===================== NEWTON–RAPHSON GENÉRICO =====================
typedef struct {
    double root;
    int    iters;
    int    converged;
    double final_fx;
} NewtonResult;

static NewtonResult newton_solve(const HyperNode *n, double x0) {
    NewtonResult r;
    r.root = x0;
    r.iters = 0;
    r.converged = 0;
    r.final_fx = 0.0;

    double x = x0;
    for (int k = 0; k < MAX_NEWTON_ITERS; k++) {
        double fx, dfx;
        eval_problem(n, x, &fx, &dfx);

        if (fabs(dfx) < 1e-14) {
            break;
        }

        double step = fx / dfx;
        x -= step;
        r.iters++;

        if (fabs(step) < NEWTON_TOL) {
            r.converged = 1;
            r.final_fx = fabs(fx);
            r.root = x;
            return r;
        }
    }

    // valor final
    double fx_last, dfx_last;
    eval_problem(n, x, &fx_last, &dfx_last);
    r.final_fx = fabs(fx_last);
    r.root = x;
    return r;
}

// ===================== ESTATÍSTICAS GLOBAIS DE NEWTON =====================
typedef struct {
    long long total_nodes;
    long long total_iters;
    long long converged;

    int       min_iters;
    int       max_iters;

    long long count_by_type[TYPE_MAX + 1];
    long long iters_by_type[TYPE_MAX + 1];
    long long conv_by_type[TYPE_MAX + 1];
} NewtonStats;

static NewtonStats g_newton;

// ===================== CONSTRUÇÃO DO PROBLEMA POR NÓ =====================
static void build_problem_for_node(HyperNode *n, int i) {
    uint64_t rng = aetl_seed_from_id(i);

    // Sorteio do tipo: 0..7 -> 8 tipos
    int r = aetl_rand_int(&rng, 0, 7);
    int t;
    if (r <= 4) {
        // Polinômios mais frequentes
        t = TYPE_POLY2 + r;  // 1..5 mapeia para graus 2..6
    } else {
        // Trigonometria
        t = TYPE_TRIG1 + (r - 5); // 6..8
    }

    n->problem_type = t;
    n->core_family = (uint32_t) t;

    // Estado octonion simbólico
    for (int d = 0; d < DIM_OCT; d++) {
        n->state[d] = (float) ((double)(i + 1) * (double)(d + 1) * PHI);
    }

    int base = i + 1;

    // Polinômios
    if (t >= TYPE_POLY2 && t <= TYPE_POLY6) {
        int degree = t - TYPE_POLY2 + 2; // 2..6
        n->degree = degree;

        // Coeficientes pequenos em [-3,3], líder = 1.0
        n->coeff[degree] = 1.0;
        for (int k = 0; k < degree; k++) {
            int coef_i = aetl_rand_int(&rng, -3, 3);
            n->coeff[k] = (double) coef_i;
        }

        // Chute inicial em [-5,5]
        double x0 = aetl_rand_uniform(&rng, -5.0, 5.0);
        NewtonResult res = newton_solve(n, x0);

        n->root_approx  = res.root;
        n->newton_iters = res.iters;
        n->converged    = res.converged;
        n->final_fx     = res.final_fx;

        // Difficulty: peso = grau * 2 + iterações + log(base)
        double diff = (double) degree * 2.0 + (double) res.iters + log(1.0 + (double) base);
        n->difficulty = diff;

        double mag = fabs(res.root);
        if (mag > 1e6) mag = 1e6;
        long long scaled = (long long) llround(mag * 1e9);
        if (scaled < 0) scaled = 0;
        n->solution_cache = (uint64_t) scaled;

    } else {
        // Trigonometria
        n->degree = 0;
        for (int k = 0; k < 7; k++) {
            n->coeff[k] = 0.0;
        }

        // Chute inicial em [-4π, 4π]
        double x0 = aetl_rand_uniform(&rng, -4.0 * M_PI, 4.0 * M_PI);
        NewtonResult res = newton_solve(n, x0);

        n->root_approx  = res.root;
        n->newton_iters = res.iters;
        n->converged    = res.converged;
        n->final_fx     = res.final_fx;

        // Difficulty: peso fixo 6.0 (complexidade trig) + iters + log(base)
        double diff = 6.0 + (double) res.iters + log(1.0 + (double) base);
        n->difficulty = diff;

        double mag = fabs(res.root);
        if (mag > 1e6) mag = 1e6;
        long long scaled = (long long) llround(mag * 1e6); // menos escala aqui
        if (scaled < 0) scaled = 0;
        n->solution_cache = (uint64_t) scaled;
    }

    // Atualiza estatísticas globais de Newton
    g_newton.total_nodes++;
    g_newton.total_iters += n->newton_iters;
    if (n->converged) g_newton.converged++;

    if (g_newton.min_iters > n->newton_iters) g_newton.min_iters = n->newton_iters;
    if (g_newton.max_iters < n->newton_iters) g_newton.max_iters = n->newton_iters;

    int idx = n->problem_type;
    if (idx >= 0 && idx <= TYPE_MAX) {
        g_newton.count_by_type[idx]++;
        g_newton.iters_by_type[idx] += n->newton_iters;
        if (n->converged) g_newton.conv_by_type[idx]++;
    }
}

// ===================== GÊNESE: UNIVERSO MISTO =====================
HyperNode *genesis_web(int depth) {
    HyperNode *nodes = (HyperNode *) calloc(depth, sizeof(HyperNode));
    if (!nodes) {
        fprintf(stderr, "[ERRO] calloc falhou ao alocar %d nós.\n", depth);
        exit(1);
    }

    memset(&g_newton, 0, sizeof(g_newton));
    g_newton.min_iters = 1000000000;

    double t0 = now_sec();

    for (int i = 0; i < depth; i++) {
        HyperNode *n = &nodes[i];

        n->id = (uint32_t) i;
        n->header.magic = MAGIC_RAF;
        n->header.flags = TAG14_BIT;
        n->header.timestamp = (uint64_t) time(NULL);

        n->problem_type = 0;
        n->degree = 0;
        n->solution_cache = 0;

        n->hint_path = NULL;

        n->problem_hash = hash_problem(n->core_family, n->id);

        build_problem_for_node(n, i);

        if (i < depth - 1) {
            n->hook_next = &nodes[i + 1];
            n->ref_sheet = &nodes[i + 1];
        } else {
            n->hook_next = NULL;
            n->ref_sheet = NULL;
        }
    }

    // Hints estilo Fibonacci-Rafael (como saltos simbólicos)
    int fib_rafa[] = {2, 4, 7, 12, 20, 33, 54, 88, 143, 232};
    int fib_count = (int) (sizeof(fib_rafa) / sizeof(fib_rafa[0]));
    for (int k = 0; k < fib_count; k++) {
        int idx = fib_rafa[k];
        if (idx >= 0 && idx < depth - 1) {
            nodes[idx].hint_path = &nodes[idx + 1];
        }
    }

    // Blindagem ZipRaf
    for (int i = 0; i < depth; i++) {
        HyperNode *n = &nodes[i];
        n->header.crc_payload = calc_crc(n->state, sizeof(n->state));
        n->header.crc_header  = calc_crc(&n->header, sizeof(uint32_t) * 2);
    }

    double t1 = now_sec();
    double dt = t1 - t0;
    if (dt < 0.0) dt = 0.0;

    printf("[GENESIS] Construído universo MISTO de %d nós em %.6f s\n", depth, dt);

    return nodes;
}

// ===================== RESOLUÇÃO LINEAR =====================
typedef struct {
    int      steps;
    int      hint_jumps;
    int      crc_checked;
    int      crc_fail;
    double   elapsed;
    double   equiv_ops;
    uint64_t total_sum;
} SolveStats;

SolveStats solve_linear(HyperNode *start_node) {
    SolveStats st;
    memset(&st, 0, sizeof(SolveStats));

    double t0 = now_sec();

    HyperNode *ptr = start_node;
    while (ptr) {
        uint32_t check_header = calc_crc(&ptr->header, sizeof(uint32_t) * 2);
        st.crc_checked++;
        if (check_header != ptr->header.crc_header) {
            st.crc_fail++;
        }

        st.total_sum += ptr->solution_cache;
        st.equiv_ops += ptr->difficulty;

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
        st.elapsed = 1e-12;
    }

    return st;
}

// ===================== NOME DO TIPO =====================
static const char *type_name(int t) {
    switch (t) {
        case TYPE_POLY2: return "POLY2 (grau 2)";
        case TYPE_POLY3: return "POLY3 (grau 3)";
        case TYPE_POLY4: return "POLY4 (grau 4)";
        case TYPE_POLY5: return "POLY5 (grau 5)";
        case TYPE_POLY6: return "POLY6 (grau 6)";
        case TYPE_TRIG1: return "TRIG1 (sin(x)-0.5)";
        case TYPE_TRIG2: return "TRIG2 (sin(x)+cos(2x))";
        case TYPE_TRIG3: return "TRIG3 (sin(x)+0.3cos(3x)-0.1)";
        default:         return "DESCONHECIDO";
    }
}

// ===================== MAIN =====================
int main(int argc, char **argv) {
    int depth = 10000;
    if (argc > 1) {
        int tmp = atoi(argv[1]);
        if (tmp > 0) depth = tmp;
    }

    printf("=== RAFAELIA COEXISTENCE ENGINE (MIXED: POLY 2-6 + TRIG) ===\n");
    printf("Meta: cada nó resolve um problema sorteado (polinômio 2..6 ou trigonometria).\n\n");

    HyperNode *universe = genesis_web(depth);

    printf("\n[PROCESS] Iniciando Resolução Linear (MIXED BENCHMARK)...\n");
    SolveStats st = solve_linear(&universe[0]);

    double nodes_per_sec = (double) st.steps / st.elapsed;
    double ops_per_sec   = st.equiv_ops / st.elapsed;
    double avg_newton    = (g_newton.total_nodes > 0)
                           ? (double) g_newton.total_iters / (double) g_newton.total_nodes
                           : 0.0;
    double conv_rate     = (g_newton.total_nodes > 0)
                           ? (double) g_newton.converged * 100.0 / (double) g_newton.total_nodes
                           : 0.0;

    printf("\n⚡ RESULTADO FINAL ATINGIDO (MIXED)\n");
    printf("   Nós percorridos       : %d\n", st.steps);
    printf("   Saltos via hint_path  : %d\n", st.hint_jumps);
    printf("   CRCs verificados      : %d\n", st.crc_checked);
    printf("   Falhas de CRC         : %d\n", st.crc_fail);
    printf("   Soma total (cache)    : %llu\n", (unsigned long long) st.total_sum);
    printf("   Dificuldade acumulada : %.3f (unid. simbólicas)\n", st.equiv_ops);
    printf("   Tempo de resolução    : %.9f s\n", st.elapsed);
    printf("   Throughput nós/s      : %.3f\n", nodes_per_sec);
    printf("   Throughput ops_eq/s   : %.3f\n", ops_per_sec);

    printf("\n📊 Estatísticas de Newton–Raphson\n");
    printf("   Nós com Newton        : %lld\n", g_newton.total_nodes);
    printf("   Iterações totais      : %lld\n", g_newton.total_iters);
    printf("   Iterações mín / máx   : %d / %d\n", g_newton.min_iters, g_newton.max_iters);
    printf("   Iterações médias/nó   : %.3f\n", avg_newton);
    printf("   Convergências         : %lld (%.2f%%)\n", g_newton.converged, conv_rate);

    printf("\n   Por tipo:\n");
    for (int t = TYPE_POLY2; t <= TYPE_TRIG3; t++) {
        long long c   = g_newton.count_by_type[t];
        long long it  = g_newton.iters_by_type[t];
        long long cv  = g_newton.conv_by_type[t];
        if (c == 0) continue;
        double avg_it = (double) it / (double) c;
        double cr     = (double) cv * 100.0 / (double) c;
        printf("     %-24s : nós=%6lld | iters_tot=%8lld | it_med=%.3f | conv=%.2f%%\n",
               type_name(t), c, it, avg_it, cr);
    }

    free(universe);
    return 0;
}
