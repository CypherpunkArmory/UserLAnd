// ============================================================================
// RAFAELIA – FIBER-H Avalanche + Monte Carlo + Velocidade + Integridade
//  - Standalone: NÃO depende de fiber_hash_tree.c no link
//  - Implementa seu próprio hash FIBER estilo árvore (64-bit)
// ============================================================================

#define _POSIX_C_SOURCE 199309L

#include <stdio.h>
#include <stdint.h>
#include <stdlib.h>
#include <string.h>
#include <inttypes.h>
#include <time.h>

// --------------------------------------------------------------------------
// 1. Núcleo de hash estilo FIBER-H TREE (local, sem main externo)
// --------------------------------------------------------------------------

typedef enum {
    FIBER_MODE_SCALAR = 0,
    FIBER_MODE_LANES6 = 1
} fiber_mode_t;

static fiber_mode_t
fiber_decide_mode_local(int block_size)
{
    if (block_size <= 64) {
        return FIBER_MODE_SCALAR;
    }
    if (block_size >= 1024) {
        return FIBER_MODE_LANES6;
    }
    if (block_size < 128) {
        return FIBER_MODE_SCALAR;
    }
    return FIBER_MODE_LANES6;
}

#define FIB_PRIME1 0x9E3779B97F4A7C15ULL
#define FIB_PRIME2 0xD6E8FEB86659FD93ULL
#define FIB_PRIME3 0xA4093822299F31D0ULL

static inline uint64_t
mix64_tree(uint64_t x)
{
    x ^= x >> 31;
    x *= 0x9e3779b97f4a7c15ULL;
    x ^= x >> 23;
    x *= 0xff51afd7ed558ccdULL;
    x ^= x >> 39;
    x += 0xc4ceb9fe1a85ec53ULL;
    x = (x << 13) | (x >> (64 - 13));
    x ^= x >> 47;
    return x;
}

static uint64_t
fiber_hash_leaf(const uint8_t *data,
                size_t         len,
                size_t         leaf_index,
                int            block_size)
{
    fiber_mode_t mode = fiber_decide_mode_local(block_size);

    uint64_t h = FIB_PRIME1
               ^ ((uint64_t)block_size << 32)
               ^ (uint64_t)leaf_index
               ^ ((uint64_t)mode * FIB_PRIME2);

    for (size_t i = 0; i < len; ++i) {
        h ^= (uint64_t)data[i];
        h = mix64_tree(h + (uint64_t)i + (uint64_t)len);
    }

    h ^= (uint64_t)len;
    h = mix64_tree(h ^ FIB_PRIME3);
    return h;
}

static uint64_t
fiber_reduce_tree(uint64_t *nodes,
                  size_t    count,
                  int       block_size)
{
    if (count == 0) {
        return 0ULL;
    }

    size_t level = 0;
    while (count > 1) {
        size_t out = 0;

        for (size_t i = 0; i < count; i += 2) {
            uint64_t left  = nodes[i];
            uint64_t right;

            if (i + 1 < count) {
                right = nodes[i + 1];
            } else {
                right = nodes[i] ^ (0xDEADBEEFCAFEBABEULL
                                   ^ ((uint64_t)level << 32)
                                   ^ (uint64_t)block_size);
            }

            uint64_t combined = left ^ mix64_tree(right
                                     + ((uint64_t)level * FIB_PRIME2)
                                     + (uint64_t)block_size);

            nodes[out++] = mix64_tree(combined);
        }

        count = out;
        ++level;
    }

    return nodes[0];
}

static uint64_t
hash_fiber_tree(const uint8_t *data,
                size_t         len,
                int            block_size)
{
    if (data == NULL || len == 0 || block_size <= 0) {
        return 0ULL;
    }

    size_t bs = (size_t)block_size;
    size_t num_leaves = (len + bs - 1u) / bs;
    if (num_leaves == 0) {
        return 0ULL;
    }

    uint64_t *leaf_hashes = (uint64_t *)malloc(num_leaves * sizeof(uint64_t));
    if (!leaf_hashes) {
        fprintf(stderr,
                "hash_fiber_tree: malloc falhou (num_leaves=%zu)\n",
                num_leaves);
        return 0ULL;
    }

    for (size_t i = 0; i < num_leaves; ++i) {
        size_t offset    = i * bs;
        size_t remaining = len - offset;
        size_t chunk     = (remaining >= bs) ? bs : remaining;

        const uint8_t *ptr = data + offset;
        leaf_hashes[i] = fiber_hash_leaf(ptr, chunk, i, block_size);
    }

    uint64_t root = fiber_reduce_tree(leaf_hashes, num_leaves, block_size);
    free(leaf_hashes);
    return root;
}

