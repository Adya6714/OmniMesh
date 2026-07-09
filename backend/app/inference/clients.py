"""Inference clients for OmniMesh AI.

Three inference paths, all AMD-aligned:

  CLOUD  -> Fireworks AI (models hosted on AMD hardware). Gemma for
            triage/translation/reconcile, Qwen2-VL for vision.

  LOCAL  -> A real open-weight model (Gemma) served on the AMD Developer
            Cloud GPU via vLLM-ROCm, exposed as an OpenAI-compatible
            endpoint at LOCAL_MODEL_URL. Genuine on-GPU inference using
            AMD's ROCm stack -- the "edge" path in hybrid routing.

  HEURISTIC -> Deterministic rule fallback used only when neither the GPU
               nor the cloud is reachable, so the system NEVER goes dark.
"""
from __future__ import annotations

import os
import time

import httpx

# ---- Cloud (Fireworks AI, AMD-hosted) ----------------------------------
FIREWORKS_BASE_URL = os.getenv("FIREWORKS_BASE_URL", "https://api.fireworks.ai/inference/v1")
FIREWORKS_API_KEY = os.getenv("FIREWORKS_API_KEY", "")

# ---- Local (Gemma on AMD GPU via vLLM-ROCm, OpenAI-compatible) ----------
LOCAL_MODEL_URL = os.getenv("LOCAL_MODEL_URL", "")          # e.g. http://vllm-rocm:8001/v1
LOCAL_MODEL_ID = os.getenv("LOCAL_MODEL_ID", "google/gemma-2-2b-it")

MODELS = {
    "triage": os.getenv("TRIAGE_MODEL", "accounts/fireworks/models/gemma-3-27b-it"),
    "translation": os.getenv("TRANSLATION_MODEL", "accounts/fireworks/models/gemma-3-27b-it"),
    "vision": os.getenv("VISION_MODEL", "accounts/fireworks/models/qwen2p5-vl-32b-instruct"),
    "reconcile": os.getenv("RECONCILE_MODEL", "accounts/fireworks/models/gemma-3-27b-it"),
    "embed": os.getenv("EMBED_MODEL", "nomic-ai/nomic-embed-text-v1.5"),
}


class InferenceError(RuntimeError):
    pass


def cloud_available() -> bool:
    return bool(FIREWORKS_API_KEY)


def local_gpu_available() -> bool:
    return bool(LOCAL_MODEL_URL)


async def _openai_chat(base_url, api_key, model, system, user, timeout, max_tokens=512):
    """Shared OpenAI-compatible chat call (works for Fireworks AND vLLM-ROCm)."""
    t0 = time.perf_counter()
    headers = {"Authorization": f"Bearer {api_key or 'none'}"}
    messages = [{"role": "system", "content": system}, {"role": "user", "content": user}]
    async with httpx.AsyncClient(timeout=timeout) as client:
        r = await client.post(
            f"{base_url}/chat/completions",
            headers=headers,
            json={"model": model, "max_tokens": max_tokens,
                  "temperature": 0.2, "messages": messages},
        )
    if r.status_code != 200:
        raise InferenceError(f"{base_url} {r.status_code}: {r.text[:200]}")
    latency = (time.perf_counter() - t0) * 1000
    return r.json()["choices"][0]["message"]["content"], model, latency


async def cloud_chat(task, system, user, timeout=30.0):
    """Fireworks AI (AMD-hosted). Returns (text, model_id, latency_ms)."""
    if not FIREWORKS_API_KEY:
        raise InferenceError("FIREWORKS_API_KEY not set")
    return await _openai_chat(FIREWORKS_BASE_URL, FIREWORKS_API_KEY,
                              MODELS.get(task, MODELS["triage"]), system, user, timeout)


async def local_chat(system, user, timeout=20.0):
    """Gemma on AMD GPU via vLLM-ROCm. Returns (text, model_id, latency_ms)."""
    if not LOCAL_MODEL_URL:
        raise InferenceError("LOCAL_MODEL_URL not set (no ROCm GPU endpoint)")
    return await _openai_chat(LOCAL_MODEL_URL, os.getenv("LOCAL_MODEL_KEY", ""),
                              LOCAL_MODEL_ID, system, user, timeout)


async def cloud_embed(texts, timeout=30.0):
    """Embeddings via Fireworks (AMD-hosted). Used by missing-person matching."""
    if not FIREWORKS_API_KEY:
        raise InferenceError("FIREWORKS_API_KEY not set")
    async with httpx.AsyncClient(timeout=timeout) as client:
        r = await client.post(
            f"{FIREWORKS_BASE_URL}/embeddings",
            headers={"Authorization": f"Bearer {FIREWORKS_API_KEY}"},
            json={"model": MODELS["embed"], "input": texts},
        )
    if r.status_code != 200:
        raise InferenceError(f"embed {r.status_code}: {r.text[:200]}")
    return [d["embedding"] for d in r.json()["data"]]
