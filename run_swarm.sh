#!/bin/bash

# Configuração
MAX_WORKERS=12      # Quantos rodam AO MESMO TEMPO (Saturação dos 8 cores + fila)
TOTAL_JOBS=1000000  # Meta infinita de jobs
TARGET_PETA=1000000 # Meta de 1 Milhão de Gigaflops (1 Peta)

echo -e "\033[2J\033[H"
echo -e "\x1b[32m=== RAFAELIA SWARM: PROCESS STACKER ===\x1b[0m"
echo "Target: EMPILHAR ATÉ 1 PETAFLOP"
echo "Worker Load: ~2.7 GigaFlops por processo"
echo "-------------------------------------------"

current_gflops=0
running=0

# Loop Infinito de Geração de Processos
for (( i=1; i<=$TOTAL_JOBS; i++ ))
do
    # Lança um Operário em Background
    ./swarm_worker $i &
    
    # Controle de visualização
    echo -ne "\r🚀 Lançando Operário #$i \t [Ativos: $(jobs -r | wc -l)]"
    
    # Se atingir o limite de processos simultâneos, espera um pouco
    while [ $(jobs -r | wc -l) -ge $MAX_WORKERS ]; do
        sleep 0.1
    done
    
    # A cada 10 lançamentos, atualiza a contabilidade da pilha
    if (( i % 5 == 0 )); then
        # Soma tudo que está no arquivo ledger
        if [ -f swarm_ledger.dat ]; then
            total_stack=$(awk '{s+=$1} END {print s}' swarm_ledger.dat)
            
            # Cálculo de Porcentagem para o Peta
            # 1 Peta = 1.000.000 Gigaflops
            perc=$(echo "$total_stack / 10000" | bc -l)
            
            echo -ne "\033[2K\r" # Limpa linha
            echo -e "\x1b[33m[PILHA]\x1b[0m Total: \x1b[36m$total_stack GigaFlops\x1b[0m acumulados (Rumo ao Peta)"
            echo -e "\x1b[31m[HEAT]\x1b[0m Processos Simultâneos: $MAX_WORKERS | Processo Atual: #$i"
            echo "-------------------------------------------"
            echo -ne "\033[3A" # Sobe cursor para reescrever
        fi
    fi
done
wait