// --------------------------------------------------------------------------
// 2. Utilidades: RNG, popcount, tempo
// --------------------------------------------------------------------------

static uint64_t rng_state = 0xA5A5F0F0DEADBEEFULL;

static uint64_t rng_u64(void) {
    uint64_t x = rng_state;
    x ^= x >> 12;
    x ^= x >> 25;
    x ^= x >> 27;
    rng_state = x;
    return x * 0x2545F4914F6CDD1DULL;
}

static void fill_random(uint8_t *buf, size_t len) {
    for (size_t i = 0; i < len; ++i) {
        buf[i] = (uint8_t)(rng_u64() & 0xFFu);
    }
}

static int popcount64(uint64_t x) {
#if defined(__GNUC__) || defined(__clang__)
    return __builtin_popcountll(x);
#else
    int c = 0;
    while (x) {
        x &= (x - 1);
        ++c;
    }
    return c;
#endif
}

static double elapsed_sec(struct timespec a, struct timespec b) {
    double s  = (double)(b.tv_sec  - a.tv_sec);
    double ns = (double)(b.tv_nsec - a.tv_nsec) / 1e9;
    return s + ns;
}

// --------------------------------------------------------------------------
// 3. Wrapper de hash
// --------------------------------------------------------------------------

static uint64_t hash_fiber(const uint8_t *data, size_t len, int block_size) {
    return hash_fiber_tree(data, len, block_size);
}

// --------------------------------------------------------------------------
// 4. Teste de Avalanche
// --------------------------------------------------------------------------

static void run_avalanche_test(size_t msg_len,
                               int    block_size,
                               size_t rounds)
{
    printf("=== AVALANCHE TEST (FIBER) ===\n");
    printf("  msg_len   = %zu bytes\n", msg_len);
    printf("  blocksize = %d bytes\n",  block_size);
    printf("  rounds    = %zu\n",       rounds);

    uint8_t *base = (uint8_t *)malloc(msg_len);
    uint8_t *mut  = (uint8_t *)malloc(msg_len);
    if (!base || !mut) {
        fprintf(stderr, "malloc falhou em run_avalanche_test\n");
        free(base);
        free(mut);
        return;
    }

    uint64_t total_distance = 0;
    uint64_t total_pairs    = 0;
    int      min_dist       = 64;
    int      max_dist       = 0;

    size_t bits_per_msg = msg_len * 8;

    for (size_t r = 0; r < rounds; ++r) {
        fill_random(base, msg_len);
        uint64_t h0 = hash_fiber(base, msg_len, block_size);

        for (size_t bit = 0; bit < bits_per_msg; ++bit) {
            memcpy(mut, base, msg_len);
            size_t byte_index = bit / 8;
            uint8_t mask      = (uint8_t)(1u << (bit % 8));
            mut[byte_index]  ^= mask;

            uint64_t h1 = hash_fiber(mut, msg_len, block_size);
            int dist = popcount64(h0 ^ h1);

            if (dist < min_dist) min_dist = dist;
            if (dist > max_dist) max_dist = dist;

            total_distance += (uint64_t)dist;
            total_pairs++;
        }
    }

    double avg = (total_pairs > 0)
               ? (double)total_distance / (double)total_pairs
               : 0.0;

    printf("  total_pairs          = %" PRIu64 "\n", total_pairs);
    printf("  avg distance (bits)  = %.4f (esperado ~32.0)\n", avg);
    printf("  min distance (bits)  = %d\n", min_dist);
    printf("  max distance (bits)  = %d\n", max_dist);
    printf("=== FIM AVALANCHE ===\n\n");
}

