"""Medical triage agent.

Hybrid routing:
  LOCAL  -> Gemma on the AMD GPU (vLLM-ROCm) if available, else START-triage
            rule heuristic. Always produces an instant, offline-safe answer.
  CLOUD  -> Gemma via Fireworks (AMD-hosted) for best-quality dispatch reasoning.
  HYBRID -> local answer first, cloud reconciles and upgrades it.
"""
from __future__ import annotations

import json
import time

from app.inference import clients
from app.schemas.models import AgentResponse, AgentType, ExecutionMode, Severity, TriagePacket

SYSTEM_PROMPT = """You are a disaster medical triage agent following START triage.
Given a victim report and vitals, respond ONLY with JSON:
{"severity": "RED|YELLOW|GREEN", "confidence": 0.0-1.0, "reasoning": "...", "recommended_action": "..."}
RED = immediate life threat, YELLOW = urgent but stable, GREEN = walking wounded.
No prose outside the JSON."""


def _local_triage(packet: TriagePacket):
    """START-style rule heuristic -- the offline edge stand-in."""
    hr = packet.vitals.get("hr")
    spo2 = packet.vitals.get("spo2")
    resp = packet.vitals.get("resp_rate")
    text = (packet.text_report or "").lower()
    red_words = ("unconscious", "not breathing", "severe bleeding", "chest pain", "trapped")
    yellow_words = ("fracture", "bleeding", "burn", "dizzy", "head injury")
    if (spo2 is not None and spo2 < 90) or (resp is not None and (resp > 30 or resp < 10)) \
            or (hr is not None and hr > 130) or any(w in text for w in red_words):
        return Severity.RED, 0.8, "Critical vitals or life-threat keywords detected (START rules)."
    if (spo2 is not None and spo2 < 94) or (hr is not None and hr > 110) \
            or any(w in text for w in yellow_words):
        return Severity.YELLOW, 0.7, "Abnormal vitals or injury keywords detected."
    return Severity.GREEN, 0.6, "No critical indicators found in report or vitals."


def _parse_llm(text):
    return json.loads(text.strip().removeprefix("```json").removesuffix("```").strip())


async def _local_answer(packet, trace):
    """Try real Gemma-on-ROCm; fall back to heuristic."""
    user = json.dumps({"report": packet.text_report, "vitals": packet.vitals,
                       "sensor_flags": packet.sensor_flags})
    if clients.local_gpu_available():
        try:
            text, model, latency = await clients.local_chat(SYSTEM_PROMPT, user)
            d = _parse_llm(text)
            trace.append(f"LOCAL: Gemma on AMD GPU (ROCm) returned {d.get('severity')}")
            return AgentResponse(
                agent=AgentType.TRIAGE, packet_id=packet.packet_id,
                mode_used=ExecutionMode.LOCAL, model_used=f"{model} (ROCm)",
                severity=Severity(d.get("severity", "UNKNOWN")),
                confidence=float(d.get("confidence", 0.6)),
                summary=d.get("reasoning", ""),
                payload={"recommended_action": d.get("recommended_action", "")},
                latency_ms=latency)
        except Exception as e:
            trace.append(f"LOCAL: ROCm GPU unavailable ({str(e)[:40]}) -> heuristic")
    sev, conf, why = _local_triage(packet)
    trace.append(f"LOCAL: START heuristic -> {sev.value}")
    return AgentResponse(
        agent=AgentType.TRIAGE, packet_id=packet.packet_id,
        mode_used=ExecutionMode.LOCAL, model_used="edge-start-heuristic",
        severity=sev, confidence=conf, summary=why, latency_ms=0.0)


async def run(packet: TriagePacket, mode: ExecutionMode, trace=None):
    trace = trace if trace is not None else []
    t0 = time.perf_counter()

    if mode in (ExecutionMode.LOCAL, ExecutionMode.HYBRID):
        local_resp = await _local_answer(packet, trace)
        if mode == ExecutionMode.LOCAL:
            return local_resp

    # CLOUD or HYBRID reconcile via Gemma on Fireworks
    user = json.dumps({"report": packet.text_report, "vitals": packet.vitals,
                       "sensor_flags": packet.sensor_flags})
    try:
        text, model, latency = await clients.cloud_chat("triage", SYSTEM_PROMPT, user)
        d = _parse_llm(text)
        trace.append(f"CLOUD: Gemma via Fireworks (AMD) returned {d.get('severity')}")
        return AgentResponse(
            agent=AgentType.TRIAGE, packet_id=packet.packet_id,
            mode_used=mode, model_used=model,
            severity=Severity(d.get("severity", "UNKNOWN")),
            confidence=float(d.get("confidence", 0.5)),
            summary=d.get("reasoning", ""),
            payload={"recommended_action": d.get("recommended_action", "")},
            latency_ms=latency, reconciled=(mode == ExecutionMode.HYBRID))
    except Exception as e:
        trace.append(f"CLOUD: Fireworks unavailable ({str(e)[:40]}) -> keeping local answer")
        if mode == ExecutionMode.HYBRID:
            return local_resp
        sev, conf, why = _local_triage(packet)
        return AgentResponse(
            agent=AgentType.TRIAGE, packet_id=packet.packet_id,
            mode_used=ExecutionMode.LOCAL, model_used="edge-start-heuristic(fallback)",
            severity=sev, confidence=conf, summary=why,
            latency_ms=(time.perf_counter() - t0) * 1000)
