#define _POSIX_C_SOURCE 200809L

#include "raf_core_api.h"

#include <errno.h>

static inline uint64_t raf_cpu_field_or_zero(const RafCpuSample *s, size_t idx) {
  return idx < s->field_count ? s->fields[idx] : 0ULL;
}

int raf_build_cpu_soa(const RafCpuSnapshot *snapshot, RafCpuSoa *out) {
  if (snapshot == NULL || out == NULL) {
    errno = EINVAL;
    return -1;
  }

  const size_t count = snapshot->sample_count;
  if (count > RAFAELIA_MAX_CPUS) {
    errno = EOVERFLOW;
    return -1;
  }

  out->count = count;
  for (size_t i = 0; i < count; ++i) {
    const RafCpuSample *sample = &snapshot->samples[i];
    out->user[i] = raf_cpu_field_or_zero(sample, 0);
    out->nice[i] = raf_cpu_field_or_zero(sample, 1);
    out->system[i] = raf_cpu_field_or_zero(sample, 2);
    out->idle[i] = raf_cpu_field_or_zero(sample, 3);
    out->iowait[i] = raf_cpu_field_or_zero(sample, 4);
    out->irq[i] = raf_cpu_field_or_zero(sample, 5);
    out->softirq[i] = raf_cpu_field_or_zero(sample, 6);
    out->steal[i] = raf_cpu_field_or_zero(sample, 7);
    out->guest[i] = raf_cpu_field_or_zero(sample, 8);
    out->guest_nice[i] = raf_cpu_field_or_zero(sample, 9);
  }

  return 0;
}

static inline float raf_safe_ratio(uint64_t num, uint64_t den) {
  if (den == 0ULL) {
    return 0.0f;
  }
  return (float)((double)num / (double)den);
}

int raf_compute_cpu_metrics(const RafCpuSoa *prev,
                            const RafCpuSoa *curr,
                            RafCpuMetrics *out,
                            float hot_threshold) {
  if (prev == NULL || curr == NULL || out == NULL) {
    errno = EINVAL;
    return -1;
  }
  if (prev->count != curr->count) {
    errno = EINVAL;
    return -1;
  }

  const size_t n = curr->count;
  raf_bitset_clear(out->hot_flags);

  for (size_t i = 0; i < n; ++i) {
    const uint64_t prev_idle = prev->idle[i] + prev->iowait[i];
    const uint64_t curr_idle = curr->idle[i] + curr->iowait[i];
    const uint64_t prev_non_idle = prev->user[i] + prev->nice[i] + prev->system[i] +
                                   prev->irq[i] + prev->softirq[i] + prev->steal[i];
    const uint64_t curr_non_idle = curr->user[i] + curr->nice[i] + curr->system[i] +
                                   curr->irq[i] + curr->softirq[i] + curr->steal[i];

    const uint64_t prev_total = prev_idle + prev_non_idle + prev->guest[i] + prev->guest_nice[i];
    const uint64_t curr_total = curr_idle + curr_non_idle + curr->guest[i] + curr->guest_nice[i];

    const uint64_t total_delta = curr_total - prev_total;
    const uint64_t idle_delta = curr_idle - prev_idle;
    const uint64_t busy_delta = total_delta - idle_delta;

    const float util = raf_safe_ratio(busy_delta, total_delta);
    const float iowait_ratio = raf_safe_ratio(curr->iowait[i] - prev->iowait[i], total_delta);

    out->utilization[i] = util;
    out->iowait_ratio[i] = iowait_ratio;

    if (util >= hot_threshold) {
      raf_bitset_set(out->hot_flags, i);
    }
  }

  return 0;
}
