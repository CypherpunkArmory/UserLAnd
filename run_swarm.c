#define _POSIX_C_SOURCE 200809L

#include <errno.h>
#include <fcntl.h>
#include <signal.h>
#include <stdarg.h>
#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/stat.h>
#include <sys/types.h>
#include <sys/wait.h>
#include <time.h>
#include <unistd.h>

#define MAX_WORKERS 12
#define TOTAL_JOBS 1000000
#define TARGET_PETA 1000000.0
#define STATUS_INTERVAL 5
#define LEDGER_PATH "swarm_ledger.dat"
#define WORKER_PATH "./swarm_worker"
#define SMALL_SLEEP_NS 100000000L

/*
 * Matrix-style orchestrator: launches bounded batches of swarm_worker processes
 * without relying on shell utilities. Designed for low-level environments where
 * process control, status accounting, and I/O must remain self-contained.
 * Mitigations:
 * - Uses non-blocking waitpid to avoid zombie accumulation.
 * - Validates child creation/exec and surfaces failures immediately.
 * - Falls back to nanosleep pacing when waitpid finds no completed children,
 *   preventing busy-wait loops under heavy load.
 */

static void matrix_die(const char *fmt, ...) {
    va_list ap;
    va_start(ap, fmt);
    vfprintf(stderr, fmt, ap);
    va_end(ap);
    fputc('\n', stderr);
    _exit(1);
}

/*
 * Sums the ledger to mirror the shell-based awk path. Reads as double values to
 * keep interoperability with existing worker output while staying robust to
 * partial lines by ignoring parse failures.
 */
static double matrix_sum_ledger(void) {
    FILE *file = fopen(LEDGER_PATH, "r");
    if (!file) {
        return 0.0;
    }

    double total = 0.0;
    while (1) {
        double value = 0.0;
        int rc = fscanf(file, "%lf", &value);
        if (rc == 1) {
            total += value;
        } else if (rc == EOF) {
            break;
        } else {
            int ch;
            do {
                ch = fgetc(file);
            } while (ch != '\n' && ch != EOF);
        }
    }

    fclose(file);
    return total;
}

/*
 * Prints progress with ANSI sequences, mirroring the original shell UX. Keeps
 * messages compact to reduce stdout contention when many jobs are active.
 */
static void matrix_render_status(int job_id, int active, double total_stack) {
    double perc = (TARGET_PETA > 0.0) ? (total_stack / TARGET_PETA) * 100.0 : 0.0;
    printf("\033[2K\r");
    printf("\x1b[33m[PILHA]\x1b[0m Total: \x1b[36m%.1f GigaFlops\x1b[0m acumulados (%.4f%% do Peta)\n", total_stack, perc);
    printf("\x1b[31m[HEAT]\x1b[0m Processos Simult\303\242neos: %d | Processo Atual: #%d\n", active, job_id);
    printf("-------------------------------------------\n");
    printf("\033[3A");
    fflush(stdout);
}

/*
 * Attempts to reap finished children without blocking. Returns the number of
 * active workers remaining after reaping to maintain an accurate concurrency
 * gate. Protects against PID leaks when workers terminate quickly.
 */
static int matrix_reap(pid_t *pids, int active) {
    int alive = 0;
    for (int i = 0; i < active; ++i) {
        int status = 0;
        pid_t result = waitpid(pids[i], &status, WNOHANG);
        if (result == 0) {
            pids[alive++] = pids[i];
        }
    }
    return alive;
}

/*
 * Launches a new swarm_worker instance with the provided identifier. Ensures
 * argv formatting is stable and does not depend on locale-specific formatting.
 */
static pid_t matrix_launch_worker(int id) {
    pid_t pid = fork();
    if (pid < 0) {
        matrix_die("[run_swarm] fork failed for job %d: %s", id, strerror(errno));
    }

    if (pid == 0) {
        char id_buf[32];
        snprintf(id_buf, sizeof(id_buf), "%d", id);
        char *argv[] = { (char *)WORKER_PATH, id_buf, NULL };
        execv(WORKER_PATH, argv);
        matrix_die("[run_swarm] exec failed for job %d: %s", id, strerror(errno));
    }

    return pid;
}

int main(void) {
    pid_t pids[MAX_WORKERS];
    int active = 0;

    printf("\033[2J\033[H");
    printf("\x1b[32m=== RAFAELIA SWARM: PROCESS STACKER (C) ===\x1b[0m\n");
    printf("Target: EMPILHAR AT\303\211 1 PETAFLOP\n");
    printf("Worker Load: ~2.7 GigaFlops por processo\n");
    printf("-------------------------------------------\n");
    fflush(stdout);

    for (int job = 1; job <= TOTAL_JOBS; ++job) {
        while (active >= MAX_WORKERS) {
            int new_active = matrix_reap(pids, active);
            if (new_active == active) {
                struct timespec ts = {0, SMALL_SLEEP_NS};
                nanosleep(&ts, NULL);
            }
            active = new_active;
        }

        active = matrix_reap(pids, active);
        pids[active++] = matrix_launch_worker(job);

        printf("\r\360\237\237\200 Lançando Oper\303\241rio #%d \t [Ativos: %d]", job, active);
        fflush(stdout);

        if (job % STATUS_INTERVAL == 0) {
            double total_stack = matrix_sum_ledger();
            matrix_render_status(job, active, total_stack);
        }
    }

    while (active > 0) {
        int new_active = matrix_reap(pids, active);
        if (new_active == active) {
            struct timespec ts = {0, SMALL_SLEEP_NS};
            nanosleep(&ts, NULL);
        }
        active = new_active;
    }

    double final_total = matrix_sum_ledger();
    printf("\033[2K\r");
    printf("\n\x1b[32m[PILHA]\x1b[0m Final: \x1b[36m%.1f GigaFlops\x1b[0m acumulados.\n", final_total);
    printf("\x1b[32m[SWARM]\x1b[0m Todos os processos encerrados.\n");
    return 0;
}
