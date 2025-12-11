// ============================================================================
// RAFAELIA – FIBER vs BLAKE3 vs SHA3-256
//  - Avalanche, Monte Carlo, Velocidade, Integridade
//  - Requer: blake3.h (+ -lblake3) e OpenSSL (-lcrypto)
// ============================================================================

#define _POSIX_C_SOURCE 199309L

#include <stdio.h>
#include <stdint.h>
#include <stdlib.h>
#include <string.h>
#include <inttypes.h>
#include <time.h>

#include <openssl/evp.h>
#include "blake3.h"

// --------------------------------------------------------------------------
// 1. RNG, popcount, tempo
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

static int popcount256(const uint8_t h[32]) {
    int bits = 0;
    for (int i = 0; i < 32; i += 8) {
        uint64_t v = 0;
        memcpy(&v, h + i, 8);
        bits += popcount64(v);
    }
    return bits;
}

static double elapsed_sec(struct timespec a, struct timespec b) {
    double s  = (double)(b.tv_sec  - a.tv_sec);
    double ns = (double)(b.tv_nsec - a.tv_nsec) / 1e9;
    return s + ns;
}

// --------------------------------------------------------------------------
// 2. Núcleo FIBER local (gera 64-bit, depois estende para 256-bit)
// --------------------------------------------------------------------------

typedef enum {
    FIBER_MODE_SCALAR = 0,
    FIBER_MODE_LANES6 = 1
} fiber_mode_t;

static fiber_mode_t fiber_decide_mode_local(int block_size)
{
    if (block_size <= 64)  return FIBER_MODE_SCALAR;
    if (block_size >= 1024) return FIBER_MODE_LANES6;
    if (block_size < 128)  return FIBER_MODE_SCALAR;
    return FIBER_MODE_LANES6;
}

#define FIB_PRIME1 0x9E3779B97F4A7C15ULL
#define FIB_PRIME2 0xD6E8FEB86659FD93ULL
#define FIB_PRIME3 0xA4093822299F31D0ULL

