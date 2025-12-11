/*
 * ======================================================================================
 * 🧬 RAFAELIA MATRIX SOC (BARE METAL / NO-NAMING / ATEMPORAL)
 * ======================================================================================
 * ARCH: C11 | Pointer Arithmetic Only | Linear Stack | CRC Self-Modifying
 * ======================================================================================
 */

#include <stdio.h>
#include <stdlib.h>
#include <math.h>
#include <stdint.h>
#include <string.h>
#include <time.h>

// --- MEMORY LAYOUT ---
#define S 100               // Size
#define N (S*S*S)           // Total Nodes
#define STEPS 30

// M[0..N-1]: Tensor Field
// M[N]: Global Sum
// M[N+1]: Mean
// M[N+2]: StdDev
// M[N+3]: Threshold
static float M[N + 16]; 

// H: Temporal Hash (CRC Accumulator)
static uint32_t H = 0xCAFEBABE;

// K: Constants (Radius, Thickness, Drop, Give, Decay, ThresholdFactor)
static const float K[] = {20.0f, 14.0f, 0.5f, 0.02f, 0.995f, 0.8f};

// --- CORE LOGIC (LINEAR & ANONYMOUS) ---

// _h: Update CRC Hash (Bitwise Stacking)
void _h(float v) {
    uint32_t *i = (uint32_t*)&v;
    uint32_t c = ~H;
    c = (c >> 8) ^ (0xEDB88320 & (-( (c ^ (*i & 0xFF)) & 1 )));
    c = (c >> 8) ^ (0xEDB88320 & (-( (c ^ ((*i >> 8) & 0xFF)) & 1 )));
    c = (c >> 8) ^ (0xEDB88320 & (-( (c ^ ((*i >> 16) & 0xFF)) & 1 )));
    c = (c >> 8) ^ (0xEDB88320 & (-( (c ^ ((*i >> 24) & 0xFF)) & 1 )));
    H = ~c;
}

// _0: Genesis (Toroidal Field Generation)
void _0() {
    float *p = M;
    float c = S / 2.0f;
    for (int i = 0; i < N; i++, p++) {
        int z = i % S;
        int y = (i / S) % S;
        int x = i / (S * S);

        float d1 = sqrtf((x - c) * (x - c) + (y - c) * (y - c));
        float d2 = sqrtf((d1 - K[0]) * (d1 - K[0]) + (z - c) * (z - c));
        *p = expf(-(d2 * d2) / (2 * K[1] * K[1]));
        _h(*p); // Stack entropy
    }
}

// _1: Statistics (Sum, Mean, Std)
void _1() {
    double s = 0.0, ss = 0.0;
    float *p = M;
    for (int i = 0; i < N; i++, p++) {
        double v = *p;
        s += v;
        ss += v * v;
    }
    M[N]   = (float)s;
    M[N+1] = (float)(s / N);
    M[N+2] = sqrtf((float)(ss / N) - (M[N+1] * M[N+1]));
    M[N+3] = M[N+1] + (K[5] * M[N+2]); // Threshold
}

// _2: Avalanche (Criticality Check & Distribution)
void _2() {
    float *p = M;
    float th   = M[N+3];
    float drop = th * K[2];
    float give = th * K[3];
    int av = 0;

    for (int i = 0; i < N; i++, p++) {
        if (*p > th) {
            *p -= drop;

            int z = i % S;
            int y = (i / S) % S;
            int x = i / (S * S);

            for (int dz = -1; dz <= 1; dz++) {
                int nz = z + dz;
                if (nz < 0 || nz >= S) continue;
                for (int dy = -1; dy <= 1; dy++) {
                    int ny = y + dy;
                    if (ny < 0 || ny >= S) continue;
                    for (int dx = -1; dx <= 1; dx++) {
                        int nx = x + dx;
                        if (nx < 0 || nx >= S) continue;
                        if (dx == 0 && dy == 0 && dz == 0) continue;
                        M[(nx * S * S) + (ny * S) + nz] += give;
                    }
                }
            }
            av++;
        }
    }
    _h((float)av);
}

// _3: Dissipation (Entropy Loss)
void _3() {
    float *p = M;
    for (int i = 0; i < N; i++, p++) {
        *p *= K[4];
    }
}

// _4: Report (Output State)
void _4() {
    printf("E:%.4f | A:%.6f | H:%08X\n", M[N], M[N+1], H);
}

// --- MAIN (PIPELINE EXECUTION + BENCH MODE) ---

int main(int argc, char *argv[]) {
    void (*OP[])(void) = { _0, _1, _2, _3, _4 };

    // Modo benchmark: ./raf_mtx_soc --bench 20
    if (argc == 3 && strcmp(argv[1], "--bench") == 0) {
        int steps = atoi(argv[2]);
        if (steps <= 0) steps = STEPS;

        // Genesis + stats
        OP[0]();
        OP[1]();

        clock_t t0 = clock();
        for (int k = 0; k < steps; k++) {
            OP[1]();
            OP[2]();
            OP[3]();
        }
        clock_t t1 = clock();

        double elapsed = (double)(t1 - t0) / (double)CLOCKS_PER_SEC;
        double steps_s = steps / (elapsed > 0 ? elapsed : 1e-9);
        double upd_s   = ((double)steps * (double)N) / (elapsed > 0 ? elapsed : 1e-9);

        OP[1](); // final stats

        printf("[BENCH] RAFAELIA MATRIX SOC\n");
        printf("[BENCH] Grid       : %dx%dx%d (N=%d)\n", S, S, S, N);
        printf("[BENCH] Steps      : %d\n", steps);
        printf("[BENCH] Elapsed    : %.6f s\n", elapsed);
        printf("[BENCH] Steps/s    : %.2f\n", steps_s);
        printf("[BENCH] CellUpd/s  : %.2f\n", upd_s);
        printf("[BENCH] Energy     : %.4f\n", M[N]);
        printf("[BENCH] Mean       : %.6f\n", M[N+1]);
        printf("[BENCH] Hash       : %08X\n", H);
        return 0;
    }

    // Modo padrão: pipeline atemporal completo
    OP[0]();
    OP[1]();
    OP[4]();

    for (int k = 0; k < STEPS; k++) {
        OP[1]();
        OP[2]();
        OP[3]();
    }

    OP[1]();
    OP[4]();

    return 0;
}
