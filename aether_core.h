/*
 * AETHER-X Core Engine
 * Núcleo de hash não-criptográfico para benchmark e integridade.
 * Autor: Rafael Melo Reis (∆RafaelVerboΩ)
 * RAFCODE-Φ / RAFAELIA Core
 */

#ifndef AETHER_CORE_H
#define AETHER_CORE_H

#include <stdint.h>
#include <stddef.h>
#include <string.h>

/* Inline/Align helpers */
#if defined(_MSC_VER)
    #define AETHER_FORCE_INLINE __forceinline
    #define AETHER_ALIGNED(x) __declspec(align(x))
#else
    #define AETHER_FORCE_INLINE __attribute__((always_inline)) inline
    #define AETHER_ALIGNED(x) __attribute__((aligned(x)))
#endif

/* --- Rotação básica de 64 bits --- */
static AETHER_FORCE_INLINE uint64_t aether_rotl64(uint64_t x, int8_t r) {
    return (uint64_t)((x << r) | (x >> (64 - r)));
}

/* --- STANDARD: byte-a-byte (referência lenta) --- */
#if defined(__GNUC__) || defined(__clang__)
__attribute__((optimize("O1")))
#endif
static inline uint64_t aether_standard_hash(const void* buffer, size_t len) {
    const uint8_t* data = (const uint8_t*)buffer;
    uint64_t hash  = 0xCBF29CE484222325ULL;
    uint64_t prime = 0x100000001B3ULL;

    for (size_t i = 0; i < len; i++) {
        hash ^= data[i];
        hash *= prime;
    }
    return hash;
}

/* --- HYPER: palavra de 64 bits + avalanche --- */
static inline uint64_t aether_hyper_hash(const void* buffer, size_t len) {
    const uint8_t* data = (const uint8_t*)buffer;
    uint64_t hash  = 0xCBF29CE484222325ULL;
    uint64_t prime = 0x100000001B3ULL;

    size_t n_blocks = len / 8;
    size_t i = 0;

    for (; i < n_blocks; i++) {
        uint64_t k;
        /* Seguro para desalinhado; o compilador otimiza pra load direto */
        memcpy(&k, data + (i * 8), 8);

        hash ^= k;
        hash *= prime;
        hash = aether_rotl64(hash, 31);
        hash ^= (hash >> 33);
    }

    size_t tail_idx = i * 8;
    while (tail_idx < len) {
        hash ^= data[tail_idx];
        hash *= prime;
        tail_idx++;
    }

    return hash;
}

#endif /* AETHER_CORE_H */
