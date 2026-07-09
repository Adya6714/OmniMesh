"""Orchestrator: decides per-packet execution mode (local / cloud / hybrid),
records a human-readable reasoning trace, and fans out to agents.
This routing logic is the project's centerpiece."""
from __future__ import annotations

import asyncio

from app.agents import triage
from app.agents.others import translate
from app.inference import clients
from app.schemas.models import (
    AgentResponse, ExecutionMode, OrchestrationResult, Severity, TriagePacket,
)

# Global connectivity switch -- flipped by the demo toggle endpoint.
STATE = {"connectivity": True}


def decide_mode(packet: TriagePacket, trace=None) -> ExecutionMode:
    """Routing policy:
    - No connectivity (node-level OR global toggle)  -> LOCAL
    - Connectivity + severity hint RED               -> HYBRID
    - Connectivity otherwise                          -> CLOUD
    """
    trace = trace if trace is not None else []
    global_up = STATE["connectivity"]
    node_up = packet.connectivity
    cloud_up = clients.cloud_available()
    trace.append(
        f"ROUTE: global_link={global_up}, node_link={node_up}, "
        f"cloud_key={cloud_up}, gpu={clients.local_gpu_available()}"
    )
    if not (global_up and node_up and cloud_up):
        trace.append("ROUTE: uplink unavailable -> LOCAL (edge inference only)")
        return ExecutionMode.LOCAL
    if packet.severity_hint == Severity.RED:
        trace.append("ROUTE: RED hint + online -> HYBRID (instant local + cloud reconcile)")
        return ExecutionMode.HYBRID
    trace.append("ROUTE: online, non-critical -> CLOUD (best quality)")
    return ExecutionMode.CLOUD


async def orchestrate(packet: TriagePacket) -> OrchestrationResult:
    trace: list[str] = []
    mode = decide_mode(packet, trace)

    tasks = [triage.run(packet, mode, trace)]
    if packet.text_report and (packet.language or "").lower() not in ("", "en", "english"):
        trace.append(f"AGENT: non-English report ({packet.language}) -> translation agent")
        tasks.append(translate(packet, mode))

    responses: list[AgentResponse] = list(await asyncio.gather(*tasks))

    triage_resp = next(r for r in responses if r.agent.value == "triage")
    final_severity = triage_resp.severity or Severity.UNKNOWN

    # ---- Uncertainty-awareness (mirrors ACT I winning "uncertainty-aware triage")
    # Flag for human review when the model is not confident enough, when severity
    # is unknown, or when we could not reach the cloud for a critical case.
    REVIEW_THRESHOLD = 0.65
    triage_resp.uncertainty = round(1.0 - triage_resp.confidence, 3)
    triage_resp.needs_human_review = (
        triage_resp.confidence < REVIEW_THRESHOLD
        or final_severity == Severity.UNKNOWN
    )
    if triage_resp.needs_human_review:
        trace.append(
            f"UNCERTAINTY: confidence {triage_resp.confidence:.2f} < {REVIEW_THRESHOLD} "
            f"-> FLAGGED for human review"
        )
    else:
        trace.append(f"UNCERTAINTY: confidence {triage_resp.confidence:.2f} -> auto-cleared")

    action = triage_resp.payload.get("recommended_action", "")
    review = " [NEEDS HUMAN REVIEW]" if triage_resp.needs_human_review else ""
    dispatch = f"[{final_severity.value}]{review} {triage_resp.summary}" + (f" Action: {action}" if action else "")
    trace.append(f"DISPATCH: final severity {final_severity.value} via {triage_resp.mode_used.value}")

    return OrchestrationResult(
        packet_id=packet.packet_id, mode=mode,
        responses=responses, final_severity=final_severity,
        dispatch_summary=dispatch, reasoning_trace=trace,
    )
