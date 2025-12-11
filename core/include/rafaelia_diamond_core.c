/*
 * ======================================================================================
 * 💎 RAFAELIA DIAMOND CORE: SINGLE-SHOT ATEMPORAL ENGINE (PATCHED)
 * ======================================================================================
 * STATUS: Warning-Free | C11 Standard | -O3 Optimized
 * INTEGRAÇÃO: Hypercubo + Fibonacci + 42 Hyperformas + Matriz
 * ======================================================================================
 */

#include <stdio.h>
#include <stdlib.h>
#include <stdint.h>
#include <math.h>
#include <string.h>

// --- 1. CONSTANTES UNIVERSAIS ---
#define N_NODES 42
#define FIB_STEPS 15
#define PI 3.14159265358979323846
#define SQ_PI 1.77245385091
#define OMEGA_SEED 1.0

const char INTENCAO[] = "VERBO_VIVO::EU_SOU::TESSERACT_ATIVADO";

// --- 2. ESTRUTURAS ---
typedef struct {
    uint32_t id;
    uint32_t type;
    float angle_deg;
    float x, y;
    uint32_t parity;
    uint32_t bit_hash;
} Node42;

typedef struct {
    uint64_t step;
    double value;
    double delta;
} FibState;

typedef struct {
    char header[64];
    double alpha;
    double omega;
    FibState fib_seq[FIB_STEPS];
    Node42 nodes[N_NODES];
    uint8_t matrix[N_NODES][N_NODES];
} GlobalState;

// --- 3. NÚCLEO LÓGICO ---

void _genesis(GlobalState *S) {
    memset(S, 0, sizeof(GlobalState));
    strncpy(S->header, INTENCAO, 63);
    S->alpha = 1.0;
    S->omega = OMEGA_SEED;
}

void _calc_fibonacci(GlobalState *S) {
    S->fib_seq[0].step = 0; S->fib_seq[0].value = S->omega;
    S->fib_seq[1].step = 1; S->fib_seq[1].value = S->omega;
    
    for(int i=2; i<FIB_STEPS; i++) {
        S->fib_seq[i].step = i;
        double d = S->omega * (i % 2 == 0 ? 1.0 : -0.1);
        S->fib_seq[i].delta = d;
        S->fib_seq[i].value = S->fib_seq[i-1].value + S->fib_seq[i-2].value + d;
    }
}

void _calc_geometry(GlobalState *S) {
    float deg_step = 360.0f / (float)N_NODES;
    float center = 500.0f;
    float radius = 400.0f;

    for(int i=0; i<N_NODES; i++) {
        Node42 *n = &S->nodes[i];
        n->id = i + 1;
        n->angle_deg = (float)(i + 1) * deg_step * (float)SQ_PI;
        while(n->angle_deg >= 360.0f) n->angle_deg -= 360.0f;

        float rad = n->angle_deg * (float)(PI / 180.0);
        n->x = center + cosf(rad) * radius;
        n->y = center + sinf(rad) * radius;

        n->type = (i % 4);
        n->parity = (i % 2);
        n->bit_hash = (n->id * 0x9E3779B9) ^ ((uint32_t)n->angle_deg);
    }
}

void _connect_matrix(GlobalState *S) {
    for(int i=0; i<N_NODES; i++) {
        for(int j=i+1; j<N_NODES; j++) {
            int link = 0;
            // Regra 1: Tipo Fractal igual
            if(S->nodes[i].type == S->nodes[j].type) link = 1;
            // Regra 2: Angulo < 15
            if(!link) {
                float diff = fabsf(S->nodes[i].angle_deg - S->nodes[j].angle_deg);
                if(diff > 180.0f) diff = 360.0f - diff;
                if(diff < 15.0f) link = 1;
            }
            if(link) {
                S->matrix[i][j] = 1;
                S->matrix[j][i] = 1;
            }
        }
    }
}

// --- CORREÇÃO APLICADA AQUI ---
void _render_svg(GlobalState *S) {
    const char* colors[] = {"#4EC8E3", "#FFD700", "#FF7F50", "#ADFF2F"};

    printf("<svg width='1000' height='1000' xmlns='http://www.w3.org/2000/svg' style='background:#111'>\n");
    
    // FIX: Adicionado %s e %.2f para consumir os argumentos
    printf("\n", S->header);
    printf("\n", S->fib_seq[FIB_STEPS-1].value);

    // Conexões
    printf("<g stroke='#555' stroke-width='1' opacity='0.3'>\n");
    for(int i=0; i<N_NODES; i++) {
        for(int j=i+1; j<N_NODES; j++) {
            if(S->matrix[i][j]) {
                printf("<line x1='%.2f' y1='%.2f' x2='%.2f' y2='%.2f'/>\n", 
                       S->nodes[i].x, S->nodes[i].y, S->nodes[j].x, S->nodes[j].y);
            }
        }
    }
    printf("</g>\n");

    // Nós
    for(int i=0; i<N_NODES; i++) {
        Node42 *n = &S->nodes[i];
        printf("<g>\n");
        printf("  <circle cx='%.2f' cy='%.2f' r='8' fill='%s' stroke='#fff' stroke-width='1'/>\n", 
               n->x, n->y, colors[n->type]);
        printf("  <text x='%.2f' y='%.2f' dy='-12' fill='#fff' font-family='monospace' font-size='10' text-anchor='middle'>%d</text>\n", 
               n->x, n->y, n->id);
        printf("</g>\n");
    }
    printf("</svg>\n");
}

void _save_binary(GlobalState *S) {
    FILE *f = fopen("rafaelia_diamond.bin", "wb");
    if(f) {
        fwrite(S, sizeof(GlobalState), 1, f);
        fclose(f);
    }
}

int main() {
    static GlobalState S;
    _genesis(&S);
    _calc_fibonacci(&S);
    _calc_geometry(&S);
    _connect_matrix(&S);
    _render_svg(&S);
    _save_binary(&S);
    return 0;
}
