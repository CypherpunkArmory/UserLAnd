#define _POSIX_C_SOURCE 199309L

#include <ctype.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <time.h>

#define MAX_DISKS 256
#define MAX_LINE 512
#define MAX_NAME_LEN 32
#define DISK_FIELD_COUNT 11

static const char *DISK_FIELD_NAMES[DISK_FIELD_COUNT] = {
    "reads_completed",     "reads_merged",       "sectors_read",
    "time_reading_ms",     "writes_completed",   "writes_merged",
    "sectors_written",     "time_writing_ms",    "ios_in_progress",
    "time_doing_io_ms",    "weighted_time_io_ms"};

typedef struct {
  unsigned int major;
  unsigned int minor;
  char name[MAX_NAME_LEN];
  uint64_t fields[DISK_FIELD_COUNT];
  size_t field_count;
} disk_sample;

static uint64_t now_ms(void) {
  struct timespec ts;
  if (clock_gettime(CLOCK_REALTIME, &ts) != 0) {
    return 0;
  }
  return (uint64_t)ts.tv_sec * 1000ULL + (uint64_t)(ts.tv_nsec / 1000000ULL);
}

static void skip_spaces(const char **cursor) {
  while (**cursor == ' ' || **cursor == '\t') {
    (*cursor)++;
  }
}

static int parse_disk_line(const char *line, disk_sample *out) {
  char buffer[MAX_LINE];
  size_t len = 0;
  while (len < sizeof(buffer) - 1 && line[len] != '\0') {
    buffer[len] = line[len];
    len++;
  }
  buffer[len] = '\0';

  const char *cursor = buffer;
  skip_spaces(&cursor);

  char *endptr = NULL;
  unsigned long major = strtoul(cursor, &endptr, 10);
  if (endptr == cursor) {
    return 0;
  }
  cursor = endptr;
  skip_spaces(&cursor);

  unsigned long minor = strtoul(cursor, &endptr, 10);
  if (endptr == cursor) {
    return 0;
  }
  cursor = endptr;
  skip_spaces(&cursor);

  const char *name_start = cursor;
  while (*cursor != '\0' && !isspace((unsigned char)*cursor)) {
    cursor++;
  }
  size_t name_len = (size_t)(cursor - name_start);
  if (name_len == 0 || name_len >= MAX_NAME_LEN) {
    return 0;
  }
  memcpy(out->name, name_start, name_len);
  out->name[name_len] = '\0';

  out->major = (unsigned int)major;
  out->minor = (unsigned int)minor;

  size_t idx = 0;
  while (*cursor != '\0' && idx < DISK_FIELD_COUNT) {
    skip_spaces(&cursor);
    if (*cursor == '\0' || *cursor == '\n') {
      break;
    }
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

static void print_disk_json(const disk_sample *sample, int is_last) {
  fputs("{\"name\":\"", stdout);
  fputs(sample->name, stdout);
  fputc('\"', stdout);

  printf(",\"major\":%u,\"minor\":%u", sample->major, sample->minor);

  for (size_t i = 0; i < DISK_FIELD_COUNT; ++i) {
    uint64_t value = i < sample->field_count ? sample->fields[i] : 0;
    fputc(',', stdout);
    fputc('\"', stdout);
    fputs(DISK_FIELD_NAMES[i], stdout);
    fputs("\":", stdout);
    printf("%llu", (unsigned long long)value);
  }

  fputc('}', stdout);
  if (!is_last) {
    fputc(',', stdout);
  }
}

int main(void) {
  FILE *fp = fopen("/proc/diskstats", "r");
  if (fp == NULL) {
    return 1;
  }

  disk_sample samples[MAX_DISKS];
  size_t sample_count = 0;
  char line[MAX_LINE];

  while (fgets(line, sizeof(line), fp) != NULL && sample_count < MAX_DISKS) {
    disk_sample sample;
    int parsed = parse_disk_line(line, &sample);
    if (parsed == 1) {
      samples[sample_count++] = sample;
    }
  }

  fclose(fp);

  if (sample_count == 0) {
    return 1;
  }

  printf("{\"timestamp_ms\":%llu,\"disks\":[",
         (unsigned long long)now_ms());
  for (size_t i = 0; i < sample_count; ++i) {
    print_disk_json(&samples[i], i + 1 == sample_count);
  }
  fputs("]}\n", stdout);
  return 0;
}
