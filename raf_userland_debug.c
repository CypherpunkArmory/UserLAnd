// raf_userland_debug.c
// Draft – UserLAnd Debug Matrix Core
// C11, no GC, low-level only.

#define _POSIX_C_SOURCE 200809L

#include "raf_core.h"   // Reuse AbsoluteMatrix, RNG, CRC helpers where possible.
#include <stdint.h>
#include <string.h>
#include <time.h>
#include <unistd.h>

// -------------------------------
// Local constants and types
// -------------------------------

#define R_MAX_ROWS   64u     // Matrix rows (time windows / signals)
#define R_MAX_COLS   16u     // Matrix cols (metrics per signal)
#define R_JSON_BUF   4096u   // Output buffer size for JSON
#define R_TAG_LEN    32u     // Small tag buffers

typedef struct {
    float m[R_MAX_ROWS][R_MAX_COLS];   // Main matrix (metrics)
    uint32_t r_used;                   // Number of active rows
    uint32_t c_used;                   // Number of active cols
    uint32_t severity;                 // 0=ok,1=warn,2=critical
    uint32_t crc_state;                // CRC over matrix
    uint64_t t_epoch;                  // Epoch snapshot
} RField;

// -------------------------------
// Small helpers (no GC, no globals)
// -------------------------------

// Map value to [0,1] (simple normalization).
static float n01(float v, float min, float max) {
    if (max <= min) {
        return 0.0f;
    }
    float x = (v - min) / (max - min);
    if (x < 0.0f) x = 0.0f;
    if (x > 1.0f) x = 1.0f;
    return x;
}

// Minimal integer to string (base 10). Returns length written (without null).
static size_t u32_to_str(uint32_t v, char *out, size_t cap) {
    if (cap == 0u) {
        return 0u;
    }
    char tmp[16];
    size_t idx = 0u;
    do {
        tmp[idx++] = (char)('0' + (v % 10u));
        v /= 10u;
    } while (v != 0u && idx < sizeof(tmp));

    size_t pos = 0u;
    while (idx > 0u && pos + 1u < cap) {
        out[pos++] = tmp[--idx];
    }
    out[pos] = '\0';
    return pos;
}

static size_t u64_to_str(uint64_t v, char *out, size_t cap) {
    if (cap == 0u) {
        return 0u;
    }
    char tmp[32];
    size_t idx = 0u;
    do {
        tmp[idx++] = (char)('0' + (v % 10u));
        v /= 10u;
    } while (v != 0u && idx < sizeof(tmp));

    size_t pos = 0u;
    while (idx > 0u && pos + 1u < cap) {
        out[pos++] = tmp[--idx];
    }
    out[pos] = '\0';
    return pos;
}

static size_t f_to_str6(float v, char *out, size_t cap) {
    if (cap < 3u) {
        return 0u;
    }

    size_t pos = 0u;
    if (v < 0.0f) {
        out[pos++] = '-';
        v = -v;
        if (pos >= cap) {
            return pos - 1u;
        }
    }

    uint32_t scaled = (uint32_t)(v * 1000000.0f + 0.5f);
    uint32_t whole = scaled / 1000000u;
    uint32_t frac = scaled % 1000000u;

    pos += u32_to_str(whole, out + pos, cap - pos);
    if (pos + 7u >= cap) {
        return pos;
    }
    out[pos++] = '.';
    uint32_t div = 100000u;
    for (int i = 0; i < 6 && pos < cap; ++i) {
        out[pos++] = (char)('0' + (frac / div));
        frac %= div;
        div /= 10u;
    }
    if (pos < cap) {
        out[pos] = '\0';
    }
    return pos;
}

// Small append helpers for static buffers.
static size_t append_str(char *out, size_t pos, size_t cap, const char *src) {
    while (src && *src && pos + 1u < cap) {
        out[pos++] = *src++;
    }
    return pos;
}

static size_t append_char(char *out, size_t pos, size_t cap, char c) {
    if (pos + 1u < cap) {
        out[pos++] = c;
    }
    return pos;
}

static size_t append_u32(char *out, size_t pos, size_t cap, uint32_t v) {
    return pos + u32_to_str(v, out + pos, cap - pos);
}

static size_t append_u64(char *out, size_t pos, size_t cap, uint64_t v) {
    return pos + u64_to_str(v, out + pos, cap - pos);
}

