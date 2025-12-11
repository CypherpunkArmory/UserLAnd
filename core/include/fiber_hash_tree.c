// ============================================================================
// RAFAELIA FIBER-H HASH TREE (v999 – Standalone)
//  - Modo árvore sobre blocos de dados (Merklization simplificada)
//  - Lógica de decisão T1 (scalar vs LANES6) embutida (espelho do kernel)
// ============================================================================

#define _POSIX_C_SOURCE 199309L
#include <stdio.h>
#include <stdint.h>
#include <stdbool.h>
#include <stdlib.h>
#include <inttypes.h>

// --------------------------------------------------------------------------
// 1. Tipos e decisão de modo (T1 local, espelhando o kernel)
// --------------------------------------------------------------------------

typedef enum {
    FIBER_MODE_SCALAR = 0,
    FIBER_MODE_LANES6 = 1
} fiber_mode_t;

/*
 * Regra de decisão alinhada ao kernel/decide script:
 *  - B <= 64   → scalar (singularidade de cache)
 *  - B >= 1024 → LANES6 (throughput e estabilidade)
 *  - zona intermediária:
 *      B < 128 → scalar
 *      caso contrário → LANES6
 */
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

// --------------------------------------------------------------------------
// 2. Constantes e mix64 (difusão forte, alinhado ao núcleo RAFAELIA)
// --------------------------------------------------------------------------

#define FIB_PRIME1 0x9E3779B97F4A7C15ULL
#define FIB_PRIME2 0xD6E8FEB86659FD93ULL
#define FIB_PRIME3 0xA4093822299F31D0ULL

// Ciclo de confusão/difusão forte
static inline uint64_t
mix64_tree(uint64_t x)
{
    x ^= x >> 31;
    x *= 0x9e3779b97f4a7c15ULL;           // Golden Ratio base
    x ^= x >> 23;
    x *= 0xff51afd7ed558ccdULL;           // multiplicação não-linear
    x ^= x >> 39;
    x += 0xc4ceb9fe1a85ec53ULL;           // confusão aditiva
    x = (x << 13) | (x >> (64 - 13));     // rotação densa
    x ^= x >> 47;                         // difusão de longo alcance
    return x;
}

// --------------------------------------------------------------------------
// 3. Hash de folha (leaf) – nível 0 da árvore
// --------------------------------------------------------------------------
//
// Cada folha:
//  - seed depende de: block_size, índice da folha, modo T1
//  - mistura todos os bytes com mix64_tree
// --------------------------------------------------------------------------

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

// --------------------------------------------------------------------------
// 4. Redução em árvore – Merkle-like sobre hashes de folhas
// --------------------------------------------------------------------------

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
                // número de nós ímpar → espelho temperado
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

// --------------------------------------------------------------------------
// 5. API pública – hash em árvore (buffer em memória)
// --------------------------------------------------------------------------

uint64_t
fiber_hash_tree_u64(const uint8_t *data,
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
                "fiber_hash_tree_u64: malloc falhou (num_leaves=%zu)\n",
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

uint8_t
fiber_hash_tree_u8(const uint8_t *data,
                   size_t         len,
                   int            block_size)
{
    uint64_t h = fiber_hash_tree_u64(data, len, block_size);
    return (uint8_t)(h & 0xFFu);
}

// --------------------------------------------------------------------------
// 6. CLI – hash de arquivo em modo árvore
// --------------------------------------------------------------------------

static void
fiber_hash_tree_print_usage(const char *prog)
{
    printf("Uso:\n");
    printf("  %s FILE BLOCK_SIZE\n", prog);
    printf("\n");
    printf("Exemplo:\n");
    printf("  %s fiber_stress_lab.c 1024\n", prog);
}

int main(int argc, char *argv[])
{
    if (argc != 3) {
        fiber_hash_tree_print_usage(argv[0]);
        return 1;
    }

    const char *path = argv[1];
    int block_size   = atoi(argv[2]);
    if (block_size <= 0) {
        fprintf(stderr, "ERRO: BLOCK_SIZE invalido: %s\n", argv[2]);
        return 1;
    }

    FILE *f = fopen(path, "rb");
    if (!f) {
        perror("fopen");
        return 1;
    }

    if (fseek(f, 0, SEEK_END) != 0) {
        perror("fseek");
        fclose(f);
        return 1;
    }
    long fsize = ftell(f);
    if (fsize < 0) {
        perror("ftell");
        fclose(f);
        return 1;
    }
    if (fseek(f, 0, SEEK_SET) != 0) {
        perror("fseek");
        fclose(f);
        return 1;
    }

    size_t len = (size_t)fsize;
    if (len == 0) {
        fclose(f);
        printf("Arquivo vazio. Hash = 0.\n");
        return 0;
    }

    uint8_t *buf = (uint8_t *)malloc(len);
    if (!buf) {
        fprintf(stderr,
                "malloc falhou ao alocar %zu bytes\n",
                len);
        fclose(f);
        return 1;
    }

    size_t read_bytes = fread(buf, 1, len, f);
    fclose(f);

    if (read_bytes != len) {
        fprintf(stderr,
                "Erro de leitura: esperado %zu bytes, obtido %zu\n",
                len, read_bytes);
        free(buf);
        return 1;
    }

    size_t bs         = (size_t)block_size;
    size_t num_leaves = (len + bs - 1u) / bs;
    fiber_mode_t mode = fiber_decide_mode_local(block_size);

    uint64_t h64 = fiber_hash_tree_u64(buf, len, block_size);
    uint8_t  h8  = (uint8_t)(h64 & 0xFFu);

    free(buf);

    printf("FIBER-H HASH TREE (RAFAELIA v999 – Standalone)\n");
    printf("  File        : %s\n", path);
    printf("  Size        : %zu bytes\n", len);
    printf("  BlockSize   : %d bytes\n", block_size);
    printf("  Leaves      : %zu\n", num_leaves);
    printf("  Mode(T1)    : %s\n",
           (mode == FIBER_MODE_SCALAR) ? "FIBER-H scalar" : "FIBER-H LANES6");
    printf("  Hash64(root): 0x%016" PRIx64 "\n", h64);
    printf("  Hash8(root) : 0x%02X\n", h8);

    return 0;
}
