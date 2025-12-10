// raf_userland_debug.c
// Draft – UserLAnd Debug Matrix Core
// C11, no GC, low-level only.

#include "raf_core.h"   // Reuse AbsoluteMatrix, RNG, CRC helpers where possible.
#include <stdio.h>
#include <stdlib.h>
#include <stdint.h>
#include <string.h>
#include <time.h>

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

    // Strict parsing: we do not trust input.
    // Example: "34.5, 512.0, 20.0, 150.0"
    float cpu = 0.0f, mem = 0.0f, io = 0.0f, thr = 0.0f;
    int n = sscanf(line, "%f , %f , %f , %f", &cpu, &mem, &io, &thr);
    if (n < 2) {
        // At least CPU/mem required.
        return -3;
    }

    uint32_t r = f->r_used;
    f->c_used = 4u; // fixed 4 metrics for now

    // Normalize to [0,1] in a simple way; real thresholds should be configurable.
    f->m[r][0] = n01(cpu, 0.0f, 100.0f);     // CPU %
    f->m[r][1] = n01(mem, 0.0f, 4096.0f);    // Memory MB
    f->m[r][2] = n01(io,  0.0f, 1000.0f);    // I/O ops
    f->m[r][3] = n01(thr, 0.0f, 4096.0f);    // Thread count

    f->r_used++;
    return 0;
}

// -------------------------------
// JSON output (W3C / interoperability friendly)
// -------------------------------

static void j(const RField *f, char *out, size_t out_len, const char *tag) {
    if (!out || out_len == 0) return;

    // Minimal JSON writer, no external libs.
    // It assumes ASCII-safe tag and sane sizes.
    uint32_t sev = f->severity;
    if (!tag) tag = "userland_debug";

    // Start
    int w = snprintf(out, out_len,
        "{"
        "\"tag\":\"%s\","
        "\"severity\":%u,"
        "\"rows\":%u,"
        "\"cols\":%u,"
        "\"crc\":%u,"
        "\"epoch\":%llu,"
        "\"matrix\":[",
        tag,
        sev,
        f->r_used,
        f->c_used,
        f->crc_state,
        (unsigned long long)f->t_epoch
    );

    if (w < 0 || (size_t)w >= out_len) {
        // Truncated or error, fallback to empty object.
        if (out_len > 2u) {
            out[0] = '{';
            out[1] = '}';
            out[2] = '\0';
        }
        return;
    }

    size_t pos = (size_t)w;
    for (uint32_t i = 0; i < f->r_used; ++i) {
        if (pos + 2 >= out_len) break;
        out[pos++] = '[';
        for (uint32_t j = 0; j < f->c_used; ++j) {
            int z = snprintf(out + pos, out_len - pos,
                             (j + 1 < f->c_used) ? "%.6f," : "%.6f",
                             f->m[i][j]);
            if (z < 0 || (size_t)z >= (out_len - pos)) {
                pos = out_len - 1;
                break;
            }
            pos += (size_t)z;
        }
        if (pos + 2 >= out_len) break;
        out[pos++] = ']';
        if (i + 1 < f->r_used) {
            out[pos++] = ',';
        }
    }

    if (pos + 2 < out_len) {
        out[pos++] = ']';
        out[pos++] = '}';
        out[pos]   = '\0';
    } else if (out_len > 2u) {
        out[0] = '{';
        out[1] = '}';
        out[2] = '\0';
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
    f.t_epoch = (uint64_t)time(NULL);

    // Strict stdin processing: CSV lines from shell or another tool.
    char line[256];
    while (f.r_used < R_MAX_ROWS && fgets(line, sizeof(line), stdin)) {
        if (line[0] == '#' || line[0] == '\n') {
            continue; // ignore comments / empty
        }
        int r = v(line, &f);
        if (r != 0) {
            // Security choice: do not print raw line to avoid leaking secrets.
            fprintf(stderr, "[RDBG] parse_error code=%d\n", r);
        }
    }

    s(&f); // analyze matrix

    char json[R_JSON_BUF];
    j(&f, json, sizeof(json), "raf_userland_matrix");

    // Always one single JSON object to stdout: safe for pipes.
    fputs(json, stdout);
    fputc('\n', stdout);

    return 0;
}
