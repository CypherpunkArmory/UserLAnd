#define _POSIX_C_SOURCE 200809L

#include "raf_core.h"

#include <ctype.h>
#include <errno.h>
#include <stdlib.h>
#include <string.h>
#include <time.h>

static uint64_t raf_now_ms(void) {
  struct timespec ts;
  if (clock_gettime(CLOCK_REALTIME, &ts) != 0) {
    return 0;
  }
  return (uint64_t)ts.tv_sec * 1000ULL + (uint64_t)(ts.tv_nsec / 1000000ULL);
}

static int raf_parse_cpu_line(const char *line, RafCpuSample *out) {
  if (strncmp(line, "cpu", 3) != 0) {
    return 0;
  }

  const char *cursor = line;
  while (*cursor != '\0' && !isspace((unsigned char)*cursor)) {
    cursor++;
  }
  size_t id_len = (size_t)(cursor - line);
  if (id_len == 0 || id_len >= sizeof(out->id)) {
    return -1;
  }
  memcpy(out->id, line, id_len);
  out->id[id_len] = '\0';

  size_t idx = 0;
  while (*cursor != '\0' && idx < RAFAELIA_CPU_FIELDS) {
    while (*cursor == ' ' || *cursor == '\t') {
      cursor++;
    }
    if (*cursor == '\0' || *cursor == '\n') {
      break;
    }
    char *endptr = NULL;
    unsigned long long value = strtoull(cursor, &endptr, 10);
    if (endptr == cursor) {
      break;
    }
    out->fields[idx++] = (uint64_t)value;
    cursor = endptr;
  }

  out->field_count = idx;
  return idx > 0 ? 1 : -1;
}

int raf_parse_proc_stat(FILE *stream, RafCpuSnapshot *out) {
  if (stream == NULL || out == NULL) {
    errno = EINVAL;
    return -1;
  }

  memset(out, 0, sizeof(*out));
  out->timestamp_ms = raf_now_ms();

  char line[512];
  while (fgets(line, sizeof(line), stream) != NULL) {
    if (strncmp(line, "cpu", 3) != 0) {
      break;
    }
    RafCpuSample sample;
    int parsed = raf_parse_cpu_line(line, &sample);
    if (parsed == 1 && out->sample_count < RAFAELIA_MAX_CPUS) {
      out->samples[out->sample_count++] = sample;
    } else if (parsed < 0) {
      return -1;
    }
  }

  return out->sample_count > 0 ? 0 : -1;
}

int raf_export_cpu_json(const RafCpuSnapshot *snapshot, char *buffer, size_t buffer_size) {
  if (snapshot == NULL || buffer == NULL) {
    errno = EINVAL;
    return -1;
  }

  size_t offset = 0;
  int written = snprintf(buffer, buffer_size,
                         "{\"timestamp_ms\":%llu,\"cpus\":[",
                         (unsigned long long)snapshot->timestamp_ms);
  if (written < 0 || (size_t)written >= buffer_size) {
    errno = ENOSPC;
    return -1;
  }
  offset += (size_t)written;

  for (size_t i = 0; i < snapshot->sample_count; ++i) {
    const RafCpuSample *sample = &snapshot->samples[i];
    written = snprintf(buffer + offset, buffer_size - offset, "{\"id\":\"%s\"",
                       sample->id);
    if (written < 0 || offset + (size_t)written >= buffer_size) {
      errno = ENOSPC;
      return -1;
    }
    offset += (size_t)written;

    for (size_t f = 0; f < RAFAELIA_CPU_FIELDS; ++f) {
      uint64_t value = f < sample->field_count ? sample->fields[f] : 0;
      written = snprintf(buffer + offset, buffer_size - offset, ",\"%s\":%llu",
                         RAFAELIA_CPU_FIELD_NAMES[f], (unsigned long long)value);
      if (written < 0 || offset + (size_t)written >= buffer_size) {
        errno = ENOSPC;
        return -1;
      }
      offset += (size_t)written;
    }

    written = snprintf(buffer + offset, buffer_size - offset, "}%s",
                       (i + 1U == snapshot->sample_count) ? "" : ",");
    if (written < 0 || offset + (size_t)written >= buffer_size) {
      errno = ENOSPC;
      return -1;
    }
    offset += (size_t)written;
  }

  written = snprintf(buffer + offset, buffer_size - offset, "]}\n");
  if (written < 0 || offset + (size_t)written >= buffer_size) {
    errno = ENOSPC;
    return -1;
  }
  return 0;
}
