#include "gpcu_Version3.h"
#include <stdio.h>

int main(void) {
    // Programa: inicializa R0, faz dois HASH e HALT:
    // (na prática você setaria regs antes de rodar)
    const char *prog = "N#0#0H";

    GPCU_PCB *p = gpcu_create_process(prog);
    if (!p) {
        printf("Falha ao criar processo.\n");
        return 1;
    }

    // Inicializa R0 com um valor conhecido
    p->regs[0] = 42;

    while (p->state != PROC_STATE_HALTED &&
           p->state != PROC_STATE_ERROR) {
        gpcu_step(p);
    }

    gpcu_dump_state(p);

    gpcu_destroy_process(p);
    return 0;
}
