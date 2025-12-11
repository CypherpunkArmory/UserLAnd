/*
 * RAFAELIA :: GPCU V3 - General Purpose Computing Unit
 * Interface: gpcu_Version3.h
 *
 * Normas de referência:
 *  - IEEE 830  : Especificação de Software (estrutura e clareza da interface)
 *  - NIST 800-53: Integridade de Estado (controle via instrução HASH)
 */

#ifndef GPCU_VERSION3_H
#define GPCU_VERSION3_H

#include <stdint.h>
#include <stddef.h>

#ifdef __cplusplus
extern "C" {
#endif

// ---------------------------------------------------------------------------
// Definições de Arquitetura
// ---------------------------------------------------------------------------

// Número de registradores gerais (R0..R7)
#define GPCU_REGISTER_COUNT        8

// Tamanho de memória de código por processo (instruções ASCII)
#define GPCU_MEM_SIZE_PER_PROCESS  256

// Máximo de processos que podem ser criados (controle simples de PID)
#define GPCU_MAX_PROCESSES         16

// ---------------------------------------------------------------------------
// Estados de Processo
// ---------------------------------------------------------------------------

typedef enum {
    PROC_STATE_NEW = 0,
    PROC_STATE_READY,
    PROC_STATE_RUNNING,
    PROC_STATE_HALTED,
    PROC_STATE_ERROR
} GPCU_PROCESS_STATE;

// ---------------------------------------------------------------------------
// PCB - Process Control Block (Bloco de Controle de Processo)
// ---------------------------------------------------------------------------

typedef struct {
    uint32_t           pid;                           // ID do Processo
    GPCU_PROCESS_STATE state;                         // Estado atual
    uint32_t           pc;                            // Program Counter (próxima instrução)
    uint32_t           regs[GPCU_REGISTER_COUNT];     // Registradores (R0..R7)
    uint8_t            memory[GPCU_MEM_SIZE_PER_PROCESS]; // Memória de instruções ASCII
} GPCU_PCB;

// ---------------------------------------------------------------------------
// Interface Pública do GPCU V3
// ---------------------------------------------------------------------------

/**
 * Cria um processo GPCU a partir de uma sequência de instruções ASCII.
 *
 * - instructions: string de instruções (ex.: "N#0H")
 * Retorna:
 *  - ponteiro para GPCU_PCB alocado em heap, ou NULL em erro.
 */
GPCU_PCB* gpcu_create_process(const char* instructions);

/**
 * Executa um único passo de instrução (fetch/decode/execute).
 *
 * - pcb: processo alvo.
 * Regras:
 *  - Se state ∈ {NEW, READY} => executa e transita para RUNNING / HALTED / ERROR.
 *  - Se state ∈ {RUNNING} => continua execução normal.
 *  - Se state ∈ {HALTED, ERROR} => não faz nada.
 */
void gpcu_step(GPCU_PCB* pcb);

/**
 * Libera recursos associados ao processo.
 */
void gpcu_destroy_process(GPCU_PCB* pcb);

/**
 * Função opcional de debug: imprime estado do PCB (regs, pc, state).
 */
void gpcu_dump_state(const GPCU_PCB* pcb);

#ifdef __cplusplus
}
#endif

#endif /* GPCU_VERSION3_H */
