#include <stdio.h>
#include <stdlib.h>
#include <complex.h> /* Matemática Nativa de Números Complexos */
#include <time.h>
#include <omp.h>

#define N 1024  /* Dimensão da Matriz (1024x1024 = 1 Milhão de Células Complexas) */
                /* Total de operações: ~8 Bilhões por ciclo (devido à complexidade) */

// O Processador conta Ciclos de Clock Reais
static inline uint64_t rdtsc() {
    uint64_t val;
    asm volatile("mrs %0, cntvct_el0" : "=r" (val));
    return val;
}

int main() {
    // Alocação de Matrizes Complexas (Z = X + iY)
    double complex *Matriz_A = malloc(N * N * sizeof(double complex));
    double complex *Matriz_B = malloc(N * N * sizeof(double complex));
    double complex *Matriz_Res = malloc(N * N * sizeof(double complex));

    printf("\033[2J\033[H");
    printf("=== RAFAELIA COMPLEX TENSOR SOLVER ===\n");
    printf("Dimensão: %dx%d (Complex Double Precision)\n", N, N);
    printf("Entrada : Gerando Caos Multidimensional...\n");

    // 1. Geração de Entradas (O Caos Inicial)
    // Preenchemos com números aleatórios complexos
    #pragma omp parallel for
    for(int i=0; i<N*N; i++) {
        double real = (double)rand() / RAND_MAX;
        double imag = (double)rand() / RAND_MAX;
        Matriz_A[i] = real + imag * I;
        
        real = (double)rand() / RAND_MAX;
        imag = (double)rand() / RAND_MAX;
        Matriz_B[i] = real + imag * I;
    }

    printf("Status  : Matrizes Carregadas. Iniciando Colisão...\n");
    printf("---------------------------------------------------\n");

    // 2. O CÁLCULO (O "Pá meu irmão")
    // Multiplicação de Matrizes Complexas Paralela
    // C[i,j] = Sum(A[i,k] * B[k,j])
    
    uint64_t start_cycles = rdtsc();
    double start_time = omp_get_wtime();

    #pragma omp parallel for collapse(2)
    for(int i=0; i<N; i++) {
        for(int j=0; j<N; j++) {
            double complex sum = 0.0 + 0.0 * I;
            // O Loop de Fogo: Contrai a dimensão K
            for(int k=0; k<N; k++) {
                sum += Matriz_A[i*N + k] * Matriz_B[k*N + j];
            }
            Matriz_Res[i*N + j] = sum;
        }
    }

    double end_time = omp_get_wtime();
    uint64_t end_cycles = rdtsc();
    
    // 3. A RESOLUÇÃO FINAL (Traço Espectral)
    // Reduzimos a matriz inteira a um único número complexo que define a solução.
    double complex Singularity = 0;
    for(int i=0; i<N; i++) {
        Singularity += Matriz_Res[i*N + i];
    }

    double dt = end_time - start_time;
    uint64_t cycles = end_cycles - start_cycles;
    
    // Estimativa de Operações: 
    // N^3 iterações * 8 ops (4 mul + 4 add por complexo)
    double ops = 8.0 * N * N * N;
    double gflops = (ops * 1e-9) / dt;

    printf("\n⚡ \033[1;32mRESULTADO OBTIDO!\033[0m\n");
    printf("   Solução (Z): \033[1;33m%.4f + %.4fi\033[0m\n", creal(Singularity), cimag(Singularity));
    printf("   Tempo Real : %.6f s\n", dt);
    printf("   Ciclos CPU : %lu\n", cycles);
    printf("   Velocidade : \033[1;36m%.2f GFLOPS\033[0m (Cálculo Real)\n", gflops);
    
    free(Matriz_A); free(Matriz_B); free(Matriz_Res);
    return 0;
}
