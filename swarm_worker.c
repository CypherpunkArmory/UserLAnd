#include <stdio.h>
#include <stdlib.h>
#include <math.h>

#define N 512 
// Matriz menor para o processo ser rápido e leve.
// Objetivo: Ciclos rápidos de nascimento e morte de processos.

int main(int argc, char *argv[]) {
    int id = atoi(argv[1]);
    double *A = (double*)malloc(N*N*sizeof(double));
    double *B = (double*)malloc(N*N*sizeof(double));
    double *C = (double*)malloc(N*N*sizeof(double));
    
    // Inicializa
    for(int i=0; i<N*N; i++) { A[i] = 1.001; B[i] = 2.002; C[i] = 0.0; }
    
    // O TRABALHO (Matemática Pesada)
    // 2 * N^3 operações = 2 * 134 Milhões = ~0.27 GigaFlops por Job
    // Repetimos 10 vezes internamente para dar um "peso" de ~2.7 GigaFlops
    
    for(int loop=0; loop<10; loop++) {
        for(int i=0; i<N; i++) {
            for(int k=0; k<N; k++) {
                double temp = A[i*N+k];
                for(int j=0; j<N; j++) {
                    C[i*N+j] += temp * B[k*N+j];
                }
            }
        }
    }
    
    // Entrega o resultado (2.7 Gigaflops entregues)
    FILE *f = fopen("swarm_ledger.dat", "a");
    if(f) {
        fprintf(f, "2.7\n"); // Adiciona na pilha
        fclose(f);
    }
    
    free(A); free(B); free(C);
    return 0;
}
