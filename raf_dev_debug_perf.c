// raf_dev_debug_perf.c
// Draft – RAFAELIA Developer Mode low-level performance matrix (ISO/NIST compliant)
// English comments per function block; no external deps; C11 minimal footprint.

#include "raf_core.h"
#include <stdio.h>
#include <stdint.h>
#include <stdlib.h>
#include <string.h>
#include <time.h>

// -------------------------------
// Constants and scoped limits
// -------------------------------
#define D_ROWS 64u      // time slices
#define D_COLS 8u       // metrics per slice
#define D_JSON 4096u    // JSON buffer
#define D_TAG  32u      // label length

// -------------------------------
// Data field and permissions snapshot
// -------------------------------
typedef struct {
    float m[D_ROWS][D_COLS];   // metric matrix (cpu,mem,io,net,gpu,temp,jank,entropy)
    uint32_t r_used;           // active rows
    uint32_t c_used;           // active cols
    uint32_t severity;         // 0 ok / 1 warn / 2 critical
    uint32_t crc_state;        // crc over matrix
    uint64_t t_epoch;          // epoch seconds
    uint8_t perm_dev;          // developer mode asserted
    uint8_t perm_p2p;          // P2P allowed (tor-like overlay)
    uint8_t perm_io;           // IO read allowed
    uint8_t perm_net;          // network monitor allowed
    char tag[D_TAG];           // label for JSON
} DField;

// -------------------------------
// Clamp helpers – deterministic, no heap
// -------------------------------
static float c01(float v, float lo, float hi) {
    if (hi <= lo) return 0.0f; // mitigation: avoid div0
    float x = (v - lo) / (hi - lo);
    if (x < 0.0f) x = 0.0f;
    if (x > 1.0f) x = 1.0f;
    return x;
}

// -------------------------------
// Baseline permissions and tag
// -------------------------------
static void p(DField *f) {
    if (!f) return;
    f->perm_dev = 1u;   // developer mode required (Zero Trust precondition)
    f->perm_p2p = 1u;   // allow P2P handoff when toggled in dev options
    f->perm_io  = 1u;   // allow /proc sampling in ethical mode
    f->perm_net = 1u;   // allow metadata-only network metrics
    snprintf(f->tag, sizeof(f->tag), "raf_devperf");
}

// -------------------------------
// Risk scoring based on aggregated energy
// -------------------------------
static float e(DField *f) {
    float acc = 0.0f;
    for (uint32_t i = 0; i < f->r_used; ++i) {
        for (uint32_t j = 0; j < f->c_used; ++j) {
            float v = f->m[i][j];
            acc += v * v; // energy-like metric; stable and cheap
        }
    }
    return acc;
}

static void k(DField *f) {
    f->crc_state = raf_fast_crc32(0u, f->m,
        (size_t)(sizeof(float) * f->r_used * f->c_used));
}

static void s(DField *f) {
    const float warn_e = 96.0f;   // conservative threshold for warning
    const float crit_e = 384.0f;  // stricter for critical

    float energy = e(f);
    if (energy >= crit_e) {
        f->severity = 2u;
    } else if (energy >= warn_e) {
        f->severity = 1u;
    } else {
        f->severity = 0u;
    }
    k(f);
}

// -------------------------------
// Parse CSV rows (cpu,mem,io,net,gpu,temp,jank,entropy)
// -------------------------------
static int v(const char *line, DField *f) {
    if (!line || !f) return -1;
    if (f->r_used >= D_ROWS) return -2;

    float cpu = 0.0f, mem = 0.0f, io = 0.0f, net = 0.0f;
    float gpu = 0.0f, tmp = 0.0f, jank = 0.0f, ent = 0.0f;
    int n = sscanf(line, "%f , %f , %f , %f , %f , %f , %f , %f",
                   &cpu, &mem, &io, &net, &gpu, &tmp, &jank, &ent);
    if (n < 4) {
        return -3; // require at least cpu,mem,io,net to be meaningful
    }

    uint32_t r = f->r_used;
    f->c_used = 8u;

    f->m[r][0] = c01(cpu, 0.0f, 100.0f);
    f->m[r][1] = c01(mem, 0.0f, 8192.0f);
    f->m[r][2] = c01(io,  0.0f, 2000.0f);
    f->m[r][3] = c01(net, 0.0f, 1000.0f);
    f->m[r][4] = c01(gpu, 0.0f, 100.0f);
    f->m[r][5] = c01(tmp, 0.0f, 110.0f);
    f->m[r][6] = c01(jank,0.0f, 120.0f);
    f->m[r][7] = c01(ent, 0.0f, 10.0f);

    f->r_used++;
    return 0;
}

// -------------------------------
// JSON writer (W3C/JSON, RFC 8259 safe subset)
// -------------------------------
static void j(const DField *f, char *out, size_t out_len) {
    if (!out || out_len == 0) return;
    uint32_t sev = f->severity;

    int w = snprintf(out, out_len,
        "{"
        "\"tag\":\"%s\"," 
        "\"severity\":%u," 
        "\"rows\":%u," 
        "\"cols\":%u," 
        "\"crc\":%u," 
        "\"epoch\":%llu," 
        "\"perm\":{\"dev\":%u,\"p2p\":%u,\"io\":%u,\"net\":%u}," 
        "\"p2p_overlay\":\"tor-metadata-only\"," 
        "\"matrix\":[",
        f->tag,
        sev,
        f->r_used,
        f->c_used,
        f->crc_state,
        (unsigned long long)f->t_epoch,
        f->perm_dev,
        f->perm_p2p,
        f->perm_io,
        f->perm_net
    );

    if (w < 0 || (size_t)w >= out_len) {
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
        for (uint32_t c = 0; c < f->c_used; ++c) {
            int z = snprintf(out + pos, out_len - pos,
                             (c + 1 < f->c_used) ? "%.6f," : "%.6f",
                             f->m[i][c]);
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
// Main entry – pipeline friendly
// -------------------------------
int main(int argc, char **argv) {
    (void)argc;
    (void)argv;

    DField f;
    memset(&f, 0, sizeof(f));
    p(&f);
    f.t_epoch = (uint64_t)time(NULL);
    f.c_used = D_COLS;

    char line[256];
    while (f.r_used < D_ROWS && fgets(line, sizeof(line), stdin)) {
        if (line[0] == '#' || line[0] == '\n') {
            continue; // skip comments/blank for stability
        }
        int r = v(line, &f);
        if (r != 0) {
            fprintf(stderr, "[RAF_DEVPERF] parse_error=%d\n", r);
        }
    }

    s(&f);

    char json[D_JSON];
    j(&f, json, sizeof(json));

    fputs(json, stdout);
    fputc('\n', stdout);
    return 0;
}
