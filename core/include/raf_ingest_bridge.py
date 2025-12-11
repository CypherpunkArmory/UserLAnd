#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
RAFAELIA :: INGEST BRIDGE (OMNI → VETOR)
========================================
Função: Ler engramas do RAFAELIA OMNI CORE v107 e alimentar a memória vetorial
(ia_vetor.IAVetor) com resumos estruturados.

Uso:
  python3 raf_ingest_bridge.py             # usa caminho padrão
  python3 raf_ingest_bridge.py /caminho/personalizado/omni_memory.jsonl
"""

import json
import os
import sys
from pathlib import Path
from datetime import datetime, timezone

try:
    from ia_vetor import IAVetor
except ImportError as e:
    print(f"[BRIDGE] ERRO: não foi possível importar IAVetor de ia_vetor.py: {e}", file=sys.stderr)
    sys.exit(1)

DEFAULT_OMNI_PATH = Path("aprendizado") / "sistema" / "omni_memory.jsonl"

def load_engrams(path: Path):
    """Carrega engramas JSONL do OMNI (stream)."""
    if not path.exists():
        print(f"[BRIDGE] Arquivo OMNI não encontrado: {path}", file=sys.stderr)
        return
    with open(path, "r", encoding="utf-8") as f:
        for line in f:
            line = line.strip()
            if not line:
                continue
            try:
                yield json.loads(line)
            except Exception:
                continue

def engram_to_text(e: dict) -> str:
    """
    Converte um Engram em um texto compacto para alimentar IAVetor.
    Mantém estrutura sem vazar dados sensíveis.
    """
    etype = e.get("type", "unknown")
    eid = e.get("id", "N/A")
    keys = []
    meta = e.get("metadata", {})
    if isinstance(meta, dict):
        if "keys" in meta and isinstance(meta["keys"], list):
            keys = meta["keys"]
    parts = [
        f"[OMNI_ENGRAM]",
        f"id={eid}",
        f"type={etype}",
    ]
    if keys:
        parts.append(f"keys={','.join(str(k) for k in keys)}")
    else:
        # fallback resumido
        parts.append(f"meta_keys={','.join(meta.keys())}" if isinstance(meta, dict) else "")
    return " | ".join(p for p in parts if p)

def main() -> None:
    if len(sys.argv) > 1:
        omni_path = Path(sys.argv[1])
    else:
        omni_path = DEFAULT_OMNI_PATH

    print(f"[BRIDGE] Lendo engramas de: {omni_path}")
    engine = IAVetor()  # usa o mesmo path padrão de ia_vetor

    count = 0
    for e in load_engrams(omni_path):
        text = engram_to_text(e)
        if not text:
            continue
        engine.process_input(
            session_id="omni_bridge",
            text=text,
            cls="omni",
            assinatura="OMNI_ENGRAM",
            log_event=True,
        )
        count += 1
        if count % 500 == 0:
            print(f"[BRIDGE] {count} engramas enviados para IAVetor...")

    print(f"[BRIDGE] Concluído. Total de engramas enviados: {count}")

if __name__ == "__main__":
    main()
