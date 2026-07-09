# OmniMesh AI — Project Context for Cursor

Paste or keep this file at the repo root. Reference it explicitly in Cursor
(`@CURSOR_CONTEXT.md`) at the start of any session so the agent has full
architecture and constraint awareness before generating code.

---

## 1. What this project is

OmniMesh AI (V2 of the existing OmniMesh disaster-response app) is an
**autonomous, multi-agent disaster response platform** where AI agents
coordinate rescue efforts even when internet connectivity is unavailable.

Core differentiator: **hybrid AI routing**. For every incoming task, an
orchestrator decides whether to run inference locally (edge, offline-capable)
or remotely (Fireworks AI on AMD infrastructure), or both, based on
connectivity, task complexity, and local-result confidence. This is the
single idea the whole product, demo, and pitch are built around — every
architectural decision should reinforce it, not dilute it with unrelated
features.

## 2. Hackathon constraints (do not violate these)

- **Event**: AMD Developer Hackathon: ACT II, Track 3 — Unicorn Track.
- **Judging criteria**: creativity/originality, product/market potential,
  completeness, meaningful use of AMD platforms. No benchmark, no leaderboard.
- **Must be containerized** — every service ships with a Dockerfile, the
  whole system runs via a single `docker-compose up`.
- **Must use AMD infrastructure meaningfully**: AMD Developer Cloud (hosting),
  ROCm (edge/local inference benchmarked on ROCm-backed instance), Fireworks
  AI API (cloud model calls).
- **Bonus target**: "Best AMD-Hosted Gemma Project" ($2,000, Track 3). At
  least the **medical triage agent** and **translation agent** must run on a
  Gemma model via Fireworks AI, and this must be explicitly documented and
  demoed, not buried.
- **Submission package required**: public GitHub repo + runnable README,
  containerized demo app with a live URL, cover image, video presentation,
  slide presentation, short + long description, tech tags. Build these into
  the day-4 checklist, not an afterthought.
- **Confirm the actual deadline** in the lablab.ai Event Schedule tab before
  finalizing the day-by-day plan below — do not hardcode a date in code or
  docs.

## 3. Tech stack

- **Backend**: Python 3.11+, FastAPI, Pydantic v2 (typed models everywhere),
  `uvicorn`/`gunicorn` for serving.
