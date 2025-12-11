#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
RAFAELIA :: HYPER CORE v27.0 (Massive Data & Visual Ingestion)
==============================================================
Arquitetura: Hyperdimensional Vector Computing (HDC) + Streaming Pipeline.
Capacidade: JSON 1GB+ (stream) e ingestão visual bruta por bytes.
Normas: ISO 25010 (Eficiência), NIST 800-53 (Audit), IEEE 12207 (Ciclo de Vida).

Author: Rafael Melo Reis Novo (∆RafaelVerboΩ)
License: RAFCODE-Φ
"""

import os
import sys
import json
import time
import math
import shutil
import logging
import hashlib
from pathlib import Path
from datetime import datetime, timezone
from typing import Dict, List, Generator, Any, Tuple
from dataclasses import dataclass, asdict
from array import array

# --- 1. LOGGING (ISO 27001 Audit) ---

logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s | %(levelname)s | HYPER | %(message)s',
    datefmt='%Y-%m-%d %H:%M:%S'
)
logger = logging.getLogger("RAFAELIA_HYPER_v27")

CONFIG = {
    "ROOT_DIR": "aprendizado",
    "SUB_DIRS": ["a_processar", "processando", "processado", "erro", "sub"],
    "VECTOR_DIM": 1024,                 # Hipervetor
    "MAX_BUFFER": 1024 * 1024 * 16,     # 16MB por chunk
}

# ---------------------------------------------------------------------------
# 2. HYPERVETORES (HDC)
# ---------------------------------------------------------------------------

class HyperVectorMath:
    """Matemática de hipervetores (vetor -> vetor)."""
    
    @staticmethod
    def cosine_similarity(v1: array, v2: array) -> float:
        if len(v1) != len(v2):
            return 0.0
        dot = sum(a * b for a, b in zip(v1, v2))
        mag1 = math.sqrt(sum(a * a for a in v1))
        mag2 = math.sqrt(sum(a * a for a in v2))
        if mag1 == 0.0 or mag2 == 0.0:
            return 0.0
        return dot / (mag1 * mag2)

    @staticmethod
    def generate_orthogonal(seed: str, dim: int) -> array:
        """Gera vetor pseudo-ortogonal determinístico baseado em seed (bipolar)."""
        h = hashlib.sha256(seed.encode("utf-8")).digest()
        vec = array("d", [0.0] * dim)
        for i in range(dim):
            byte_val = h[i % len(h)]
            vec[i] = float(byte_val - 128) / 128.0
        return vec

# ---------------------------------------------------------------------------
# 3. MEMÓRIA SINÁPTICA
# ---------------------------------------------------------------------------

@dataclass
class MemoryUnit:
    id: str
    type: str            # 'json_obj' ou 'image'
    vector: List[float]
    metadata: Dict[str, Any]
    timestamp: str

class SynapticMemory:
    """Memória associativa simples (append-only JSONL)."""
    
    def __init__(self, db_path: str = None) -> None:
        if db_path is None:
            root = Path(CONFIG["ROOT_DIR"])
            db_path = str(root / "sub" / "memory.jsonl")
        self.db_path = db_path
        self.memory: List[MemoryUnit] = []
        os.makedirs(os.path.dirname(self.db_path), exist_ok=True)
        self._load()
    
    def _load(self) -> None:
        if not os.path.exists(self.db_path):
            return
        try:
            with open(self.db_path, "r", encoding="utf-8") as f:
                for line in f:
                    line = line.strip()
                    if not line:
                        continue
                    try:
                        data = json.loads(line)
                        self.memory.append(MemoryUnit(**data))
                    except Exception:
                        continue
            logger.info(f"[MEM] Loaded {len(self.memory)} units from disk.")
        except Exception as e:
            logger.error(f"[MEM] Load error: {e}")

    def save_unit(self, unit: MemoryUnit) -> None:
        self.memory.append(unit)
        try:
            with open(self.db_path, "a", encoding="utf-8") as f:
                f.write(json.dumps(asdict(unit), ensure_ascii=False) + "\n")
        except Exception as e:
            logger.error(f"[MEM] Save error: {e}")

    def query(self, query_vec: array, top_k: int = 3) -> List[Tuple[MemoryUnit, float]]:
        res: List[Tuple[MemoryUnit, float]] = []
        for unit in self.memory:
            score = HyperVectorMath.cosine_similarity(query_vec, array("d", unit.vector))
            res.append((unit, score))
        res.sort(key=lambda x: x[1], reverse=True)
        return res[:top_k]

# ---------------------------------------------------------------------------
# 4. HANDLER DE ARQUIVOS GIGANTES
# ---------------------------------------------------------------------------

class MassiveFileHandler:
    """Stream de JSON gigante + ingestão de imagem via histograma de bytes."""
    
    @staticmethod
    def stream_json_objects(filepath: Path) -> Generator[Dict[str, Any], None, None]:
        """
        Lê JSONL ou JSON array gigantesco sem carregar tudo na RAM.
        """
        try:
            with open(filepath, "r", encoding="utf-8") as f:
                first_char = f.read(1)
                f.seek(0)
                
                # JSONL (cada linha é um objeto)
                if first_char == "{":
                    for line in f:
                        line = line.strip()
                        if not line:
                            continue
                        try:
                            yield json.loads(line)
                        except json.JSONDecodeError:
                            logger.warning(f"[JSONL] Invalid line in {filepath.name}, skipping.")
                
                # JSON array: [ { ... }, { ... }, ... ]
                elif first_char == "[":
                    buffer = ""
                    depth = 0
                    in_string = False
                    escape = False
                    while True:
                        chunk = f.read(CONFIG["MAX_BUFFER"])
                        if not chunk:
                            break
                        for ch in chunk:
                            buffer += ch
                            if in_string:
                                if not escape and ch == '"':
                                    in_string = False
                                escape = (ch == "\\" and not escape)
                            else:
                                if ch == '"':
                                    in_string = True
                                elif ch == "{":
                                    depth += 1
                                elif ch == "}":
                                    depth -= 1
                            if depth == 0 and ch == "}" and buffer.strip():
                                try:
                                    clean = buffer.strip().lstrip(",").rstrip(",")
                                    yield json.loads(clean)
                                    buffer = ""
                                except json.JSONDecodeError:
                                    # mantém buffer para tentar juntar com próximo chunk
                                    pass
                else:
                    logger.error(f"[JSON] Unknown format in {filepath.name} (first char: {repr(first_char)})")
        except Exception as e:
            logger.error(f"[JSON] Error streaming {filepath.name}: {e}")

    @staticmethod
    def ingest_image_raw(filepath: Path) -> Dict[str, Any]:
        """
        Lê imagem como bytes, cria histograma de 256 canais e expande para VECTOR_DIM.
        Sem dependências externas.
        """
        try:
            size = os.path.getsize(filepath)
            with open(filepath, "rb") as f:
                header = f.read(128)
                f.seek(0)
                hist = [0] * 256
                while True:
                    chunk = f.read(65536)
                    if not chunk:
                        break
                    for b in chunk:
                        hist[b] += 1
            total = sum(hist) or 1
            vec = [h / total for h in hist]
            if len(vec) < CONFIG["VECTOR_DIM"]:
                vec.extend([0.0] * (CONFIG["VECTOR_DIM"] - len(vec)))
            else:
                vec = vec[:CONFIG["VECTOR_DIM"]]
            return {
                "type": "image_raw",
                "size_bytes": size,
                "header_hex": header.hex()[:32],
                "vector": vec,
            }
        except Exception as e:
            logger.error(f"[IMG] Error ingesting {filepath.name}: {e}")
            return {}

# ---------------------------------------------------------------------------
# 5. DIRETÓRIOS E WORKFLOW
# ---------------------------------------------------------------------------

class DirectoryManager:
    def __init__(self) -> None:
        self.root = Path(CONFIG["ROOT_DIR"])
        self._setup()

    def _setup(self) -> None:
        for sub in CONFIG["SUB_DIRS"]:
            (self.root / sub).mkdir(parents=True, exist_ok=True)

    def get_pending_files(self) -> Generator[Path, None, None]:
        src = self.root / "a_processar"
        for entry in src.iterdir():
            if entry.is_file():
                yield entry

    def move_to_state(self, filepath: Path, state: str) -> Path:
        dest = self.root / state / filepath.name
        if dest.exists():
            ts = int(time.time())
            dest = self.root / state / f"{filepath.stem}_{ts}{filepath.suffix}"
        shutil.move(str(filepath), str(dest))
        return dest

# ---------------------------------------------------------------------------
# 6. ORQUESTRADOR HYPER
# ---------------------------------------------------------------------------

class RafaeliaOrchestrator:
    def __init__(self) -> None:
        self.dirs = DirectoryManager()
        mem_path = str(self.dirs.root / "sub" / "memory.jsonl")
        self.memory = SynapticMemory(db_path=mem_path)
        logger.info("RAFAELIA HYPER CORE v27 inicializado.")

    def process_file(self, filepath: Path) -> None:
        logger.info(f"[PROC] Iniciando processamento: {filepath.name}")
        processing_path = self.dirs.move_to_state(filepath, "processando")
        success = False
        try:
            ext = processing_path.suffix.lower()
            # JSON / JSONL
            if ext in [".json", ".jsonl"]:
                count = 0
                for obj in MassiveFileHandler.stream_json_objects(processing_path):
                    content_str = json.dumps(obj, sort_keys=True, ensure_ascii=False)
                    vec = HyperVectorMath.generate_orthogonal(content_str, CONFIG["VECTOR_DIM"])
                    unit = MemoryUnit(
                        id=f"{processing_path.name}_{count}",
                        type="json_obj",
                        vector=list(vec),
                        metadata={
                            "source": processing_path.name,
                            "keys": list(obj.keys()) if isinstance(obj, dict) else []
                        },
                        timestamp=datetime.now(timezone.utc).isoformat()
                    )
                    self.memory.save_unit(unit)
                    count += 1
                    if count % 1000 == 0:
                        logger.info(f"[PROC] {count} objetos absorvidos de {processing_path.name}")
                logger.info(f"[PROC] Total JSON absorvido: {count} objetos.")
                success = True
            # IMAGEM
            elif ext in [".jpg", ".jpeg", ".png", ".bmp", ".gif"]:
                data = MassiveFileHandler.ingest_image_raw(processing_path)
                if data:
                    unit = MemoryUnit(
                        id=processing_path.name,
                        type="image",
                        vector=data["vector"],
                        metadata={
                            "size_bytes": data["size_bytes"],
                            "header_hex": data["header_hex"]
                        },
                        timestamp=datetime.now(timezone.utc).isoformat()
                    )
                    self.memory.save_unit(unit)
                    logger.info(f"[PROC] Imagem absorvida: {processing_path.name}")
                    success = True
            else:
                logger.warning(f"[PROC] Tipo de arquivo não suportado: {ext}")
        except Exception as e:
            logger.error(f"[PROC] Falha em {filepath.name}: {e}")
            success = False
        final_state = "processado" if success else "erro"
        self.dirs.move_to_state(processing_path, final_state)
        logger.info(f"[STATE] {processing_path.name} -> {final_state.upper()}")

    def run_cycle(self) -> None:
        found = False
        for f in self.dirs.get_pending_files():
            found = True
            self.process_file(f)
        if not found:
            print(".", end="", flush=True)
            time.sleep(2)

    def communicate(self, query_text: str) -> None:
        query_vec = HyperVectorMath.generate_orthogonal(query_text, CONFIG["VECTOR_DIM"])
        results = self.memory.query(query_vec, top_k=3)
        print("\n----------------------------------------")
        print("🤖 RAFAELIA HYPER (Ressonância Vetorial):")
        if not results:
            print("  (Silêncio. Ainda não há memória suficiente sobre isso.)")
        else:
            unit, score = results[0]
            print(f"  Melhor match: {unit.id} | score={score:.4f}")
            print(f"  Tipo: {unit.type} | Fonte: {unit.metadata.get('source', 'N/A')}")
            print(f"  Meta: {json.dumps(unit.metadata, ensure_ascii=False)}")
        print("----------------------------------------\n")

# ---------------------------------------------------------------------------
# 7. MAIN
# ---------------------------------------------------------------------------

def main() -> None:
    print("==================================================")
    print("RAFAELIA HYPER CORE v27.0")
    print("JSON 1GB+ (stream) | Imagem por bytes | Vetor->Vetor")
    print("Pasta: aprendizado/a_processar -> processado/erro")
    print("==================================================")
    bot = RafaeliaOrchestrator()
    import threading

    def input_loop() -> None:
        while True:
            try:
                q = input()
            except EOFError:
                break
            if not q:
                continue
            bot.communicate(q)

    t = threading.Thread(target=input_loop, daemon=True)
    t.start()

    logger.info("Monitorando 'aprendizado/a_processar'...")
    try:
        while True:
            bot.run_cycle()
    except KeyboardInterrupt:
        logger.info("Encerrando RAFAELIA HYPER CORE v27 com segurança.")

if __name__ == "__main__":
    main()
