#define _POSIX_C_SOURCE 199309L

#include <stdio.h>
#include <stdlib.h>
#include <time.h>
#include <omp.h>
#include <arm_neon.h> /* A Mágica do Hardware ARM */

#define N 2048  /* Matriz Gigante para garantir cache pressure */
#define BLOCK_SIZE 32
#define SAVE_FILE "peta_neon.dat"

double get_time() {
    struct timespec ts;
    clock_gettime(CLOCK_MONOTONIC, &ts);
    return ts.tv_sec + ts.tv_nsec * 1e-9;
}

// Carregar Pilha
double load_stack() {
    FILE *f = fopen(SAVE_FILE, "rb");
    double stack = 0.0;
    if (f) {
        fread(&stack, sizeof(double), 1, f);
        fclose(f);
    }
    return stack;
}

// Salvar Pilha
void save_stack(double current_stack) {
    FILE *f = fopen(SAVE_FILE, "wb");
    if (f) {
        fwrite(&current_stack, sizeof(double), 1, f);
        fclose(f);
    }
}

int main() {
    // Alocação Alinhada (Fundamental para NEON)
    float *A, *B, *C;
    posix_memalign((void**)&A, 128, N*N*sizeof(float));
    posix_memalign((void**)&B, 128, N*N*sizeof(float));
    posix_memalign((void**)&C, 128, N*N*sizeof(float));

    // Inicialização Paralela
    #pragma omp parallel for
    for(int i=0; i<N*N; i++) {
        A[i] = 1.001f; B[i] = 2.002f; C[i] = 0.0f;
    }

    double total_ops_stacked = load_stack();
    double target_peta = 1.0e15;

    printf("\033[2J\033[H");
    printf("=== RAFAELIA NEON STACKER (8 CORES x 4 VETORES) ===\n");
    printf("Modo: SIMD (Single Instruction Multiple Data)\n");
    printf("Target: PETAFLOP Acumulado\n\n");

    while(total_ops_stacked < target_peta) {
        double start = get_time();

        // --- NÚCLEO VETORIAL NEON ---
        // Aqui está o que você pediu: Um processo fazendo dezenas de cálculos.
        // O loop desenrolado processa BLOCOS inteiros de uma vez.
        
        #pragma omp parallel for collapse(2)
        for (int i = 0; i < N; i++) {
            for (int k = 0; k < N; k++) {
                // Carrega 1 valor de A e duplica para um vetor de 4 posições
                // Ex: A[i,k] = 5.0 -> {5.0, 5.0, 5.0, 5.0}
                float32x4_t vec_a = vdupq_n_f32(A[i * N + k]);

                // Percorre B e C em saltos de 4 (Vetores)
                // O processador faz 4 multiplicações e 4 somas A CADA CICLO aqui
                for (int j = 0; j < N; j += 4) {
                    // Carrega 4 floats de B e C
                    float32x4_t vec_b = vld1q_f32(&B[k * N + j]);
                    float32x4_t vec_c = vld1q_f32(&C[i * N + j]);

                    // FMA (Fused Multiply Add): C = C + (A * B)
                    // Faz tudo junto no hardware em 1 ciclo
                    vec_c = vmlaq_f32(vec_c, vec_a, vec_b);

                    // Salva o resultado de volta
                    vst1q_f32(&C[i * N + j], vec_c);
                }
            }
        }

        double end = get_time();
        double dt = end - start;

        // Matemática: 2 ops (mul+add) * N^3
        double ops_block = 2.0 * (double)N * (double)N * (double)N; 
        double gflops = (ops_block * 1e-9) / dt;
        
        total_ops_stacked += ops_block;
        save_stack(total_ops_stacked);

        double peta_view = total_ops_stacked / 1.0e15;
        double tera_view = total_ops_stacked / 1.0e12;

        printf("\r\033[K");
        // Mostra GFLOPS Reais (Velocidade) e TERA Acumulado (Volume)
        printf("VELOCIDADE: \033[1;33m%.2f GFLOPS\033[0m (NEON) | PILHA: \033[1;36m%.6f PETA\033[0m", 
               gflops, peta_view);
        printf("\n\r\033[K   └─> Tijolos: %.4f TERA | 8 Cores a 100%%", tera_view);
        printf("\033[A");
        
        fflush(stdout);
    }

    printf("\n\n🏆 PETAFLOP ATINGIDO!\n");
    return 0;
}
