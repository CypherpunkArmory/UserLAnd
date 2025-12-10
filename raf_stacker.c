/* RAFAELIA PETA STACKER
 * Logic: Cumulative Matrix Multiplication + Disk Persistence
 * Goal: Stack operations until 1.0 PetaFlop is reached.
 */
#include <stdio.h>
#include <stdlib.h>
#include <time.h>
#include <omp.h>
#include <unistd.h>

#define N 1024
#define SAVE_FILE "peta_stack.dat"

double get_time() {
    struct timespec ts;
    clock_gettime(CLOCK_MONOTONIC, &ts);
    return ts.tv_sec + ts.tv_nsec * 1e-9;
}

// Carregar progresso anterior
double load_stack() {
    FILE *f = fopen(SAVE_FILE, "rb");
    double stack = 0.0;
    if (f) {
        fread(&stack, sizeof(double), 1, f);
        fclose(f);
        printf("[SYSTEM] Pilha anterior encontrada: %.12f PETA\n", stack / 1.0e15);
    }
    return stack;
}

// Salvar progresso atual
void save_stack(double current_stack) {
    FILE *f = fopen(SAVE_FILE, "wb");
    if (f) {
        fwrite(&current_stack, sizeof(double), 1, f);
        fclose(f);
    }
}

int main() {
    double *A = (double*)malloc(N*N*sizeof(double));
    double *B = (double*)malloc(N*N*sizeof(double));
    double *C = (double*)malloc(N*N*sizeof(double));
    
    // Inicialização da Matriz (O "Tijolo")
    #pragma omp parallel for
    for(int i=0; i<N*N; i++) { A[i] = 1.0001; B[i] = 2.0002; }

    double total_ops_stacked = load_stack();
    double target_peta = 1.0e15; // 1 Quatrilhão de Operações
    
    printf("\033[2J\033[H");
    printf("=== RAFAELIA PETA STACKER (8 CORES) ===\n");
    printf("Hardware: Unisoc T612 (Realme)\n");
    printf("Target  : 1.0 PETAFLOP (Stacked)\n\n");

    while(total_ops_stacked < target_peta) {
        double start = get_time();
        
        // --- O TRABALHO PESADO (DGEMM) ---
        // Empilhando calor e matemática
        #pragma omp parallel for
        for(int i=0; i<N; i++) {
            for(int k=0; k<N; k++) {
                double r = A[i*N+k];
                for(int j=0; j<N; j++) {
                    C[i*N+j] += r * B[k*N+j];
                }
            }
        }
        
        double end = get_time();
        double dt = end - start;
        
        // Matemática do Bloco
        double ops_block = 2.0 * N * N * N; // ~2.1 Bilhões de ops por loop
        double current_gflops = (ops_block * 1e-9) / dt;
        
        // Empilhando
        total_ops_stacked += ops_block;
        
        // Salvando no Disco (Persistência)
        save_stack(total_ops_stacked);
        
        // Visualização
        double percent = (total_ops_stacked / target_peta) * 100.0;
        double peta_view = total_ops_stacked / 1.0e15;
        double tera_view = total_ops_stacked / 1.0e12;
        
        printf("\r\033[K"); // Limpa linha
        printf("SPEED: \033[1;33m%.2f GFLOPS\033[0m | STACK: \033[1;32m%.9f PETA\033[0m (%.4f%%)", 
               current_gflops, peta_view, percent);
        printf("\n\r\033[K   └─> Tijolos (Tera): %.4f T | Calor: ON", tera_view);
        printf("\033[A"); // Sobe cursor para redesenhar
        
        fflush(stdout);
    }
    
    printf("\n\n\n🏆 PETAFLOP ALCANÇADO! A PIRÂMIDE ESTÁ COMPLETA.\n");
    return 0;
}
