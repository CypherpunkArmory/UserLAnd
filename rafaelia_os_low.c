// RAFAELIA COEXISTENCE OS (LOW LEVEL)
// Kernel + micro-OS demo construído no mesmo estilo do motor ZIPRAF.
// O objetivo é mostrar um ciclo completo: header blindado, hooks lineares,
// ref sheets pré-calculadas e validação CRC a cada salto O(N).

#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <time.h>

#define RAF_MAGIC     0x52414641u // "RAFA"
#define RAF_MAX_TASKS 64u
#define RAF_TAG_BOOT  (1u << 0)
#define RAF_TAG_SEAL  (1u << 1)
#define RAF_TAG_HINT  (1u << 2)

typedef struct {
  uint32_t magic;       // assinatura
  uint32_t crc_payload; // selo interno
  uint32_t crc_header;  // selo do selo
  uint64_t timestamp;   // tempo de emissão
  uint32_t flags;       // tags ZIPRAF
} RafHeader;

typedef struct {
  uint32_t magic;
  uint32_t crc_payload;
  uint64_t timestamp;
  uint32_t flags;
} RafHeaderStamp;

// Cada tarefa é um "mundo" com solução já conhecida.
typedef struct RafTask {
  RafHeader header;
  char      name[16];       // identificação amigável
  uint64_t  ref_sheet;      // resposta pré-computada
  uint32_t  hook_next;      // próxima tarefa (linear)
  uint32_t  hint_skip;      // salto curto
  uint32_t  cycles;         // quantas vezes foi tocada
} RafTask;

typedef struct {
  RafHeader header;
  RafTask   tasks[RAF_MAX_TASKS];
  uint32_t  task_count;
  uint64_t  solution_sum;
} RafKernel;

static uint32_t raf_crc32(const void *data, size_t len) {
  const uint8_t *p = (const uint8_t *)data;
  uint32_t crc = 0xFFFFFFFFu;
  while (len--) {
    crc ^= *p++;
    for (int k = 0; k < 8; ++k) {
      crc = (crc >> 1) ^ (0xEDB88320u & (-(int32_t)(crc & 1u)));
    }
  }
  return ~crc;
}

static void raf_seal_header(RafHeader *hdr, const void *payload, size_t payload_len) {
  hdr->crc_payload = raf_crc32(payload, payload_len);
  const RafHeaderStamp stamp = {hdr->magic, hdr->crc_payload, hdr->timestamp, hdr->flags};
  hdr->crc_header = raf_crc32(&stamp, sizeof(stamp));
}

static void raf_seed_task(RafTask *task, const char *name, uint32_t next, uint32_t hint, uint64_t base) {
  memset(task, 0, sizeof(*task));
  task->header.magic = RAF_MAGIC;
  task->header.flags = RAF_TAG_SEAL | (hint ? RAF_TAG_HINT : 0u);
  task->header.timestamp = (uint64_t)time(NULL);
  snprintf(task->name, sizeof(task->name), "%s", name);
  task->ref_sheet = base * 137u + 42u; // solução pré-calculada
  task->hook_next = next;
  task->hint_skip = hint;
  raf_seal_header(&task->header, task->name, sizeof(task->name));
}

static void raf_boot_kernel(RafKernel *kernel, uint32_t task_count) {
  memset(kernel, 0, sizeof(*kernel));
  kernel->header.magic = RAF_MAGIC;
  kernel->header.flags = RAF_TAG_BOOT | RAF_TAG_SEAL;
  kernel->header.timestamp = (uint64_t)time(NULL);
  kernel->task_count = task_count > RAF_MAX_TASKS ? RAF_MAX_TASKS : task_count;

  for (uint32_t i = 0; i < kernel->task_count; ++i) {
    // Hint: a cada 7 tarefas, criamos um salto curto (scan O(N))
    uint32_t hint = (i + 7u < kernel->task_count) ? (i + 7u) : 0u;
    raf_seed_task(&kernel->tasks[i], "ZIPRAF-T", i + 1u, hint, (uint64_t)(i + 1u));
  }

  raf_seal_header(&kernel->header, kernel->tasks, sizeof(RafTask) * kernel->task_count);
}

static int raf_validate(const RafHeader *hdr, const void *payload, size_t len) {
  if (hdr->magic != RAF_MAGIC) return 0;
  if (hdr->crc_payload != raf_crc32(payload, len)) return 0;
  const RafHeaderStamp stamp = {hdr->magic, hdr->crc_payload, hdr->timestamp, hdr->flags};
  if (hdr->crc_header != raf_crc32(&stamp, sizeof(stamp))) return 0;
  return 1;
}

static void raf_scheduler(RafKernel *kernel) {
  printf("[PROCESS] Iniciando resolução linear (LOW LEVEL)...\n\n");
  uint32_t idx = 0u;
  uint32_t steps = 0u;

  while (idx < kernel->task_count) {
    RafTask *t = &kernel->tasks[idx];
    if (!raf_validate(&t->header, t->name, sizeof(t->name))) {
      printf("  ! Integridade quebrada em %s (idx %u)\n", t->name, idx);
      break;
    }

    kernel->solution_sum += t->ref_sheet;
    t->cycles++;
    steps++;

    // O.S.: preferir salto hint se existir, senão seguir linear
    if (t->hint_skip && t->hint_skip < kernel->task_count) {
      idx = t->hint_skip;
    } else if (t->hook_next < kernel->task_count) {
      idx = t->hook_next;
    } else {
      break;
    }
  }

  printf("⚡ RESULTADO FINAL ATINGIDO (LOW LEVEL)\n");
  printf("   Passos Lineares : %u\n", steps);
  printf("   Solução (Soma)  : %llu\n", (unsigned long long)kernel->solution_sum);
}

int main(void) {
  printf("\033[2J\033[H");
  printf("🌌 Iniciando RAFAELIA COEXISTENCE ENGINE (LOW LEVEL)...\n");
  printf("🔥 Compilando kernel/OS ZIPRAF...\n");
  printf("🚀 Executando RAFAELIA COEXISTENCE ENGINE (LOW LEVEL)...\n");
  printf("=== RAFAELIA COEXISTENCE OS (ZIPRAF LOW LEVEL) ===\n");
  printf("Lógica: Headers CRC + Hooks + Ref Sheets\n");
  printf("Meta  : Complexidade N^N resolvida com scan O(N)\n\n");

  RafKernel kernel;
  raf_boot_kernel(&kernel, 32u);
  raf_scheduler(&kernel);

  printf("\n┌─[zipraf@kernel]──[~/os] (main)\n");
  return 0;
}
