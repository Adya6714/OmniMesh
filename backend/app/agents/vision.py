"""Vision agent: structural damage assessment from a disaster photo.

Uses a vision-capable model (Qwen2-VL) via Fireworks AI (AMD-hosted).
Accepts an image as a base64 data URL or an https URL. Pairs with the
Android app's existing StructuralDangerDetector camera capture, adding
cloud-grade multimodal reasoning on top of the on-device signal.
"""
from __future__ import annotations

import json
import time

import httpx

from app.inference import clients
from app.schemas.models import AgentResponse, AgentType, ExecutionMode

VISION_SYSTEM = """You are a disaster structural-damage assessor. Look at the image
and respond ONLY with JSON:
{"damage_level": "SEVERE|MODERATE|MINOR|NONE", "hazards": ["..."],
 "entry_safe": true|false, "confidence": 0.0-1.0, "summary": "..."}"""


async def assess(image_ref: str, note: str = "", timeout: float = 45.0) -> AgentResponse:
    t0 = time.perf_counter()
    if not clients.cloud_available():
        return AgentResponse(
            agent=AgentType.VISION, packet_id="vision",
            mode_used=ExecutionMode.LOCAL, model_used="unavailable",
            summary="Vision requires cloud (Fireworks) connectivity; queued for reconcile.",
            payload={"damage_level": "UNKNOWN"}, latency_ms=0.0)

    content = [
        {"type": "text", "text": f"Assess structural damage. Context: {note or 'none'}"},
        {"type": "image_url", "image_url": {"url": image_ref}},
    ]
    model = clients.MODELS["vision"]
    async with httpx.AsyncClient(timeout=timeout) as client:
        r = await client.post(
            f"{clients.FIREWORKS_BASE_URL}/chat/completions",
            headers={"Authorization": f"Bearer {clients.FIREWORKS_API_KEY}"},
            json={"model": model, "max_tokens": 512, "temperature": 0.2,
                  "messages": [{"role": "system", "content": VISION_SYSTEM},
                               {"role": "user", "content": content}]},
        )
    latency = (time.perf_counter() - t0) * 1000
    if r.status_code != 200:
        return AgentResponse(
            agent=AgentType.VISION, packet_id="vision",
            mode_used=ExecutionMode.CLOUD, model_used=model,
            summary=f"Vision error {r.status_code}", latency_ms=latency)
    text = r.json()["choices"][0]["message"]["content"]
    try:
        d = json.loads(text.strip().removeprefix("```json").removesuffix("```").strip())
    except Exception:
        d = {"damage_level": "UNKNOWN", "summary": text[:200]}
    return AgentResponse(
        agent=AgentType.VISION, packet_id="vision",
        mode_used=ExecutionMode.CLOUD, model_used=model,
        confidence=float(d.get("confidence", 0.5)),
        summary=d.get("summary", ""),
        payload={k: d.get(k) for k in ("damage_level", "hazards", "entry_safe")},
        latency_ms=latency)
