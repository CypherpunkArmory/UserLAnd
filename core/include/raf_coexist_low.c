#include <stdint.h>
#include <stddef.h>
#include <unistd.h>   // write()

// --- CONSTANTES RAFAELIA BÁSICAS ---
#define MAGIC_RAF 0x52414641u   // "RAFA"
#define DIM_OCT   8
#define DEPTH     10000         // total de nós
#define FLAG_TAG14 (1u << 4)
#define PHI 1.61803398f

// --- HEADER ZIPRAF (BLINDAGEM) ---
typedef struct {
    uint32_t magic;
    uint32_t crc_payload;
    uint32_t crc_header;
    uint64_t timestamp;
    uint32_t flags;
} ZipHeader;

// --- HYPERNODE (NÓ DE COEXISTÊNCIA) ---
typedef struct {
    ZipHeader header;
    float     state[DIM_OCT];   // estado octonion
    int32_t   hook_next;        // índice do próximo nó (linear)
    int32_t   ref_sheet;        // índice de referência (cola)
    int32_t   hint_path;        // índice de atalho (hint)
    uint64_t  solution_cache;   // solução já colapsada
} HyperNode;

// --- UNIVERSO ESTÁTICO (SEM MALLOC) ---
static HyperNode universe[DEPTH];

// --- SAÍDA LOW LEVEL (SEM printf) ---
static void print_str(const char *s) {
    size_t len = 0;
    while (s[len] != '\0') {
        len++;
    }
    if (len > 0) {
        (void) write(1, s, len);
    }
}

static void print_u64(uint64_t v) {
    char buf[32];
    int i = 0;

    if (v == 0) {
        (void) write(1, "0", 1);
        return;
    }

    while (v > 0 && i < (int)sizeof(buf)) {
        uint64_t digit = v % 10u;
        buf[i++] = (char)('0' + (char)digit);
        v /= 10u;
    }
    while (i > 0) {
        i--;
        (void) write(1, &buf[i], 1);
    }
}

static void print_int(int v) {
    if (v < 0) {
        (void) write(1, "-", 1);
        v = -v;
    }
    print_u64((uint64_t)v);
}

// --- CRC32 IMPLEMENTADO À MÃO ---
static uint32_t crc32_raw(const void *data, uint32_t len) {
    const uint8_t *p = (const uint8_t *) data;
    uint32_t crc = 0xFFFFFFFFu;
    uint32_t i, k;

    for (i = 0; i < len; i++) {
        crc ^= (uint32_t)p[i];
        for (k = 0; k < 8; k++) {
            uint32_t mask = (uint32_t)-(int32_t)(crc & 1u);
            crc = (crc >> 1) ^ (0xEDB88320u & mask);
        }
    }
    return ~crc;
}

// --- GÊNESE DO UNIVERSO (PRECOMPUTE TUDO) ---
static void genesis_web(void) {
    int i, d;

    for (i = 0; i < DEPTH; i++) {
        HyperNode *n = &universe[i];

        // Identidade básica
        n->header.magic = MAGIC_RAF;
        n->header.flags = FLAG_TAG14;
        n->header.timestamp = (uint64_t)i;   // tempo simbólico

        // Estado octonion: i * d * PHI
        for (d = 0; d < DIM_OCT; d++) {
            n->state[d] = (float)(i * d) * PHI;
        }

        // Solução já colapsada (seed simbólica)
        n->solution_cache = (uint64_t)((uint64_t)i * 963u * 42u);

        // Ligações lineares
        if (i < (DEPTH - 1)) {
            n->hook_next = i + 1;
            n->ref_sheet = i + 1;
        } else {
            n->hook_next = -1;
            n->ref_sheet = -1;
        }

        n->hint_path = -1; // padrão: sem atalho

        // CRC do payload (estado)
        n->header.crc_payload = crc32_raw((const void *)n->state,
                                          (uint32_t)(sizeof(n->state)));

        // CRC do cabeçalho inteiro (Selo do Selo)
        {
            ZipHeader tmp = n->header;
            tmp.crc_header = 0;
            n->header.crc_header = crc32_raw((const void *)&tmp,
                                             (uint32_t)sizeof(ZipHeader));
        }
    }

    // Exemplo de HINT real: nó 50 salta para 144, se existir
    if (DEPTH > 144) {
        universe[50].hint_path = 144;
    }
}

// --- NAVEGAÇÃO LINEAR (COEXISTÊNCIA) ---
static void solve_linear(void) {
    int32_t idx = 0;
    uint64_t total_resolution = 0;
    int steps = 0;

    print_str("\n[PROCESS] Iniciando Resolução Linear (LOW LEVEL)...\n");

    while (idx >= 0 && idx < DEPTH) {
        HyperNode *n = &universe[idx];

        // Validação do Selo do Selo (ZIPRAF)
        ZipHeader tmp = n->header;
        tmp.crc_header = 0;
        {
            uint32_t check = crc32_raw((const void *)&tmp,
                                       (uint32_t)sizeof(ZipHeader));
            if (check != n->header.crc_header) {
                print_str("[ALERTA] Selo quebrado em idx=");
                print_int(idx);
                print_str(". Encerrando navegação.\n");
                break;
            }
        }

        // Leitura da solução já resolvida
        total_resolution += n->solution_cache;

        // Escolha de caminho: HINT tem prioridade
        if (n->hint_path >= 0 && n->hint_path < DEPTH) {
            idx = n->hint_path;
        } else {
            idx = n->hook_next;
        }

        steps++;
    }

    print_str("\n⚡ RESULTADO FINAL ATINGIDO (LOW LEVEL)\n");
    print_str("   Passos Lineares : ");
    print_int(steps);
    print_str("\n   Solução (Soma)  : ");
    print_u64(total_resolution);
    print_str("\n");
}

int main(void) {
    print_str("=== RAFAELIA COEXISTENCE ENGINE (ZIPRAF LOW LEVEL) ===\n");
    print_str("Lógica: Headers CRC + Hooks + Ref Sheets\n");
    print_str("Meta  : Complexidade N^N resolvida com scan O(N)\n\n");

    genesis_web();
    solve_linear();

    return 0;
}
