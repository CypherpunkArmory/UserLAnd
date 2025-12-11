/*
 * ======================================================================================
 * 🌌 RAFAELIA OCTONION CORE (v9.0-b) – TUNED
 * ======================================================================================
 * MATH: Octonion Algebra (O) + Zeta Potential (n9)
 * ARCH: C11 | Pointer Arithmetic | Unrolled Math | No Dependencies
 * TARGET: Max FLOPS | Zero Latency | 64-byte Alignment
 * ======================================================================================
 */

/* [1] LOW LEVEL INCLUDES */
#define _POSIX_C_SOURCE 200809L
#include <stdio.h>
#include <stdlib.h>
#include <math.h>
#include <stdint.h>
#include <time.h>

/* [2] HYPER-CONSTANTS */
#define N 4096          /* Nodes (Matrix Rows) */
#define D 9             /* Dimensions (8 Octonion + 1 Phi) */
#define ALIGN 64        /* Cache Line */

/* [3] THE MATRIX (Aligned Monolith) */
typedef struct __attribute__((aligned(ALIGN))) {
    float M[N * D];     /* The Tensor (Flat) */
    uint32_t S;         /* State (CRC/Time) */
    uint64_t C;         /* Cycles */
} _X;

static _X X;            /* Global Static Instance (Zero BSS) */

/* [4] KERNEL MATEMÁTICO (OCTONION ALGEBRA) */

/* Fast Random (Xorshift) */
static inline uint32_t _r(uint32_t *s) {
    uint32_t x = *s;
    x ^= x << 13; x ^= x >> 17; x ^= x << 5;
    *s = x;
    return x;
}

/*
 * Octonion Multiplication: z = x * y
 * Fano Plane Logic (Hardcoded for Speed/Vectorization)
 * p = input x (8 floats), q = input y (8 floats), r = output z
 */
static inline void _mul8(const float *restrict p, const float *restrict q, float *restrict r) {
    /* Real Part */
    r[0] = p[0]*q[0] - p[1]*q[1] - p[2]*q[2] - p[3]*q[3] - p[4]*q[4] - p[5]*q[5] - p[6]*q[6] - p[7]*q[7];

    /* Imaginary Parts (Cross Products) */
    r[1] = p[0]*q[1] + p[1]*q[0] + p[2]*q[3] - p[3]*q[2] + p[4]*q[5] - p[5]*q[4] - p[6]*q[7] + p[7]*q[6];
    r[2] = p[0]*q[2] - p[1]*q[3] + p[2]*q[0] + p[3]*q[1] + p[4]*q[6] + p[5]*q[7] - p[6]*q[4] - p[7]*q[5];
    r[3] = p[0]*q[3] + p[1]*q[2] - p[2]*q[1] + p[3]*q[0] + p[4]*q[7] - p[5]*q[6] + p[6]*q[5] - p[7]*q[4];
    r[4] = p[0]*q[4] - p[1]*q[5] - p[2]*q[6] - p[3]*q[7] + p[4]*q[0] + p[5]*q[1] + p[6]*q[2] + p[7]*q[3];
    r[5] = p[0]*q[5] + p[1]*q[4] - p[2]*q[7] + p[3]*q[6] - p[4]*q[1] + p[5]*q[0] - p[6]*q[3] + p[7]*q[2];
    r[6] = p[0]*q[6] + p[1]*q[7] + p[2]*q[4] - p[3]*q[5] - p[4]*q[2] + p[5]*q[3] + p[6]*q[0] - p[7]*q[1];
    r[7] = p[0]*q[7] - p[1]*q[6] + p[2]*q[5] + p[3]*q[4] - p[4]*q[3] - p[5]*q[2] + p[6]*q[1] + p[7]*q[0];
}

/* Sigmoid Fast Approximation */
static inline float _sig(float x) {
    return x / (1.0f + fabsf(x));
}

/* [5] NÚCLEO OPERACIONAL (LINEAR PROCESSING) */

void _0() { /* Genesis */
    X.S = 0x52414641; /* Seed "RAFA" */
    float *p = X.M;
    for (int i = 0; i < N*D; i++)
        *p++ = ((float)(_r(&X.S) % 2000) / 1000.0f) - 1.0f;
}

void _1() { /* Hyper-Process (n9 Evolution) */
    float tmp[8];
    float *p = X.M;                   /* Cursor Current */
    float *e = X.M + (N * D);         /* End Marker */
    float *q = X.M + ((_r(&X.S) % N) * D); /* Random Peer (Entanglement) */

    /* Loop Linear Otimizado */
    while (p < e) {
        /* 1. Octonion Interaction (8 dimensions) */
        _mul8(p, q, tmp);

        /* 2. Feedback Loop & Phi Modulation (9th dimension) */
        float phi = p[8];
        float norm = 0.0f;

        /* Update Octonion part (0-7) – TUNED:
         *  - Decaimento mais suave: 0.995f
         *  - Acoplamento mais forte: 0.08f
         */
        for (int k = 0; k < 8; k++) {
            p[k] = (p[k] * 0.995f) + (tmp[k] * 0.08f * phi);
            norm += p[k] * p[k];
        }

        /* 3. Update Phi (Observer / Energy) – TUNED:
         *  - Correção mais forte da norma: 0.2f
         */
        norm = sqrtf(norm);
        p[8] = _sig(phi + (norm - 1.0f) * 0.2f);

        /* Move pointers */
        p += D;

        /* Cyclic peer pointer (branchless wrap) */
        q += D;
        if (q >= e) q = X.M;
    }

    X.C++;
    /* Time Tick via CRC-like mix */
    X.S ^= X.S << 13; X.S ^= X.S >> 17; X.S ^= X.S << 5;
}

/* Output Compacto (Benchmark / Status) */
void _2() {
    float E = 0.0f; /* Energy */
    float Z = 0.0f; /* Zeta (n9 mean) */
    float *p = X.M;

    for (int i = 0; i < N; i++) {
        for (int k = 0; k < 8; k++) E += fabsf(p[k]);
        Z += p[8];
        p += D;
    }

    /* Mais precisão na energia (NRG) */
    printf("\r[CYC %lu] S:%08X | NRG:%.6f | ZETA(n9):%.4f | IOPS: ULTRA",
           X.C, X.S, E / N, Z / N);
    fflush(stdout);
}

/* [6] MAIN PIPELINE */

int main() {
    printf("\033[2J\033[H");
    printf("\x1b[36m=== RAFAELIA OCTONION CORE [v9.0-b] ===\x1b[0m\n");
    printf("DIM: %d (8 Oct + 1 Phi) | NODES: %d | MEM: %lu KB\n",
           D, N, (unsigned long)(sizeof(_X) / 1024));

    _0(); /* Init */

    clock_t t0 = clock();

    /* Rodar 10000 épocas de aquecimento/medição */
    for (int i = 0; i < 10000; i++) {
        _1();
        if (i % 100 == 0) _2();
    }

    clock_t t1 = clock();
    double dt = (double)(t1 - t0) / CLOCKS_PER_SEC;

    printf("\n\n\x1b[32m[DONE]\x1b[0m\n");

    /* Cálculo de Giga-Ops (aprox 200 ops per node per epoch) */
    double ops = (double)N * 200.0 * 10000.0;
    printf("SPEED: %.2f GFLOPS (Est.)\n", (ops / dt) / 1e9);

    return 0;
}