static size_t append_f6(char *out, size_t pos, size_t cap, float v) {
    return pos + f_to_str6(v, out + pos, cap - pos);
}

// Lightweight float parser for positive/negative decimals.
static int parse_float(const char **p, float *out) {
    const char *s = *p;
    while (*s == ' ' || *s == '\t' || *s == ',') {
        ++s;
    }

    int neg = 0;
    if (*s == '+') {
        ++s;
    } else if (*s == '-') {
        neg = 1;
        ++s;
    }

    uint32_t whole = 0u;
    uint32_t frac = 0u;
    uint32_t frac_div = 1u;
    int has_digit = 0;

    while (*s >= '0' && *s <= '9') {
        has_digit = 1;
        whole = whole * 10u + (uint32_t)(*s - '0');
        ++s;
    }

    if (*s == '.') {
        ++s;
        while (*s >= '0' && *s <= '9') {
            has_digit = 1;
            frac = frac * 10u + (uint32_t)(*s - '0');
            frac_div *= 10u;
            ++s;
        }
    }

    if (!has_digit) {
        return 0;
    }

    float val = (float)whole + ((float)frac / (float)frac_div);
    if (neg) {
        val = -val;
    }
    *out = val;
    *p = s;
    return 1;
}

static void log_parse_error(int code) {
    char buf[48];
    size_t pos = 0u;
    pos = append_str(buf, pos, sizeof(buf), "[RDBG] parse_error code=");
    pos = append_u32(buf, pos, sizeof(buf), (uint32_t)code);
    pos = append_char(buf, pos, sizeof(buf), '\n');
    if (pos < sizeof(buf)) {
        write(STDERR_FILENO, buf, pos);
    }
}

// Compute a simple "energy" metric of the matrix.
static float e(RField *f) {
    float acc = 0.0f;
    for (uint32_t i = 0; i < f->r_used; ++i) {
        for (uint32_t j = 0; j < f->c_used; ++j) {
            float v = f->m[i][j];
            acc += v * v;
        }
    }
    return acc;
}

// Update CRC of the matrix using raf_fast_crc32.
static void k(RField *f) {
    f->crc_state = raf_fast_crc32(0u, f->m,
        (size_t)(sizeof(float) * f->r_used * f->c_used));
}

// -------------------------------
// Core step: analyze and classify
// -------------------------------

// Interpret matrix as debug "frame" and derive severity.
static void s(RField *f) {
    // In a real build, thresholds come from config.
    const float warn_e = 128.0f;
    const float crit_e = 512.0f;

    float energy = e(f);

    if (energy >= crit_e) {
        f->severity = 2; // critical
    } else if (energy >= warn_e) {
        f->severity = 1; // warning
    } else {
        f->severity = 0; // ok
    }

    k(f);
}

// -------------------------------
// Input mapping (from simple CSV-like lines)
// -------------------------------

// Parse a single line "cpu,mem,io,threads" into one matrix row.
static int v(const char *line, RField *f) {
    if (!line || !f) return -1;
    if (f->r_used >= R_MAX_ROWS) return -2;

    const char *p = line;
    float vals[4] = {0.0f, 0.0f, 0.0f, 0.0f};
    int count = 0;
    while (count < 4) {
        if (!parse_float(&p, &vals[count])) {
            break;
        }
        ++count;
        while (*p == ' ' || *p == '\t' || *p == ',' ) {
            ++p;
        }
        if (*p == '\0' || *p == '\n') {
            break;
        }
    }

    if (count < 2) {
        // At least CPU/mem required.
        return -3;
    }

    uint32_t r = f->r_used;
    f->c_used = 4u; // fixed 4 metrics for now

    // Normalize to [0,1] in a simple way; real thresholds should be configurable.
    f->m[r][0] = n01(vals[0], 0.0f, 100.0f);     // CPU %
    f->m[r][1] = n01(vals[1], 0.0f, 4096.0f);    // Memory MB
    f->m[r][2] = n01(vals[2], 0.0f, 1000.0f);    // I/O ops
    f->m[r][3] = n01(vals[3], 0.0f, 4096.0f);    // Thread count

    f->r_used++;
    return 0;
}

