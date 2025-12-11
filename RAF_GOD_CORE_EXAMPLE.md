# Exemplo do melhor núcleo RAFAELIA ("GOD" core)

Este repositório já inclui o núcleo benchmark **raf_god_core.c** (versão v1005).
O script abaixo compila esse núcleo com flags agressivas e gera um binário pronto
para testes interativos ou para o modo bridge (saída JSON mínima).

## Como compilar

```sh
./scripts/build_raf_god_core.sh
```

O binário resultante fica em `build/bin/raf_god_core_example`.

## Como usar

### Modo interativo (dashboard)

```sh
./build/bin/raf_god_core_example
```

Controles principais:

* `1` – ajustar `N` (dimensão do universo).
* `2` – ajustar RAM alvo em MB (realoca o tensor alinhado).
* `3` – simular 1000 passos (injeta energia pseudoaleatória e processa física).
* `B` – rodar benchmark de 1s e exibir IOPS/banda.
* `0` – sair e liberar memória.

### Modo bridge (JSON)

Para integrar com observabilidade ou pipelines automatizados, use:

```sh
./build/bin/raf_god_core_example --bridge
```

O bridge imprime frames JSON compactos conforme o núcleo evolui, sem precisar
interagir pelo console.

## Por que este exemplo

* Usa o núcleo mais completo/otimizado disponível no repositório como referência
  de desempenho.
* Flags C pensadas para footprint e IOPS (`-std=c11 -O3 -DNDEBUG -fvisibility=hidden`).
* Saída consistente: dashboard textual para tuning rápido e bridge JSON para
  consumo por scripts ou runtimes externos.
