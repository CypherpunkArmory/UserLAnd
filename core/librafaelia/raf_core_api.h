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
#define RAFAELIA_BITSET_WORDS (((RAFAELIA_MAX_CPUS) + 63U) / 64U)

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

typedef struct {
  size_t count;
  uint64_t user[RAFAELIA_MAX_CPUS];
  uint64_t nice[RAFAELIA_MAX_CPUS];
  uint64_t system[RAFAELIA_MAX_CPUS];
  uint64_t idle[RAFAELIA_MAX_CPUS];
  uint64_t iowait[RAFAELIA_MAX_CPUS];
  uint64_t irq[RAFAELIA_MAX_CPUS];
  uint64_t softirq[RAFAELIA_MAX_CPUS];
  uint64_t steal[RAFAELIA_MAX_CPUS];
  uint64_t guest[RAFAELIA_MAX_CPUS];
  uint64_t guest_nice[RAFAELIA_MAX_CPUS];
} RafCpuSoa;

typedef struct {
  float utilization[RAFAELIA_MAX_CPUS];
  float iowait_ratio[RAFAELIA_MAX_CPUS];
  uint64_t hot_flags[RAFAELIA_BITSET_WORDS];
} RafCpuMetrics;

static inline void raf_bitset_clear(uint64_t bits[RAFAELIA_BITSET_WORDS]) {
  for (size_t i = 0; i < RAFAELIA_BITSET_WORDS; ++i) {
    bits[i] = 0ULL;
  }
}

static inline void raf_bitset_set(uint64_t bits[RAFAELIA_BITSET_WORDS], size_t idx) {
  const size_t word = idx / 64U;
  const size_t shift = idx % 64U;
  if (word < RAFAELIA_BITSET_WORDS) {
    bits[word] |= (1ULL << shift);
  }
}

int raf_parse_proc_stat(FILE *stream, RafCpuSnapshot *out);
int raf_export_cpu_json(const RafCpuSnapshot *snapshot, char *buffer, size_t buffer_size);
int raf_build_cpu_soa(const RafCpuSnapshot *snapshot, RafCpuSoa *out);
int raf_compute_cpu_metrics(const RafCpuSoa *prev,
                            const RafCpuSoa *curr,
                            RafCpuMetrics *out,
                            float hot_threshold);

#ifdef __cplusplus
}
#endif

#endif  // RAFAELIA_CORE_API_H
