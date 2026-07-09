"""Missing-person matching agent.

Matches a "missing person" description against triaged/found victim records.
  ONLINE  -> Fireworks embeddings (AMD-hosted) for semantic similarity,
             then Gemma explains the top match.
  OFFLINE -> pure-python token-cosine similarity so it still works with no
             connectivity (zero dependencies, zero tokens).
This is the most novel feature in the roster -- reuniting families is a
concrete "unicorn" product story for disaster response.
"""
from __future__ import annotations

import math
import re
import time
from collections import Counter

from app.inference import clients
from app.schemas.models import AgentResponse, AgentType, ExecutionMode

_WORD = re.compile(r"[a-z0-9]+")


def _tokens(s: str) -> Counter:
    return Counter(_WORD.findall((s or "").lower()))


def _cosine(a: Counter, b: Counter) -> float:
    keys = set(a) | set(b)
    dot = sum(a[k] * b[k] for k in keys)
    na = math.sqrt(sum(v * v for v in a.values()))
    nb = math.sqrt(sum(v * v for v in b.values()))
    return dot / (na * nb) if na and nb else 0.0


def _embed_cosine(x: list[float], y: list[float]) -> float:
    dot = sum(i * j for i, j in zip(x, y))
    nx = math.sqrt(sum(i * i for i in x))
    ny = math.sqrt(sum(j * j for j in y))
    return dot / (nx * ny) if nx and ny else 0.0


async def match(query: str, candidates: list[dict], top_k: int = 3) -> AgentResponse:
    """candidates: [{"id": "...", "description": "..."}]. Returns ranked matches."""
    t0 = time.perf_counter()
    mode = ExecutionMode.LOCAL
    model = "token-cosine (offline)"
    ranked = []

    if clients.cloud_available():
        try:
            texts = [query] + [c["description"] for c in candidates]
            vecs = await clients.cloud_embed(texts)
            qv, cvs = vecs[0], vecs[1:]
            ranked = sorted(
                ({"id": c["id"], "description": c["description"],
                  "score": round(_embed_cosine(qv, cv), 4)}
                 for c, cv in zip(candidates, cvs)),
                key=lambda m: m["score"], reverse=True)[:top_k]
            mode = ExecutionMode.CLOUD
            model = clients.MODELS["embed"] + " (Fireworks/AMD)"
        except Exception:
            ranked = []

    if not ranked:  # offline fallback
        qt = _tokens(query)
        ranked = sorted(
            ({"id": c["id"], "description": c["description"],
              "score": round(_cosine(qt, _tokens(c["description"])), 4)}
             for c in candidates),
            key=lambda m: m["score"], reverse=True)[:top_k]

    explanation = ""
    if mode == ExecutionMode.CLOUD and ranked:
        try:
            sys = ("You reunite missing persons. Given a query and the best "
                   "candidate match, explain in one sentence why they likely match.")
            user = f"Query: {query}\nBest match: {ranked[0]['description']}"
            explanation, _, _ = await clients.cloud_chat("reconcile", sys, user)
        except Exception:
            explanation = ""

    return AgentResponse(
        agent=AgentType.MISSING_PERSON, packet_id="missing",
        mode_used=mode, model_used=model,
        confidence=ranked[0]["score"] if ranked else 0.0,
        summary=explanation.strip() or (f"Top match: {ranked[0]['id']}" if ranked else "No candidates"),
        payload={"matches": ranked},
        latency_ms=(time.perf_counter() - t0) * 1000)
