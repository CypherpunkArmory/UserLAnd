/*
 * RAFAELIA :: GPCU V3 - Implementação Core
 * Arquivo: gpcu_Version3.c
 *
 * Requisitos normativos:
 *  - NIST 800-53: Integridade de Estado via instrução HASH determinística.
 *  - ISO/IEC 27001: controle de integridade aplicado ao "estado cognitivo" (regs + PC).
 */

#include "gpcu_Version3.h"
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

// ---------------------------------------------------------------------------
// Constantes internas
// ---------------------------------------------------------------------------

// Constante prime para hash simples (inspirada em multiplicadores clássicos)
#define GPCU_PRIME 2654435761U

static uint32_t NEXT_PID = 1;

// ---------------------------------------------------------------------------
// Helpers internos
// ---------------------------------------------------------------------------

static uint8_t gpcu_fetch_byte(GPCU_PCB* pcb, int* ok) {
    if (!pcb || pcb->pc >= GPCU_MEM_SIZE_PER_PROCESS) {
        if (pcb) {
            pcb->state = PROC_STATE_HALTED;
        }
        if (ok) *ok = 0;
        return 0;
    }
    uint8_t b = pcb->memory[pcb->pc];
    pcb->pc++;
    if (ok) *ok = 1;
    return b;
}

// ---------------------------------------------------------------------------
// Criação / destruição de processo
// ---------------------------------------------------------------------------

GPCU_PCB* gpcu_create_process(const char* instructions) {
    if (!instructions) return NULL;
    if (NEXT_PID > GPCU_MAX_PROCESSES) return NULL;

    GPCU_PCB* pcb = (GPCU_PCB*)malloc(sizeof(GPCU_PCB));
    if (!pcb) return NULL;

    memset(pcb, 0, sizeof(GPCU_PCB));
    pcb->pid   = NEXT_PID++;
    pcb->state = PROC_STATE_NEW;
    pcb->pc    = 0;

    // Copia instruções (ASCII) para a memória do processo
    size_t len = strlen(instructions);
    if (len > GPCU_MEM_SIZE_PER_PROCESS) {
        len = GPCU_MEM_SIZE_PER_PROCESS;
    }
    memcpy(pcb->memory, instructions, len);

    // Estado inicial: pronto para rodar
    pcb->state = PROC_STATE_READY;
    return pcb;
}

void gpcu_destroy_process(GPCU_PCB* pcb) {
    if (pcb) {
        free(pcb);
    }
}

// ---------------------------------------------------------------------------
// Instrução HASH (Validação de Estado Cognitivo)
// ---------------------------------------------------------------------------
//
// Semântica:
//   regs[reg_idx] <- (regs[reg_idx] * GPCU_PRIME) XOR (pc)
//   - Depende do valor anterior do registrador + PC atual.
//   - Determinístico: mesma sequência de instruções => mesmo hash.
//

static void gpcu_execute_hash(GPCU_PCB* pcb, int reg_idx) {
    if (!pcb) return;
    if (reg_idx < 0 || reg_idx >= GPCU_REGISTER_COUNT) {
        pcb->state = PROC_STATE_ERROR;
        return;
    }

    uint32_t current_val = pcb->regs[reg_idx];
    uint32_t new_hash    = (current_val * GPCU_PRIME) ^ pcb->pc;
    pcb->regs[reg_idx]   = new_hash;
}

// ---------------------------------------------------------------------------
// Dump de estado (debug / auditoria)
// ---------------------------------------------------------------------------

void gpcu_dump_state(const GPCU_PCB* pcb) {
    if (!pcb) {
        printf("[GPCU] (NULL PCB)\n");
        return;
    }

    const char* state_str = "UNKNOWN";
    switch (pcb->state) {
        case PROC_STATE_NEW:     state_str = "NEW";     break;
        case PROC_STATE_READY:   state_str = "READY";   break;
        case PROC_STATE_RUNNING: state_str = "RUNNING"; break;
        case PROC_STATE_HALTED:  state_str = "HALTED";  break;
        case PROC_STATE_ERROR:   state_str = "ERROR";   break;
        default: break;
    }

    printf("[GPCU] PID=%u STATE=%s PC=%u\n",
           pcb->pid, state_str, pcb->pc);

    for (int i = 0; i < GPCU_REGISTER_COUNT; ++i) {
        printf("  R%d = 0x%08X (%u)\n", i, pcb->regs[i], pcb->regs[i]);
    }
}

