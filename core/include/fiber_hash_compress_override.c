#include "fiber_hash.h"

#if defined(__aarch64__)
extern void fiber_h_compress_asm_real(fiber_u64 *state, const fiber_u8 block[64]);
#endif

// Override / wrapper para o core de compressão.
// Em AArch64 usamos ASM; em outras arquiteturas, você mantém a versão em C.
void fiber_h_compress_block_override(fiber_u64 *a, fiber_u64 *b,
                                     fiber_u64 *c, fiber_u64 *d,
                                     const fiber_u8 block[64]) {
#if defined(__aarch64__)
    fiber_u64 st[4];
    st[0] = *a;
    st[1] = *b;
    st[2] = *c;
    st[3] = *d;

    fiber_h_compress_asm_real(st, block);

    *a = st[0];
    *b = st[1];
    *c = st[2];
    *d = st[3];
#else
    // Aqui você pode colar a SUA versão C atual de fiber_h_compress_block,
    // caso queira manter compatibilidade com x86, etc.
    (void)a; (void)b; (void)c; (void)d; (void)block;
    // placeholder para não quebrar o build fora de aarch64
#endif
}
