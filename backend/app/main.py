"""OmniMesh AI backend -- FastAPI entrypoint.

Multi-agent disaster-response orchestration on AMD infrastructure:
Gemma via Fireworks AI (AMD-hosted) + Gemma on AMD GPU via vLLM-ROCm.
"""
from __future__ import annotations

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from app.agents import missing_person, vision
from app.agents.others import allocate_resources, plan_route
from app.inference import clients
from app.orchestrator import core as orch
from app.schemas.models import OrchestrationResult, TriagePacket

app = FastAPI(title="OmniMesh AI", version="2.0.0",
              description="AMD-hosted multi-agent disaster response")
app.add_middleware(CORSMiddleware, allow_origins=["*"],
                   allow_methods=["*"], allow_headers=["*"])


@app.get("/health")
async def health():
    return {
        "status": "ok",
        "connectivity": orch.STATE["connectivity"],
        "cloud_available": clients.cloud_available(),
        "local_gpu_available": clients.local_gpu_available(),
    }


@app.get("/v1/infra")
async def infra():
    """Report which AMD inference paths are wired -- surfaced on the dashboard."""
    return {
        "cloud": {"provider": "Fireworks AI (AMD-hosted)",
                  "enabled": clients.cloud_available(),
                  "triage_model": clients.MODELS["triage"],
                  "vision_model": clients.MODELS["vision"]},
        "local_gpu": {"provider": "vLLM-ROCm on AMD Developer Cloud",
                      "enabled": clients.local_gpu_available(),
                      "model": clients.LOCAL_MODEL_ID},
    }


@app.get("/v1/gpu-status")
async def gpu_status():
    """Live AMD GPU inference server status -- for a real-time dashboard
    panel proving the GPU is up during judging, not just a screenshot."""
    return await clients.gpu_status()


@app.post("/v1/triage", response_model=OrchestrationResult)
async def triage_packet(packet: TriagePacket):
    return await orch.orchestrate(packet)


@app.post("/v1/connectivity/{state}")
async def set_connectivity(state: str):
    """Demo centerpiece: toggle the simulated uplink on/off live."""
    orch.STATE["connectivity"] = state.lower() in ("on", "true", "1")
    return {"connectivity": orch.STATE["connectivity"]}


@app.post("/v1/vision")
async def vision_assess(body: dict):
    """body: {"image_ref": "<https url or base64 data url>", "note": "..."}"""
    resp = await vision.assess(body["image_ref"], body.get("note", ""))
    return resp.model_dump()


@app.post("/v1/missing-person")
async def missing(body: dict):
    """body: {"query": "...", "candidates": [{"id":"..","description":".."}]}"""
    resp = await missing_person.match(body["query"], body["candidates"],
                                      body.get("top_k", 3))
    return resp.model_dump()


@app.post("/v1/route")
async def route(body: dict):
    path = plan_route(body["grid"], tuple(body["start"]), tuple(body["goal"]))
    return {"path": path, "reachable": bool(path)}


@app.post("/v1/allocate")
async def allocate(body: dict):
    return {"assignments": allocate_resources(body["victims"], body["units"])}