// --------------------------------------------------------------------------
// 5. Monte Carlo – Uniformidade
// --------------------------------------------------------------------------

static void run_montecarlo_uniformity(size_t max_len,
                                      int    block_size,
                                      size_t rounds)
{
    printf("=== MONTE CARLO (UNIFORMIDADE) ===\n");
    printf("  max_len   = %zu bytes\n", max_len);
    printf("  blocksize = %d bytes\n",  block_size);
    printf("  rounds    = %zu\n",       rounds);

    uint8_t *buf = (uint8_t *)malloc(max_len);
    if (!buf) {
        fprintf(stderr, "malloc falhou em run_montecarlo_uniformity\n");
        return;
    }

    uint64_t total_bits_set = 0;
    uint64_t total_hashes   = 0;

    for (size_t r = 0; r < rounds; ++r) {
        size_t len = 1 + (size_t)(rng_u64() % max_len);
        fill_random(buf, len);

        uint64_t h = hash_fiber(buf, len, block_size);
        int pc = popcount64(h);

        total_bits_set += (uint64_t)pc;
        total_hashes++;
    }

    free(buf);

    double avg_bits_set = (total_hashes > 0)
                        ? (double)total_bits_set / (double)total_hashes
                        : 0.0;
    double frac = avg_bits_set / 64.0;

    printf("  total_hashes         = %" PRIu64 "\n", total_hashes);
    printf("  avg bits set / hash  = %.4f (esperado ~32.0)\n",
           avg_bits_set);
    printf("  frac bits set        = %.4f (esperado ~0.5)\n", frac);
    printf("=== FIM MONTE CARLO ===\n\n");
}

// --------------------------------------------------------------------------
// 6. Velocidade – throughput em MiB/s
// --------------------------------------------------------------------------

#ifndef CPU_MHz
#define CPU_MHz 2800.0
#endif

static void run_speed_test(size_t msg_len,
                           int    block_size,
                           size_t total_mib)
{
    printf("=== SPEED TEST (FIBER) ===\n");
    printf("  msg_len    = %zu bytes\n", msg_len);
    printf("  blocksize  = %d bytes\n",  block_size);
    printf("  total_mib  = %zu MiB\n",    total_mib);

    uint8_t *buf = (uint8_t *)malloc(msg_len);
    if (!buf) {
        fprintf(stderr, "malloc falhou em run_speed_test\n");
        return;
    }
    fill_random(buf, msg_len);

    const size_t total_bytes_target =
        total_mib * 1024ULL * 1024ULL;

    size_t iters = total_bytes_target / msg_len;
    if (iters == 0) {
        iters = 1;
    }
    size_t effective_bytes = iters * msg_len;

    struct timespec t0, t1;
    clock_gettime(CLOCK_MONOTONIC, &t0);

    for (size_t i = 0; i < iters; ++i) {
        (void)hash_fiber(buf, msg_len, block_size);
    }

    clock_gettime(CLOCK_MONOTONIC, &t1);
    free(buf);

    double sec = elapsed_sec(t0, t1);
    double mib = (double)effective_bytes / (1024.0 * 1024.0);
    double mibps = (sec > 0.0) ? (mib / sec) : 0.0;
    double cpb   = (sec > 0.0)
                 ? (sec * CPU_MHz * 1e6) / (double)effective_bytes
                 : 0.0;

    printf("  effective_bytes = %zu\n", effective_bytes);
    printf("  elapsed_sec     = %.6f\n", sec);
    printf("  MiB/s           = %.3f\n", mibps);
    printf("  cycles/byte     = %.3f (assumindo CPU_MHz=%.1f)\n",
           cpb, (double)CPU_MHz);
    printf("=== FIM SPEED TEST ===\n\n");
}

// --------------------------------------------------------------------------
// 7. Integridade básica
// --------------------------------------------------------------------------

