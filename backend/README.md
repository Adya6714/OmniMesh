# OmniMesh AI — Backend (AMD Developer Hackathon ACT II, Unicorn Track)

Autonomous multi-agent disaster-response orchestration running on AMD
infrastructure. Adds an AMD-hosted, open-weight reasoning layer on top of the
existing OmniMesh offline-first mesh-triage system.

## Why this wins (mapped to the judging criteria)

- **Use of AMD platforms** — Gemma runs on AMD hardware **two independent ways**:
  locally on an AMD Developer Cloud GPU via **vLLM-ROCm**, and in the cloud via
  **Fireworks AI** (AMD-hosted). The vision agent uses Qwen2-VL on Fireworks.
  Deployed and containerized on AMD Developer Cloud.
- **Creativity / originality** — a live **hybrid routing orchestrator** that
  decides per-packet whether to answer on the edge GPU, in the cloud, or both
  (local-first, cloud-reconciled), and streams a human-readable reasoning trace.
- **Completeness** — six agents, 12 endpoints, containerized, 15 passing tests,
  graceful degradation so it never goes dark.
- **Product / market** — open-weight and vendor-neutral: NGOs and governments
  can deploy disaster AI without proprietary-cloud lock-in or export constraints.

## Agents

| Agent | Type | Model / method |
|---|---|---|
| Medical triage | LLM + rules | Gemma (ROCm local + Fireworks cloud) + START heuristic |
| Translation | LLM | Gemma via Fireworks |
| Vision damage assessment | multimodal LLM | Qwen2-VL via Fireworks |
| Route planning | algorithmic | A* on occupancy grid (zero tokens) |
| Resource allocation | algorithmic | severity-first greedy assignment |
| Missing-person matching | embeddings + LLM | Fireworks embeddings + Gemma reconcile; offline token-cosine fallback |

## Endpoints

| Method | Path | Purpose |
|---|---|---|
| GET | `/health` | liveness + which inference paths are up |
| GET | `/v1/infra` | AMD infrastructure status (cloud + GPU) |
| POST | `/v1/triage` | orchestrated triage (returns mode + reasoning trace) |
| POST | `/v1/connectivity/{on\|off}` | **demo toggle** for the uplink |
| POST | `/v1/vision` | structural damage assessment from a photo |
| POST | `/v1/missing-person` | match a missing person against victim records |
| POST | `/v1/route` | A* path on a grid |
| POST | `/v1/allocate` | assign response units to victims |

## Quick start

```bash
cd backend
cp .env.example .env            # add FIREWORKS_API_KEY
docker compose up --build       # cloud-only, runs anywhere
./scripts/smoke_test.sh         # verify
```

On an AMD Developer Cloud GPU instance (adds real Gemma-on-ROCm):

```bash
export HF_TOKEN=hf_...           # accept gemma-2-2b-it license on HF first
docker compose -f docker-compose.yml -f docker-compose.rocm.yml up --build
```

Full testing walkthrough: see **`TESTING.md`**.

## Architecture

```
mesh / dashboard packet
        │
        ▼
  ┌───────────────┐   reasoning_trace
  │ Orchestrator  │──────────────────► dashboard (LOCAL/CLOUD/HYBRID badge)
  │ decide_mode() │
  └──────┬────────┘
         │ per-packet routing
   ┌─────┴───────────────────────────┐
   ▼                                 ▼
 LOCAL                             CLOUD
 Gemma on AMD GPU (vLLM-ROCm)      Gemma / Qwen2-VL via Fireworks (AMD)
 └─ heuristic if no GPU            (best-quality reasoning)
         │                                 │
         └──────────── HYBRID ─────────────┘
             (local answer, cloud reconciles)
```

Existing OmniMesh (Android on-device TFLite triage, Firebase, Gemini dispatch)
is unchanged — this backend is an additive AMD-powered layer.