// -------------------------------
// JSON output (W3C / interoperability friendly)
// -------------------------------

static void j(const RField *f, char *out, size_t out_len, const char *tag) {
    if (!out || out_len == 0u) return;

    if (!tag) tag = "userland_debug";

    size_t pos = 0u;
    pos = append_char(out, pos, out_len, '{');
    pos = append_str(out, pos, out_len, "\"tag\":\"");
    pos = append_str(out, pos, out_len, tag);
    pos = append_str(out, pos, out_len, "\",");

    pos = append_str(out, pos, out_len, "\"severity\":");
    pos = append_u32(out, pos, out_len, f->severity);
    pos = append_char(out, pos, out_len, ',');

    pos = append_str(out, pos, out_len, "\"rows\":");
    pos = append_u32(out, pos, out_len, f->r_used);
    pos = append_char(out, pos, out_len, ',');

    pos = append_str(out, pos, out_len, "\"cols\":");
    pos = append_u32(out, pos, out_len, f->c_used);
    pos = append_char(out, pos, out_len, ',');

    pos = append_str(out, pos, out_len, "\"crc\":");
    pos = append_u32(out, pos, out_len, f->crc_state);
    pos = append_char(out, pos, out_len, ',');

    pos = append_str(out, pos, out_len, "\"epoch\":");
    pos = append_u64(out, pos, out_len, f->t_epoch);
    pos = append_char(out, pos, out_len, ',');

    pos = append_str(out, pos, out_len, "\"matrix\":[");

    for (uint32_t i = 0u; i < f->r_used && pos < out_len; ++i) {
        pos = append_char(out, pos, out_len, '[');
        for (uint32_t j = 0u; j < f->c_used && pos < out_len; ++j) {
            pos = append_f6(out, pos, out_len, f->m[i][j]);
            if (j + 1u < f->c_used) {
                pos = append_char(out, pos, out_len, ',');
            }
        }
        pos = append_char(out, pos, out_len, ']');
        if (i + 1u < f->r_used) {
            pos = append_char(out, pos, out_len, ',');
        }
    }

    pos = append_char(out, pos, out_len, ']');
    pos = append_char(out, pos, out_len, '}');

    if (pos < out_len) {
        out[pos] = '\0';
    } else if (out_len > 0u) {
        out[out_len - 1u] = '\0';
    }
}

// -------------------------------
// CLI entry point
// -------------------------------

int main(int argc, char **argv) {
    (void)argc;
    (void)argv;

    RField f;
    memset(&f, 0, sizeof(f));

    // Time snapshot for traceability (ISO 27001 logging).
    struct timespec ts;
    if (clock_gettime(CLOCK_REALTIME, &ts) == 0) {
        f.t_epoch = (uint64_t)ts.tv_sec;
    }

    // Strict stdin processing: CSV lines from shell or another tool.
    char line[256];
    size_t line_len = 0u;
    char in_buf[512];

    while (f.r_used < R_MAX_ROWS) {
        ssize_t got = read(STDIN_FILENO, in_buf, sizeof(in_buf));
        if (got <= 0) {
            break;
        }
        for (ssize_t i = 0; i < got && f.r_used < R_MAX_ROWS; ++i) {
            char c = in_buf[i];
            if (c == '\r') {
                continue;
            }
            if (c == '\n') {
                line[line_len] = '\0';
                if (line_len > 0u && line[0] != '#') {
                    int r = v(line, &f);
                    if (r != 0) {
                        log_parse_error(r);
                    }
                }
                line_len = 0u;
                continue;
            }
            if (line_len + 1u < sizeof(line)) {
                line[line_len++] = c;
            }
        }
    }

    if (line_len > 0u && f.r_used < R_MAX_ROWS) {
        line[line_len] = '\0';
        if (line[0] != '#') {
            int r = v(line, &f);
            if (r != 0) {
                log_parse_error(r);
            }
        }
    }

    s(&f); // analyze matrix

    char json[R_JSON_BUF];
    j(&f, json, sizeof(json), "raf_userland_matrix");

    // Always one single JSON object to stdout: safe for pipes.
    size_t out_len = strlen(json);
    if (out_len > 0u) {
        write(STDOUT_FILENO, json, out_len);
    }
    write(STDOUT_FILENO, "\n", 1u);

    return 0;
}