static void run_integrity_smoke(int block_size)
{
    printf("=== INTEGRIDADE (SMOKE TEST) ===\n");
    printf("  blocksize = %d bytes\n", block_size);

    uint8_t a[64];
    uint8_t b[64];
    uint8_t c[64];

    memset(a, 0x11, sizeof(a));
    memset(b, 0x11, sizeof(b));
    memset(c, 0x11, sizeof(c));
    c[0] ^= 0x01;

    uint64_t ha1 = hash_fiber(a, sizeof(a), block_size);
    uint64_t ha2 = hash_fiber(b, sizeof(b), block_size);
    uint64_t hc  = hash_fiber(c, sizeof(c), block_size);

    printf("  hash(a)  = 0x%016" PRIx64 "\n", ha1);
    printf("  hash(a') = 0x%016" PRIx64 " (esperado igual a hash(a))\n", ha2);
    printf("  hash(c)  = 0x%016" PRIx64 " (esperado diferente)\n", hc);

    if (ha1 == ha2) {
        printf("  [OK] Consistencia: hash(a) == hash(a')\n");
    } else {
        printf("  [FALHA] Inconsistencia: hash(a) != hash(a')\n");
    }

    if (ha1 != hc) {
        printf("  [OK] Diferenciacao: hash(a) != hash(c)\n");
    } else {
        printf("  [AVISO] Colisao em smoke test\n");
    }

    printf("=== FIM INTEGRIDADE ===\n\n");
}

// --------------------------------------------------------------------------
// 8. CLI / Main
// --------------------------------------------------------------------------

static void print_usage(const char *prog) {
    printf("Uso:\n");
    printf("  %s                # avalanche + montecarlo + speed + integridade (default)\n", prog);
    printf("  %s all            # igual ao default\n", prog);
    printf("  %s avalanche      # apenas teste de avalanche\n", prog);
    printf("  %s montecarlo     # apenas teste de uniformidade/Monte Carlo\n", prog);
    printf("  %s speed          # apenas teste de velocidade\n", prog);
    printf("  %s mc             # Monte Carlo + speed + integridade (SEM avalanche)\n", prog);
}

int main(int argc, char *argv[])
{
    const size_t AV_MSG_LEN   = 256;
    const int    AV_BLOCKSIZE = 1024;
    const size_t AV_ROUNDS    = 16;

    const size_t MC_MAX_LEN   = 2048;
    const int    MC_BLOCKSIZE = 1024;
    const size_t MC_ROUNDS    = 10000;

    const size_t SP_MSG_LEN   = 1024;
    const int    SP_BLOCKSIZE = 1024;
    const size_t SP_TOTAL_MIB = 256;

    const int    INTEG_BLOCKSIZE = 1024;

    const char *mode = NULL;
    if (argc >= 2) {
        mode = argv[1];
        if (strcmp(mode, "all") != 0 &&
            strcmp(mode, "avalanche") != 0 &&
            strcmp(mode, "montecarlo") != 0 &&
            strcmp(mode, "speed") != 0 &&
            strcmp(mode, "mc") != 0) {
            print_usage(argv[0]);
            return 1;
        }
    }

    // Modo especial "mc": Monte Carlo + speed + integridade (SEM avalanche)
    if (mode != NULL && strcmp(mode, "mc") == 0) {
        run_montecarlo_uniformity(MC_MAX_LEN, MC_BLOCKSIZE, MC_ROUNDS);
        run_speed_test(SP_MSG_LEN, SP_BLOCKSIZE, SP_TOTAL_MIB);
        run_integrity_smoke(INTEG_BLOCKSIZE);
        return 0;
    }

    // Default / all / avalanche / montecarlo / speed
    if (mode == NULL || strcmp(mode, "avalanche") == 0 || strcmp(mode, "all") == 0) {
        run_avalanche_test(AV_MSG_LEN, AV_BLOCKSIZE, AV_ROUNDS);
    }

    if (mode == NULL || strcmp(mode, "montecarlo") == 0 || strcmp(mode, "all") == 0) {
        run_montecarlo_uniformity(MC_MAX_LEN, MC_BLOCKSIZE, MC_ROUNDS);
    }

    if (mode == NULL || strcmp(mode, "speed") == 0 || strcmp(mode, "all") == 0) {
        run_speed_test(SP_MSG_LEN, SP_BLOCKSIZE, SP_TOTAL_MIB);
    }

    if (mode == NULL || strcmp(mode, "all") == 0) {
        run_integrity_smoke(INTEG_BLOCKSIZE);
    }

    return 0;
}
