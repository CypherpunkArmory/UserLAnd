#define _POSIX_C_SOURCE 199309L

/* RAFAELIA FLOP MAXIMIZER
 * Target: ARMv8 NEON + OpenMP (8 Cores)
 * Logic: Raw Matrix Multiplication (C = A * B)
 */
#include <stdio.h>
#include <stdlib.h>
#include <time.h>
#include <omp.h> // Multithreading Real

#define N 1024 // Tamanho da Matriz (1024x1024)
// Total de Operações por ciclo: 2 * N^3
// Para N=1024 -> 2.1 Bilhões de Operações (2 GigaOps) por passo.

double get_time() {
    struct timespec ts;
    clock_gettime(CLOCK_MONOTONIC, &ts);
    return ts.tv_sec + ts.tv_nsec * 1e-9;
}

int main() {
    // Alocação de Memória Real
    double *A = (double*)malloc(N*N*sizeof(double));
    double *B = (double*)malloc(N*N*sizeof(double));
    double *C = (double*)malloc(N*N*sizeof(double));
    
    // Inicialização (Para garantir que a CPU não otimize cortando zeros)
    #pragma omp parallel for
    for(int i=0; i<N*N; i++) { A[i] = 1.0; B[i] = 2.0; C[i] = 0.0; }

    printf("\033[2J\033[H"); // Limpa tela
    printf("=== RAFAELIA FLOP MAXIMIZER (8 CORES) ===\n");
    printf("Matrix: %dx%d (Double Precision)\n", N, N);
    printf("Target: MAX GFLOPS -> ACCUMULATING TERA/PETA\n\n");

    double total_ops = 0;
    double start_global = get_time();
    
    while(1) {
        double start = get_time();
        
        // O CORAÇÃO DO CÁLCULO (MULTIPLICAÇÃO DE MATRIZES)
        // Isso queima a FPU (Floating Point Unit)
        #pragma omp parallel for
        for(int i=0; i<N; i++) {
            for(int k=0; k<N; k++) {
                double temp = A[i*N+k];
                for(int j=0; j<N; j++) {
                    C[i*N+j] += temp * B[k*N+j];
                }
            }
        }
        
        double end = get_time();
        double elapsed = end - start;
        
        // Matemática Real
        double ops_pass = 2.0 * N * N * N; // 2 * 1024^3
        total_ops += ops_pass;
        double gflops = (ops_pass * 1e-9) / elapsed;
        
        // Conversões para exibição
        double tera_total = total_ops * 1e-12;
        double peta_total = total_ops * 1e-15;
        
        // Dashboard
        printf("\r\033[K"); // Limpa linha
        printf("SPEED: \033[1;33m%.2f GFLOPS\033[0m | TOTAL: \033[1;36m%.4f TERA-OPs\033[0m", 
               gflops, tera_total);
        
        // Se bater 1 Peta (Isso vai demorar dias, mas é o alvo)
        if (peta_total >= 1.0) {
            printf("\n\n🏆 PETAFLOP ACCUMULATED! (Tempo: %.1fs)\n", end - start_global);
            break;
        }
        
        fflush(stdout);
    }
    
    return 0;
}
