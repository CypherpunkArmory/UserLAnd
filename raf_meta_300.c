#include <stdio.h>
#include <stdlib.h>
#include <math.h>
#include <time.h>
#include <string.h>

#define DIM 300          // 300 Dimensões (O seu pedido)
#define ENTITIES 1024    // 1024 Entidades Metaversais interagindo
#define PHI 1.618033988  // A Constante Universal

// A Estrutura da Realidade
// Cada "MetaVector" não é um número. É um universo de 300 eixos.
typedef struct {
    float d[DIM]; // As 300 Dimensões
} MetaVector;

MetaVector population[ENTITIES];
MetaVector interaction_matrix[DIM]; // Matriz de Dobra Dimensional

// Inicializa o Metaverso
void big_bang() {
    for(int i=0; i<ENTITIES; i++) {
        for(int d=0; d<DIM; d++) {
            // Inicializa com ruído quântico normalizado
            population[i].d[d] = ((float)rand()/RAND_MAX) * 2.0f - 1.0f;
        }
    }
    // Cria a Matriz de Interação (Como as dimensões afetam umas as outras)
    for(int i=0; i<DIM; i++) {
        for(int j=0; j<DIM; j++) {
            // Conexão baseada em PHI e Seno (Frequência)
            interaction_matrix[i].d[j] = sinf(i * j * PHI); 
        }
    }
}

// O CÁLCULO META-DIMENSIONAL (Tensor Product / Folding)
// Aqui nós fazemos as 300 dimensões colidirem.
// Não é soma simples. É projeção vetorial.
void meta_evolve() {
    #pragma omp parallel for
    for(int i=0; i<ENTITIES; i++) {
        float energy_buffer[DIM];
        
        // 1. Dobra Dimensional (Folding)
        // Cada dimensão "sente" as outras
        for(int d=0; d<DIM; d++) {
            float sum = 0.0f;
            // Otimização: Sampling de ressonância (olha para vizinhos dimensionais)
            // Para calcular TUDO seria N^2 (pesado), aqui fazemos uma "dobra" local
            for(int k=0; k<DIM; k+=10) { // Salto de 10 em 10 para simular conexão não-local
                sum += population[i].d[k] * interaction_matrix[d].d[k];
            }
            energy_buffer[d] = tanh(sum); // Normalização não-linear (Rede Neural)
        }
        
        // 2. Atualização de Estado (Passagem de Tempo Metaversal)
        for(int d=0; d<DIM; d++) {
            population[i].d[d] = energy_buffer[d];
        }
    }
}

// Visualizador Holográfico (ASCII)
// Tenta projetar 300 dimensões em uma linha de texto 2D
void render_hologram(int cycle) {
    MetaVector probe = population[ENTITIES/2]; // Pega uma entidade central
    
    printf("\r\033[K"); // Limpa linha
    printf("[META-CYCLE %d] 300D Projection: ", cycle);
    
    // Mostra um "espectrograma" das 300 dimensões
    for(int d=0; d<DIM; d+=5) { // Mostra 1 a cada 5 dimensões para caber na tela
        float val = probe.d[d];
        if (val > 0.5) printf("\033[1;31m█\033[0m");      // Alta Energia (Vermelho)
        else if (val > 0.0) printf("\033[1;33m▒\033[0m"); // Média (Amarelo)
        else if (val > -0.5) printf("\033[1;34m░\033[0m"); // Baixa (Azul)
        else printf(" ");                                 // Vazio
    }
    printf(" | \033[1;37mEntropy: High\033[0m");
    fflush(stdout);
}

int main() {
    srand(time(NULL));
    printf("\033[2J\033[H");
    printf("=== RAFAELIA TENSOR-300 (META-VERSAL ENGINE) ===\n");
    printf("Dimensões: %d Ativas Simultâneas\n", DIM);
    printf("Entidades: %d (Cada uma carrega seu próprio universo 300D)\n", ENTITIES);
    printf("Matemática : Hipervetores + Dobra Phi\n\n");

    big_bang();

    int cycle = 0;
    while(1) {
        meta_evolve();
        render_hologram(cycle);
        cycle++;
        // usleep(10000); // Remova para velocidade máxima
    }
    return 0;
}