// ---------------------------------------------------------------------------
// Decodificação / Execução de instruções
// ---------------------------------------------------------------------------
//
// Formato simplificado de instruções (1 byte de opcode + 0..2 operandos ASCII):
//
//  'N'             -> NOP
//  'H'             -> HALT
//  'M' <d> <s>     -> MOV R<d> <- R<s>
//  'A' <d> <s>     -> ADD R<d> <- R<d> + R<s>
//  '#' <r>         -> HASH R<r>
//
// Onde <d>, <s>, <r> ∈ {'0'..'7'}.
//

void gpcu_step(GPCU_PCB* pcb) {
    if (!pcb) return;

    // Se já terminou ou está em erro, não faz nada
    if (pcb->state == PROC_STATE_HALTED ||
        pcb->state == PROC_STATE_ERROR) {
        return;
    }

    // Transição para RUNNING se estava NEW/READY
    if (pcb->state == PROC_STATE_NEW || pcb->state == PROC_STATE_READY) {
        pcb->state = PROC_STATE_RUNNING;
    }

    int ok = 0;
    uint8_t opcode = gpcu_fetch_byte(pcb, &ok);
    if (!ok) {
        // PC saiu da memória; já foi marcado como HALTED em gpcu_fetch_byte
        return;
    }

    switch (opcode) {
        case 'N':  // NOP
            // Não faz nada
            break;

        case 'H':  // HALT
            pcb->state = PROC_STATE_HALTED;
            break;

        case 'M': { // MOV R<d> <- R<s>
            int ok1 = 0, ok2 = 0;
            uint8_t d_ch = gpcu_fetch_byte(pcb, &ok1);
            uint8_t s_ch = gpcu_fetch_byte(pcb, &ok2);
            if (!ok1 || !ok2) {
                pcb->state = PROC_STATE_ERROR;
                break;
            }
            int r_dest = (int)(d_ch - '0');
            int r_src  = (int)(s_ch - '0');
            if (r_dest < 0 || r_dest >= GPCU_REGISTER_COUNT ||
                r_src  < 0 || r_src  >= GPCU_REGISTER_COUNT) {
                pcb->state = PROC_STATE_ERROR;
                break;
            }
            pcb->regs[r_dest] = pcb->regs[r_src];
            break;
        }

        case 'A': { // ADD R<d> <- R<d> + R<s>
            int ok1 = 0, ok2 = 0;
            uint8_t d_ch = gpcu_fetch_byte(pcb, &ok1);
            uint8_t s_ch = gpcu_fetch_byte(pcb, &ok2);
            if (!ok1 || !ok2) {
                pcb->state = PROC_STATE_ERROR;
                break;
            }
            int r_dest = (int)(d_ch - '0');
            int r_src  = (int)(s_ch - '0');
            if (r_dest < 0 || r_dest >= GPCU_REGISTER_COUNT ||
                r_src  < 0 || r_src  >= GPCU_REGISTER_COUNT) {
                pcb->state = PROC_STATE_ERROR;
                break;
            }
            pcb->regs[r_dest] += pcb->regs[r_src];
            break;
        }

        case '#': { // HASH R<r>
            int ok1 = 0;
            uint8_t r_ch = gpcu_fetch_byte(pcb, &ok1);
            if (!ok1) {
                pcb->state = PROC_STATE_ERROR;
                break;
            }
            int r_idx = (int)(r_ch - '0');
            gpcu_execute_hash(pcb, r_idx);
            break;
        }

        default:
            // Opcode inválido
            pcb->state = PROC_STATE_ERROR;
            break;
    }

    // Se PC sair da memória, marca HALTED (fim seguro de programa)
    if (pcb->pc >= GPCU_MEM_SIZE_PER_PROCESS &&
        pcb->state == PROC_STATE_RUNNING) {
        pcb->state = PROC_STATE_HALTED;
    }
}
