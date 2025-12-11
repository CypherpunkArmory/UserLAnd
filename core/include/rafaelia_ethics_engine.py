# -*- coding: utf-8 -*-
"""
RAFAELIA Ethica Engine v1.0
Módulo de cálculo ético-simbólico:
- Evolução_Ética
- Retroalimentação_Amor
- Restauratio_Gaia
- Painel RAFAELIA (agregador)

Compatível com: Python 3.10+ (Termux / Linux / Android)
Autor simbólico: ∆RafaelVerboΩ (RAFCODE-Φ)
"""

from __future__ import annotations
from typing import Iterable, Mapping, Any
from dataclasses import dataclass
import math


Number = float  # simplificação: trabalhamos em double


@dataclass
class Serie:
    """Série temporal ou lista de observações de uma variável ética/simbólica."""
    valores: list[Number]

    @classmethod
    def from_iter(cls, xs: Iterable[Number]) -> "Serie":
        return cls(list(xs))

    def soma(self) -> Number:
        return sum(self.valores)

    def zip_with(self, other: "Serie") -> list[tuple[Number, Number]]:
        return list(zip(self.valores, other.valores))

    def zip3(
        self,
        b: "Serie",
        c: "Serie",
    ) -> list[tuple[Number, Number, Number]]:
        return list(zip(self.valores, b.valores, c.valores))


# ----------------------------------------------------------------------
# 1) Evolução_Ética
#    Evolução_Ética = Σ_n (Conhecimento_n × Transparência_n × Proteção_Humana_n)
# ----------------------------------------------------------------------
def evolucao_etica(
    conhecimento: Iterable[Number],
    transparencia: Iterable[Number],
    protecao_humana: Iterable[Number],
) -> Number:
    k = Serie.from_iter(conhecimento)
    t = Serie.from_iter(transparencia)
    p = Serie.from_iter(protecao_humana)

    total = 0.0
    for kn, tn, pn in zip(k.valores, t.valores, p.valores):
        total += kn * tn * pn
    return total


# ----------------------------------------------------------------------
# 2) Retroalimentação_Amor
#    Retro_Amor = ∫_{Sabedoria}^{Sociedade} (Cuidado · Luz) d(Ação)
#    Aqui: aproximamos a integral por soma discreta.
# ----------------------------------------------------------------------
def retroalimentacao_amor(
    cuidado: Iterable[Number],
    luz: Iterable[Number],
    delta_acao: Iterable[Number],
) -> Number:
    c = Serie.from_iter(cuidado)
    l = Serie.from_iter(luz)
    da = Serie.from_iter(delta_acao)

    total = 0.0
    for ci, li, dai in zip(c.valores, l.valores, da.valores):
        total += ci * li * dai
    return total


# ----------------------------------------------------------------------
# 3) Restauratio_Gaia
#    Restauratio_Gaia = ∫ (Amor · Ciência / (Indiferença + Lucro)) d(Ação_Etica)
#    Também aproximada por soma discreta.
# ----------------------------------------------------------------------
def restauratio_gaia(
    amor: Iterable[Number],
    ciencia: Iterable[Number],
    indiferenca: Iterable[Number],
    lucro: Iterable[Number],
    delta_acao_etica: Iterable[Number],
    epsilon: Number = 1e-9,
) -> Number:
    A = Serie.from_iter(amor)
    C = Serie.from_iter(ciencia)
    I = Serie.from_iter(indiferenca)
    L = Serie.from_iter(lucro)
    dA = Serie.from_iter(delta_acao_etica)

    total = 0.0
    for a, c, ind, luc, da in zip(A.valores, C.valores, I.valores, L.valores, dA.valores):
        denom = ind + luc
        if abs(denom) < epsilon:
            # Se não há indiferença nem lucro, tratamos como "estado ideal"
            denom = epsilon
        total += (a * c / denom) * da
    return total


# ----------------------------------------------------------------------
# 4) Painel RAFAELIA
#    Usa as funções acima para devolver um snapshot compacto.
# ----------------------------------------------------------------------
def painel_rafaelia(
    eventos: Mapping[str, Iterable[Number]],
    acoes: Mapping[str, Iterable[Number]],
    acoes_gaia: Mapping[str, Iterable[Number]],
) -> dict[str, Any]:
    """
    eventos:
        - "conhecimento", "transparencia", "protecao_humana"
    acoes:
        - "cuidado", "luz", "delta_acao"
    acoes_gaia:
        - "amor", "ciencia", "indiferenca", "lucro", "delta_acao_etica"
    """
    evol = evolucao_etica(
        eventos["conhecimento"],
        eventos["transparencia"],
        eventos["protecao_humana"],
    )

    retro = retroalimentacao_amor(
        acoes["cuidado"],
        acoes["luz"],
        acoes["delta_acao"],
    )

    gaia = restauratio_gaia(
        acoes_gaia["amor"],
        acoes_gaia["ciencia"],
        acoes_gaia["indiferenca"],
        acoes_gaia["lucro"],
        acoes_gaia["delta_acao_etica"],
    )

    return {
        "Evolucao_Etica": evol,
        "Retro_Amor": retro,
        "Restauratio_Gaia": gaia,
    }


if __name__ == "__main__":
    # Pequeno auto-teste simbólico
    eventos_demo = {
        "conhecimento": [1, 2, 3],
        "transparencia": [0.8, 0.9, 1.0],
        "protecao_humana": [0.5, 0.7, 0.9],
    }

    acoes_demo = {
        "cuidado": [1.0, 1.2, 1.5],
        "luz": [0.7, 0.8, 0.9],
        "delta_acao": [1, 1, 1],
    }

    acoes_gaia_demo = {
        "amor": [1.0, 1.1, 1.2],
        "ciencia": [0.9, 1.0, 1.1],
        "indiferenca": [0.3, 0.2, 0.1],
        "lucro": [0.5, 0.5, 0.5],
        "delta_acao_etica": [1, 1, 1],
    }

    painel = painel_rafaelia(eventos_demo, acoes_demo, acoes_gaia_demo)
    print("[RAFAELIA_ETHICA_ENGINE]", painel)