- **Agent orchestration**: custom async orchestrator (no heavyweight framework
  required — LangGraph is fine if it speeds you up, but don't let framework
  ceremony eat sprint time you don't have).
- **Cloud inference**: Fireworks AI API (`fireworks-ai` Python client or raw
  HTTPS calls to `https://api.fireworks.ai/inference/v1/chat/completions`).
  Models: Gemma (triage, translation), Llama-3.2-Vision or Qwen2-VL (damage
  vision), a general Llama/Mixtral instruct model as fallback reasoning model.
- **Local/edge inference**: small quantized models served via `vllm` or
  `llama.cpp` (HIP/ROCm backend) on an AMD Developer Cloud GPU instance,
  explicitly labeled in docs as "edge-simulated on ROCm" — do not claim this
  runs on a physical phone unless it actually does.
- **Non-LLM agents** (route planning, resource allocation): plain algorithms —
  A*/Dijkstra for routing, a greedy/constraint heuristic (or `ortools`) for
  allocation. Do not force an LLM call where a deterministic algorithm is
  more correct and more impressive to judges who know the difference.
- **Existing assets to reuse, not rebuild**: `TriagePacket` schema, RED/YELLOW/
  GREEN priority model, the simulation/demo injection mode, the React web
  dashboard, the Android mesh/BLE layer and Gemini-dispatch call site (this
  call site gets replaced by a call to the new orchestrator service).
- **Containerization**: Docker + docker-compose. One container per service
  (`agents-api`, `web-dashboard`, optionally `local-inference` if run as a
  separate process). Deployed on AMD Developer Cloud.
- **Config/secrets**: `.env` file, never committed. Required vars listed in
  section 6.

## 4. Repo structure

```
omnimesh-ai/
├── CURSOR_CONTEXT.md              # this file
├── docker-compose.yml
├── README.md                      # judge-facing, must be complete & runnable
├── backend/
│   ├── Dockerfile
│   ├── pyproject.toml
│   ├── app/
│   │   ├── main.py                # FastAPI entrypoint
│   │   ├── config.py              # settings via pydantic-settings
│   │   ├── schemas/
│   │   │   ├── event.py           # TriagePacket, incoming event schema
│   │   │   └── agent_response.py  # unified AgentResponse contract
│   │   ├── orchestrator/
│   │   │   ├── router.py          # local vs cloud routing decision fn
│   │   │   └── synthesis.py       # merges agent outputs into one response
│   │   ├── agents/
│   │   │   ├── base.py            # Agent protocol/ABC
│   │   │   ├── medical_triage.py  # Gemma via Fireworks + local fallback
│   │   │   ├── vision_damage.py   # Llama/Qwen-VL via Fireworks + local VLM
│   │   │   ├── route_planning.py  # A*/Dijkstra, no LLM required
│   │   │   ├── resource_alloc.py  # heuristic/ortools, no LLM required
│   │   │   ├── translation.py     # Gemma via Fireworks + local NLLB
│   │   │   └── missing_person.py  # embedding similarity + LLM reconcile
│   │   ├── inference/
│   │   │   ├── fireworks_client.py
│   │   │   └── local_client.py    # calls local vllm/llama.cpp endpoint
│   │   └── api/
│   │       └── routes.py          # POST /events, GET /health, etc.
│   └── tests/
├── web-dashboard/                 # existing React app, modified to call
│                                  # the new backend instead of Gemini directly
├── android/                       # existing app, modify the dispatch call
│                                  # site to hit the new backend API
└── infra/
    ├── amd-dev-cloud-notes.md     # instance setup, rocm-smi screenshots
    └── demo-script.md             # the connectivity-toggle demo, timed
```

## 5. Core data contracts

Keep these stable — every agent and the dashboard/Android depend on them.

```python
# schemas/event.py
class TriagePacket(BaseModel):
    event_id: str
    event_type: Literal["medical", "damage_image", "route_request",
                         "resource_request", "translation", "missing_person"]
    payload: dict  # shape depends on event_type, validated per-agent
    connectivity: Literal["offline", "online", "degraded"]
    timestamp: datetime

# schemas/agent_response.py
class AgentResponse(BaseModel):
    event_id: str
    agent_name: str
    source: Literal["local", "cloud", "hybrid"]
    model_used: str          # e.g. "gemma-2-9b-it" or "local-nllb-200"
    result: dict
    confidence: float
    escalated: bool          # true if local ran first and cloud reconciled it
```

## 6. Environment variables

```
FIREWORKS_API_KEY=
FIREWORKS_BASE_URL=https://api.fireworks.ai/inference/v1
LOCAL_INFERENCE_URL=http://local-inference:8001   # vllm/llama.cpp endpoint
AMD_DEV_CLOUD_INSTANCE=                            # for docs/logging only
CONNECTIVITY_OVERRIDE=                             # "offline" | "online" | unset
                                                    # lets the demo force a mode
```

## 7. Orchestrator routing logic (pseudocode — implement for real, don't stub)

```python
def route(event: TriagePacket, agent: Agent) -> Literal["local", "cloud", "hybrid"]:
    if event.connectivity == "offline":
        return "local"
    if agent.requires_high_precision and event.connectivity == "online":
        return "cloud"
    local_result = agent.run_local(event)
    if local_result.confidence < agent.escalation_threshold and event.connectivity != "offline":
        return "hybrid"  # local ran, now reconcile with cloud
    return "local"
```

The **hybrid** path — local answers first, cloud reconciles when reachable —
is the single most important behavior to get right and demo live. Do not
let this become "if online: cloud else: local" with no reconciliation;
that's a much weaker story and loses the point of the whole project.

## 8. Coding conventions

- Typed Python everywhere, no bare `dict`/`Any` in function signatures where
  a Pydantic model or TypedDict is possible.
- One agent = one class implementing a shared `Agent` protocol
  (`run_local`, `run_cloud`, `escalation_threshold`, `requires_high_precision`).
- FastAPI routers per concern, not one giant `main.py`.
- No hardcoded API keys or model names inline — pull from `config.py`.
- Prefer explicit over clever. This is a 4-day build being read by judges,
  not a codebase optimized for future maintainers.

## 9. Build order for Cursor sessions (follow in this order)

1. Scaffold `backend/` skeleton + docker-compose + `.env.example`, get a
   trivial `/health` endpoint running in a container, confirm Fireworks API
   key works with one raw curl/test call.
2. Implement `TriagePacket`/`AgentResponse` schemas + the orchestrator router
   function (section 7) with a stub echo agent, prove the local/cloud/hybrid
   paths all work end-to-end through Docker.
3. Implement `medical_triage.py` (Gemma via Fireworks) — first real agent.
4. Implement `vision_damage.py`, `route_planning.py`, `resource_alloc.py`.
5. Implement `translation.py` (Gemma) and `missing_person.py` if time allows.
6. Wire `web-dashboard/` to call the new backend instead of Gemini directly.
7. Wire one Android flow (medical triage) to call the new backend.
8. Build the connectivity-toggle demo control end-to-end and rehearse it.
9. README, architecture diagram, rocm-smi screenshot, demo video, slides.

## 10. What NOT to do

- Don't rebuild the Android BLE mesh layer — it already works, reuse it.
- Don't add a framework (LangGraph, CrewAI, AutoGen) unless it demonstrably
  saves time this week — ceremony you don't finish is worse than a plain
  asyncio orchestrator that works.
- Don't fake ROCm/local inference claims — label simulated components
  honestly in the README; judges respect this more than they punish it.
- Don't let agent count grow at the expense of the connectivity-toggle demo
  — that single moment matters more than a 7th agent.
