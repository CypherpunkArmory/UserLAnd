#ifndef RAFAELIA_CORE_API_H
#define RAFAELIA_CORE_API_H

#include <stddef.h>
#include <stdint.h>
#include <stdio.h>

#ifdef __cplusplus
extern "C" {
#endif

#define RAFAELIA_MAX_CPUS 256
#define RAFAELIA_CPU_FIELDS 10

static const char *const RAFAELIA_CPU_FIELD_NAMES[RAFAELIA_CPU_FIELDS] = {
    "user", "nice", "system", "idle", "iowait",
    "irq",  "softirq", "steal", "guest", "guest_nice"};

typedef struct {
  char id[16];
  uint64_t fields[RAFAELIA_CPU_FIELDS];
  size_t field_count;
} RafCpuSample;

typedef struct {
  uint64_t timestamp_ms;
  RafCpuSample samples[RAFAELIA_MAX_CPUS];
  size_t sample_count;
} RafCpuSnapshot;

int raf_parse_proc_stat(FILE *stream, RafCpuSnapshot *out);
int raf_export_cpu_json(const RafCpuSnapshot *snapshot, char *buffer, size_t buffer_size);

#ifdef __cplusplus
}
#endif

#endif  // RAFAELIA_CORE_API_H
