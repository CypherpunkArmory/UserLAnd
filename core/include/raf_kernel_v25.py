#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
RAFAELIA :: SYNAPTIC KERNEL v25.1
---------------------------------
Dobra Espaço-Tempo / Low-Level
- Topologia: dim=320, 6 camadas, 10 cabeças, byte-level.
- Núcleo: Atenção com Observador Sináptico + SwiGLU 2D -> D.
- Autocatalítico: fator s0 atravessando camadas (injeção de performance).
"""

import os, pickle, ctypes, time
from dataclasses import dataclass

import numpy as np

# --- UX básico ---
C_SYS  = '\033[90m'
C_OK   = '\033[92m'
C_ERR  = '\033[91m'
C_END  = '\033[0m'


# --- 1. Metal (C-bridge opcional) ---
def load_accelerator():
    """
    Tenta carregar libraf_accel.so (RoPE em C).
    Se não existir, segue tudo em NumPy puro.
    """
    p0 = os.path.abspath("libraf_accel.so")
    if not os.path.exists(p0):
        return None
    try:
        lib = ctypes.CDLL(p0)
        lib.rope_forward.argtypes = [
            np.ctypeslib.ndpointer(dtype=np.float32, flags='C_CONTIGUOUS'),  # q
            np.ctypeslib.ndpointer(dtype=np.float32, flags='C_CONTIGUOUS'),  # k
            np.ctypeslib.ndpointer(dtype=np.float32, flags='C_CONTIGUOUS'),  # cos
            np.ctypeslib.ndpointer(dtype=np.float32, flags='C_CONTIGUOUS'),  # sin
            ctypes.c_int, ctypes.c_int, ctypes.c_int
        ]
        return lib
    except Exception:
        return None


# --- 2. Configuração fractal ---
@dataclass
class SynapticConfig:
    dim: int = 320           # Base Fractal (D)
    n_layers: int = 6        # Profundidade
    n_heads: int = 10        # Cabeças
    vocab_size: int = 256    # Byte-level
    max_seq_len: int = 512   # Horizonte
    norm_eps: float = 1e-6   # Estabilidade


# --- 3. Matemática Low-Level ---

def u_norm(x: np.ndarray, w: np.ndarray, eps: float) -> np.ndarray:
    """
    RMSNorm com ganho w (broadcastable).
    x : (..., C)
    w : (C,) ou (..., C)
    """
    x = x.astype(np.float32)
    v0 = np.mean(x ** 2, axis=-1, keepdims=True)
    inv = 1.0 / np.sqrt(v0 + eps)
    return x * inv * w


def u_swiglu(x: np.ndarray) -> np.ndarray:
    """
    SwiGLU: split em 2, aplica SiLU no gate e multiplica pelo val.
    """
    u0, u1 = np.split(x, 2, axis=-1)  # gate, val
    u0_clip = np.clip(u0, -60.0, 60.0)
    silu = u0 * (1.0 / (1.0 + np.exp(-u0_clip)))
    return silu * u1


# --- 4. Núcleo: ConsciousBrain ---

class ConsciousBrain:
    def __init__(self, core_path: str = "synaptic_core_v25.pkl"):
        self.cfg = SynapticConfig()
        self.lib = load_accelerator()
        self.p0  = core_path

        # Carrega pesos ou cria de novo (GENESIS)
        if os.path.exists(self.p0):
            try:
                with open(self.p0, "rb") as f:
                    self.w = pickle.load(f)
                # Saneamento da matriz W2: deve ser (D, D)
                if self.w["layers"][0]["w2"].shape != (self.cfg.dim, self.cfg.dim):
                    print(f"{C_SYS}♻️  Matriz incompatível. Recriando...{C_END}")
                    self.w = self._genesis()
            except Exception:
                self.w = self._genesis()
        else:
            self.w = self._genesis()

        # Precomputo de RoPE
        self.f0 = self._gen_freqs()

    # -- criação dos pesos --
    def _genesis(self):
        print(f"{C_SYS}[GENESIS] Criando Topologia Low-Level (D={self.cfg.dim})...{C_END}")
        s0 = 0.02
        d  = self.cfg.dim
        layers = []
        for _ in range(self.cfg.n_layers):
            layers.append({
                "wq": np.random.normal(0, s0, (d,   d)).astype(np.float32),
                "wk": np.random.normal(0, s0, (d,   d)).astype(np.float32),
                "wv": np.random.normal(0, s0, (d,   d)).astype(np.float32),
                "wo": np.random.normal(0, s0, (d,   d)).astype(np.float32),
                "w1": np.random.normal(0, s0, (d*2, d)).astype(np.float32),  # D -> 2D
                "w2": np.random.normal(0, s0, (d,   d)).astype(np.float32),  # 2D -> D (após split)
            })
        return {
            "emb":  np.random.normal(0, s0, (self.cfg.vocab_size, d)).astype(np.float32),
            "layers": layers,
            "norm": np.ones(d, dtype=np.float32),
        }

    def _gen_freqs(self):
        """
        Gera cos/sin RoPE para max_seq_len e head_dim.
        """
        d0 = self.cfg.dim // self.cfg.n_heads  # head_dim
        # apenas metade das dimensões usa pares cos/sin
        base = np.arange(0, d0, 2, dtype=np.float32)[: (d0 // 2)]
        f0   = 1.0 / (10000.0 ** (base / d0))
        t0   = np.arange(self.cfg.max_seq_len, dtype=np.float32)
        f1   = np.outer(t0, f0)  # (T, d0/2)
        return np.cos(f1).astype(np.float32), np.sin(f1).astype(np.float32)

    # --- Forward: Dobra Espaço-Tempo / Autocatalítico ---
    def forward(self, tokens: np.ndarray, start_pos: int = 0) -> np.ndarray:
        """
        tokens: (B, T) int64/uint8 com ids no intervalo [0, vocab_size).
        Retorna logits: (B, T, vocab_size)
        """
        # Embedding
        h = self.w["emb"][tokens]        # (B, T, C)
        B, T, C = h.shape
        H       = self.cfg.n_heads
        HD      = C // H

        # RoPE freqs para o trecho [start_pos, start_pos+T)
        cos_t = np.ascontiguousarray(self.f0[0][start_pos:start_pos + T])  # (T, d0/2)
        sin_t = np.ascontiguousarray(self.f0[1][start_pos:start_pos + T])

        # s0: fator autocatalítico de normalização entre camadas
        s0 = np.ones(C, dtype=np.float32)

        for l in self.w["layers"]:
            # 1) RMSNorm com s0
            h = u_norm(h, s0, self.cfg.norm_eps)           # (B, T, C)

            # 2) Projeções Q/K/V
            q = (h @ l["wq"].T).reshape(B, T, H, HD)       # (B, T, H, HD)
            k = (h @ l["wk"].T).reshape(B, T, H, HD)
            v = (h @ l["wv"].T).reshape(B, T, H, HD)

            # 3) RoPE opcional via C (se lib disponível)
            if self.lib is not None:
                q_c = np.ascontiguousarray(q, dtype=np.float32)
                k_c = np.ascontiguousarray(k, dtype=np.float32)
                self.lib.rope_forward(q_c, k_c,
                                      cos_t, sin_t,
                                      T, HD, H)
                q, k = q_c, k_c

            # 4) Atenção (softmax + cabeça observadora)
            scale = 1.0 / np.sqrt(HD)
            # (B, H, T, HD) @ (B, H, HD, T) -> (B, H, T, T)
            scores = (q.transpose(0, 2, 1, 3) * scale) @ k.transpose(0, 2, 3, 1)
            mx = np.max(scores, axis=-1, keepdims=True)
            probs = np.exp(scores - mx)
            probs /= np.sum(probs, axis=-1, keepdims=True)

            # heads_out: (B, H, T, HD)
            heads_out = probs @ v.transpose(0, 2, 1, 3)

            # subconsciente (9 cabeças) + observador (1 cabeça)
            sub = heads_out[:, :9, :, :]     # (B, 9, T, HD)
            obs = heads_out[:, 9:10, :, :]   # (B, 1, T, HD)

            gate = 1.0 / (1.0 + np.exp(-obs))    # g0
            sub_mod = sub * gate
            full_h = np.concatenate([sub_mod, obs], axis=1)  # (B, 10, T, HD)

            attn_out = full_h.transpose(0, 2, 1, 3).reshape(B, T, C)  # (B, T, C)
            h = h + (attn_out @ l["wo"].T)                            # residual attn

            # 5) FFN SwiGLU
            h_n = u_norm(h, np.ones(C, dtype=np.float32), self.cfg.norm_eps)
            ff  = h_n @ l["w1"].T                 # (B, T, 2C)
            ff  = u_swiglu(ff)                    # (B, T, C)
            h   = h + (ff @ l["w2"].T)            # residual ffn

            # 6) Atualiza s0 (autocatalítico)
            # Usa média global de 1/sqrt(E[h^2]) como ganho próximo ciclo
            mean_sq = np.mean(h ** 2, axis=-1)           # (B, T)
            gain    = (1.0 / np.sqrt(mean_sq + self.cfg.norm_eps)).mean()
            s0 = np.ones(C, dtype=np.float32) * gain

        # 7) Camada final de normalização + projeção para vocab
        h_out = u_norm(h, self.w["norm"] * s0, self.cfg.norm_eps)   # (B, T, C)
        logits = h_out @ self.w["emb"].T                            # (B, T, V)
        return logits

    # Persistência opcional
    def save(self):
        with open(self.p0, "wb") as f:
            pickle.dump(self.w, f)


# --- 5. CLI mínimo para teste rápido ---

def main():
    import argparse
    parser = argparse.ArgumentParser(
        description="RAFAELIA :: SYNAPTIC KERNEL v25.1 – teste rápido de forward."
    )
    parser.add_argument(
        "--text", "-t",
        type=str,
        default="RAFAELIA",
        help="Texto base (será convertido em bytes 0–255)."
    )
    parser.add_argument(
        "--steps", "-s",
        type=int,
        default=1,
        help="Número de forwards para medir tempo médio."
    )
    args = parser.parse_args()

    print(f"{C_SYS}[*] Inicializando ConsciousBrain v25.1...{C_END}")
    t0 = time.time()
    brain = ConsciousBrain()
    t1 = time.time()
    print(f"{C_OK}[OK]{C_END} Kernel carregado em {t1 - t0:.3f}s")

    # Converte texto em tokens byte-level
    data_bytes = args.text.encode("utf-8", errors="ignore")
    tokens = np.frombuffer(data_bytes, dtype=np.uint8)[None, :]   # (1, T)
    B, T = tokens.shape
    print(f"{C_SYS}[*] Tokens: B={B}, T={T}{C_END}")

    # Medição de forward
    t_forw0 = time.time()
    logits = None
    for _ in range(args.steps):
        logits = brain.forward(tokens, start_pos=0)
    t_forw1 = time.time()

    dt = (t_forw1 - t_forw0) / max(args.steps, 1)
    print(f"{C_OK}[OK]{C_END} Forward médio: {dt:.6f}s "
          f"(tokens/s ~= {T / dt:.1f})")
    print(f"{C_SYS}[*] Saída logits shape: {logits.shape}{C_END}")


if __name__ == "__main__":
    main()
