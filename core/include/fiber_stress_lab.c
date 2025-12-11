// (mantém todo o resto do código igual – só mostro a parte crítica)

#include <stdint.h>

// POPCNT genérico – o compilador vai gerar instruções ARM64 nativas
static inline int popcnt64_u64(uint64_t x) {
    return __builtin_popcountll((unsigned long long)x);
}

// Conta bits em um vetor de 32 bytes (256 bits) via 4 words de 64 bits
static int hamming_distance_256(const uint8_t a[32], const uint8_t b[32]) {
    uint64_t a0, a1, a2, a3;
    uint64_t b0, b1, b2, b3;

    memcpy(&a0, a + 0, 8);
    memcpy(&a1, a + 8, 8);
    memcpy(&a2, a + 16, 8);
    memcpy(&a3, a + 24, 8);

    memcpy(&b0, b + 0, 8);
    memcpy(&b1, b + 8, 8);
    memcpy(&b2, b + 16, 8);
    memcpy(&b3, b + 24, 8);

    int dist = 0;
    dist += popcnt64_u64(a0 ^ b0);
    dist += popcnt64_u64(a1 ^ b1);
    dist += popcnt64_u64(a2 ^ b2);
    dist += popcnt64_u64(a3 ^ b3);

    return dist;
}

// No monobit, mesma ideia:
static void run_monobit_test(size_t samples, size_t max_msg_len) {
    // ...
    uint64_t w0, w1, w2, w3;
    memcpy(&w0, hash + 0, 8);
    memcpy(&w1, hash + 8, 8);
    memcpy(&w2, hash + 16, 8);
    memcpy(&w3, hash + 24, 8);

    ones += popcnt64_u64(w0);
    ones += popcnt64_u64(w1);
    ones += popcnt64_u64(w2);
    ones += popcnt64_u64(w3);
    // ...
}
