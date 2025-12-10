#include <stdio.h>
#include <stdlib.h>
#include <stdint.h>
#include <time.h>
#include <string.h>
#include <arm_acle.h> /* Acesso direto ao Hardware de CRC do ARM */

/* * ESTRUTURA DO ZIPRAF (O Protocolo)
 * ---------------------------------
 * [ HEADER (32 bytes) ]
 * |-- Magic "RAFA"
 * |-- Timestamp
 * |-- ID do Bloco
 * |-- CRC_INTERNO (Protege o Dado)
 * |-- CRC_EXTERNO (Protege o Próprio Header)
 * [ PAYLOAD (Dados Brutos) ]
 */

typedef struct {
    uint32_t magic;       // 0x52414641 (RAFA)
    uint64_t timestamp;   // Tempo da criação
    uint32_t block_id;    // Sequência
    uint32_t data_size;   // Tamanho do payload
    uint32_t crc_inner;   // SELO 1: O Recheio
    uint32_t crc_outer;   // SELO 2: A Casca (CRC sobre os campos acima)
} ZipRafHeader;

// --- FUNÇÃO CRC32 VIA HARDWARE (ARMv8) ---
// Isso roda em 0 ciclos de CPU (usa unidade dedicada)
uint32_t calc_crc32(const void *data, size_t len, uint32_t seed) {
    const uint8_t *p = (const uint8_t *)data;
    uint32_t crc = ~seed;
    
    while (len--) {
        crc = __crc32b(crc, *p++);
    }
    return ~crc;
}

// Simula a criação de um bloco de cálculo PETA
void create_and_seal_block(int id) {
    // 1. Criar Dados (Simulando resultado do cálculo)
    char payload[1024];
    sprintf(payload, "RESULTADO_DO_BLOCO_%d_RAFAELIA_PETA_STACK_XYZ", id);
    uint32_t p_len = strlen(payload);

    // 2. Calcular CRC Interno (Selo do Dado)
    uint32_t inner = calc_crc32(payload, p_len, 0);

    // 3. Montar Cabeçalho
    ZipRafHeader header;
    header.magic = 0x52414641; // RAFA
    header.timestamp = (uint64_t)time(NULL);
    header.block_id = id;
    header.data_size = p_len;
    header.crc_inner = inner;
    header.crc_outer = 0; // Ainda não calculado

    // 4. Calcular CRC Externo (Selo do Cabeçalho)
    // Calculamos o CRC de tudo na struct, EXCETO o próprio campo crc_outer
    uint32_t header_len_to_hash = sizeof(ZipRafHeader) - sizeof(uint32_t);
    header.crc_outer = calc_crc32(&header, header_len_to_hash, 0);

    // 5. Salvar no Disco (O Arquivo Zipado)
    char filename[32];
    sprintf(filename, "block_%d.zipraf", id);
    FILE *f = fopen(filename, "wb");
    fwrite(&header, sizeof(ZipRafHeader), 1, f);
    fwrite(payload, 1, p_len, f);
    fclose(f);

    printf("\n🔒 [BLOCO %d] SELADO.\n", id);
    printf("   └── Payload CRC: \x1b[33m%08X\x1b[0m (Interno)\n", header.crc_inner);
    printf("   └── Header  CRC: \x1b[32m%08X\x1b[0m (Externo - O Zíper)\n", header.crc_outer);
}

// Verifica se o Zíper está fechado ou estourado
void verify_block(int id) {
    char filename[32];
    sprintf(filename, "block_%d.zipraf", id);
    FILE *f = fopen(filename, "rb");
    if (!f) return;

    ZipRafHeader h;
    fread(&h, sizeof(ZipRafHeader), 1, f);

    // 1. Validar CRC Externo (A Casca)
    uint32_t header_len_to_hash = sizeof(ZipRafHeader) - sizeof(uint32_t);
    uint32_t calc_outer = calc_crc32(&h, header_len_to_hash, 0);

    printf("\n🔍 [AUDITORIA] Verificando Bloco %d...\n", id);
    
    if (calc_outer != h.crc_outer) {
        printf("   ❌ \x1b[31mALERTA CRÍTICO: O CABEÇALHO FOI VIOLADO!\x1b[0m\n");
        fclose(f);
        return;
    } else {
        printf("   ✅ Cabeçalho Íntegro (Zíper Fechado).\n");
    }

    // 2. Validar CRC Interno (O Recheio)
    char *buffer = malloc(h.data_size);
    fread(buffer, 1, h.data_size, f);
    uint32_t calc_inner = calc_crc32(buffer, h.data_size, 0);

    if (calc_inner != h.crc_inner) {
        printf("   ❌ \x1b[31mALERTA: O DADO ESTÁ CORROMPIDO!\x1b[0m\n");
    } else {
        printf("   ✅ Dados Puros (Integridade 100%%).\n");
    }

    free(buffer);
    fclose(f);
}

int main() {
    printf("\x1b[36m=== RAFAELIA ZIPPER (CRC on CRC) ===\x1b[0m\n");
    
    // Criar blocos
    create_and_seal_block(1);
    create_and_seal_block(2);

    // Verificar blocos
    verify_block(1);
    
    // SIMULAR UM ATAQUE HACKER NO BLOCO 2
    printf("\n⚠️  \x1b[33mSIMULANDO ATAQUE DE CORRUPÇÃO NO BLOCO 2...\x1b[0m\n");
    FILE *f = fopen("block_2.zipraf", "r+b");
    fseek(f, 20, SEEK_SET); // Vai no meio do cabeçalho
    fputc(0xFF, f); // Injeta lixo
    fclose(f);

    // Tentar verificar o bloco hackeado
    verify_block(2);

    return 0;
}