static inline uint64_t mix64_tree(uint64_t x) {
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

static uint64_t fiber_hash_leaf(const uint8_t *data,
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

static uint64_t fiber_reduce_tree(uint64_t *nodes,
                                  size_t    count,
                                  int       block_size)
{
    if (count == 0) return 0ULL;

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

static uint64_t hash_fiber_tree64(const uint8_t *data,
                                  size_t         len,
                                  int            block_size)
{
    if (!data || len == 0 || block_size <= 0) return 0ULL;

    size_t bs = (size_t)block_size;
    size_t num_leaves = (len + bs - 1u) / bs;
    if (num_leaves == 0) return 0ULL;

    uint64_t *leaf_hashes = (uint64_t *)malloc(num_leaves * sizeof(uint64_t));
    if (!leaf_hashes) {
        fprintf(stderr, "hash_fiber_tree64: malloc falhou\n");
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

// Estende 64 bits para 256 usando mix em cadeia (para comparar estrutura)
static void hash_fiber_256(const uint8_t *data, size_t len, uint8_t out[32]) {
    uint64_t h = hash_fiber_tree64(data, len, 1024);
    uint64_t v0 = mix64_tree(h ^ 0x0123456789ABCDEFULL);
    uint64_t v1 = mix64_tree(h ^ 0xFEDCBA9876543210ULL);
    uint64_t v2 = mix64_tree(h ^ 0xA5A5A5A5A5A5A5A5ULL);
    uint64_t v3 = mix64_tree(h ^ 0x5A5A5A5A5A5A5A5AULL);
    memcpy(out +  0, &v0, 8);
    memcpy(out +  8, &v1, 8);
    memcpy(out + 16, &v2, 8);
    memcpy(out + 24, &v3, 8);
}

// --------------------------------------------------------------------------
// 3. Wrappers BLAKE3-256 e SHA3-256
// --------------------------------------------------------------------------

static void hash_blake3_256(const uint8_t *data, size_t len, uint8_t out[32]) {
    blake3_hasher hasher;
    blake3_hasher_init(&hasher);
    blake3_hasher_update(&hasher, data, len);
    blake3_hasher_finalize(&hasher, out, 32);
}

static void hash_sha3_256(const uint8_t *data, size_t len, uint8_t out[32]) {
    EVP_MD_CTX *ctx = EVP_MD_CTX_new();
    if (!ctx) {
        fprintf(stderr, "hash_sha3_256: EVP_MD_CTX_new falhou\n");
        memset(out, 0, 32);
        return;
    }
    const EVP_MD *md = EVP_sha3_256();
    if (!md) {
        fprintf(stderr, "hash_sha3_256: EVP_sha3_256 indisponivel\n");
        EVP_MD_CTX_free(ctx);
        memset(out, 0, 32);
        return;
    }
    if (EVP_DigestInit_ex(ctx, md, NULL) != 1 ||
        EVP_DigestUpdate(ctx, data, len) != 1) {
        fprintf(stderr, "hash_sha3_256: DigestInit/Update falhou\n");
        EVP_MD_CTX_free(ctx);
        memset(out, 0, 32);
        return;
    }
    unsigned int outlen = 0;
    if (EVP_DigestFinal_ex(ctx, out, &outlen) != 1 || outlen != 32) {
        fprintf(stderr, "hash_sha3_256: DigestFinal falhou\n");
        memset(out, 0, 32);
    }
    EVP_MD_CTX_free(ctx);
}

// --------------------------------------------------------------------------
// 4. Estrutura genérica de teste
// --------------------------------------------------------------------------

typedef void (*hash256_fn)(const uint8_t*, size_t, uint8_t[32]);

typedef struct {
    const char *name;
    hash256_fn  fn;
} hash_impl_t;

// Avalanche em 256-bit
static void run_avalanche_256(const hash_impl_t *impl,
                              size_t msg_len,
                              size_t rounds)
{
    printf("=== AVALANCHE (256-bit) – %s ===\n", impl->name);
    printf("  msg_len = %zu, rounds = %zu\n", msg_len, rounds);

    uint8_t *base = (uint8_t *)malloc(msg_len);
    uint8_t *mut  = (uint8_t *)malloc(msg_len);
    uint8_t h0[32], h1[32];

    if (!base || !mut) {
        fprintf(stderr, "malloc falhou em run_avalanche_256\n");
        free(base); free(mut);
        return;
    }

    uint64_t total_pairs = 0;
    uint64_t total_bits  = 0;
    int min_dist = 256;
    int max_dist = 0;

    size_t bits_per_msg = msg_len * 8;

    for (size_t r = 0; r < rounds; ++r) {
        fill_random(base, msg_len);
        impl->fn(base, msg_len, h0);

        for (size_t bit = 0; bit < bits_per_msg; ++bit) {
            memcpy(mut, base, msg_len);
            size_t byte_index = bit / 8;
            uint8_t mask      = (uint8_t)(1u << (bit % 8));
            mut[byte_index]  ^= mask;

            impl->fn(mut, msg_len, h1);

            uint8_t x[32];
            for (int i = 0; i < 32; ++i) {
                x[i] = h0[i] ^ h1[i];
            }
            int dist = popcount256(x);

            if (dist < min_dist) min_dist = dist;
            if (dist > max_dist) max_dist = dist;

            total_pairs++;
            total_bits += (uint64_t)dist;
        }
    }

    double avg = (total_pairs > 0)
        ? (double)total_bits / (double)total_pairs
        : 0.0;

    printf("  total_pairs         = %" PRIu64 "\n", total_pairs);
    printf("  avg distance (bits) = %.4f (esperado ~128.0)\n", avg);
    printf("  min                 = %d\n", min_dist);
    printf("  max                 = %d\n\n", max_dist);

    free(base); free(mut);
}

// Monte Carlo: bit balance 256-bit
static void run_montecarlo_256(const hash_impl_t *impl,
                               size_t max_len,
                               size_t rounds)
{
    printf("=== MONTE CARLO (256-bit) – %s ===\n", impl->name);
    printf("  max_len = %zu, rounds = %zu\n", max_len, rounds);

    uint8_t *buf = (uint8_t *)malloc(max_len);
    uint8_t h[32];
    if (!buf) {
        fprintf(stderr, "malloc falhou em run_montecarlo_256\n");
        return;
    }

    uint64_t total_bits = 0;

    for (size_t r = 0; r < rounds; ++r) {
        size_t len = 1 + (size_t)(rng_u64() % max_len);
        fill_random(buf, len);
        impl->fn(buf, len, h);
        total_bits += (uint64_t)popcount256(h);
    }

    free(buf);

    double avg_bits = (rounds > 0)
        ? (double)total_bits / (double)rounds
        : 0.0;
    double frac = avg_bits / 256.0;

    printf("  avg bits set/hash   = %.4f (esperado ~128.0)\n", avg_bits);
    printf("  frac bits set       = %.4f (esperado ~0.5)\n\n", frac);
}

// Speed test
#ifndef CPU_MHz
#define CPU_MHz 2800.0
#endif

static void run_speed_256(const hash_impl_t *impl,
                          size_t msg_len,
                          size_t total_mib)
{
    printf("=== SPEED – %s ===\n", impl->name);
    printf("  msg_len = %zu, total_mib = %zu\n", msg_len, total_mib);

    uint8_t *buf = (uint8_t *)malloc(msg_len);
    uint8_t out[32];
    if (!buf) {
        fprintf(stderr, "malloc falhou em run_speed_256\n");
        return;
    }
    fill_random(buf, msg_len);

    const size_t total_bytes_target = total_mib * 1024ULL * 1024ULL;
    size_t iters = total_bytes_target / msg_len;
    if (iters == 0) iters = 1;
    size_t effective_bytes = iters * msg_len;

    struct timespec t0, t1;
    clock_gettime(CLOCK_MONOTONIC, &t0);

    for (size_t i = 0; i < iters; ++i) {
        impl->fn(buf, msg_len, out);
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
    printf("  cycles/byte     = %.3f (CPU_MHz=%.1f)\n\n",
           cpb, (double)CPU_MHz);
}

// Integridade básica
static void run_integrity_256(const hash_impl_t *impl)
{
    printf("=== INTEGRIDADE – %s ===\n", impl->name);

    uint8_t a[64], b[64], c[64];
    uint8_t ha1[32], ha2[32], hc[32];

    memset(a, 0x11, sizeof(a));
    memset(b, 0x11, sizeof(b));
    memset(c, 0x11, sizeof(c));
    c[0] ^= 0x01;

    impl->fn(a, sizeof(a), ha1);
    impl->fn(b, sizeof(b), ha2);
    impl->fn(c, sizeof(c), hc);

    int same_ab = (memcmp(ha1, ha2, 32) == 0);
    int same_ac = (memcmp(ha1, hc, 32) == 0);

    printf("  hash(a)[0..7]  = %02X%02X%02X%02X%02X%02X%02X%02X\n",
        ha1[0],ha1[1],ha1[2],ha1[3],ha1[4],ha1[5],ha1[6],ha1[7]);
    printf("  hash(a')[0..7] = %02X%02X%02X%02X%02X%02X%02X%02X\n",
        ha2[0],ha2[1],ha2[2],ha2[3],ha2[4],ha2[5],ha2[6],ha2[7]);
    printf("  hash(c)[0..7]  = %02X%02X%02X%02X%02X%02X%02X%02X\n",
        hc[0],hc[1],hc[2],hc[3],hc[4],hc[5],hc[6],hc[7]);

    printf("  [Consistencia] hash(a) == hash(a') ? %s\n",
           same_ab ? "SIM" : "NAO");
    printf("  [Diferenciacao] hash(a) != hash(c) ? %s\n\n",
           (!same_ac) ? "SIM" : "NAO");
}

// --------------------------------------------------------------------------
// 5. CLI / main
// --------------------------------------------------------------------------

static void print_usage(const char *prog) {
    printf("Uso:\n");
    printf("  %s           # roda todos os testes para FIBER, BLAKE3, SHA3-256\n", prog);
    printf("  %s fiber     # apenas FIBER\n", prog);
    printf("  %s blake3    # apenas BLAKE3\n", prog);
    printf("  %s sha3      # apenas SHA3-256\n", prog);
}

int main(int argc, char *argv[])
{
    const size_t AV_MSG_LEN = 256;
    const size_t AV_ROUNDS  = 8;      // 8 msgs base -> já gera bastante par

    const size_t MC_MAX_LEN = 2048;
    const size_t MC_ROUNDS  = 5000;   // pode ajustar pra mais/menos

    const size_t SP_MSG_LEN = 1024;
    const size_t SP_MIB     = 256;    // mesmo padrão que você já usou

    hash_impl_t impls[] = {
        { "FIBER-256",  hash_fiber_256  },
        { "BLAKE3-256", hash_blake3_256 },
        { "SHA3-256",   hash_sha3_256   },
    };
    const size_t N_IMPLS = sizeof(impls) / sizeof(impls[0]);

    int mode = 0; // 0 = all, 1=fiber, 2=blake3, 3=sha3
    if (argc >= 2) {
        if      (strcmp(argv[1], "fiber")  == 0) mode = 1;
        else if (strcmp(argv[1], "blake3") == 0) mode = 2;
        else if (strcmp(argv[1], "sha3")   == 0) mode = 3;
        else {
            print_usage(argv[0]);
            return 1;
        }
    }

    for (size_t i = 0; i < N_IMPLS; ++i) {
        if ((mode == 1 && i != 0) ||
            (mode == 2 && i != 1) ||
            (mode == 3 && i != 2)) {
            continue;
        }

        printf("============================================================\n");
        printf("### HASH: %s\n", impls[i].name);
        printf("============================================================\n");

        run_avalanche_256(&impls[i], AV_MSG_LEN, AV_ROUNDS);
        run_montecarlo_256(&impls[i], MC_MAX_LEN, MC_ROUNDS);
        run_speed_256(&impls[i], SP_MSG_LEN, SP_MIB);
        run_integrity_256(&impls[i]);
    }

    return 0;
}
