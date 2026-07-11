# OmniMesh AI — Testing & Deployment Guide

This is your step-by-step checklist to verify the backend works, deploy it on
AMD hardware, and rehearse the demo. Do these **in order**. Don't skip ahead —
each stage confirms the previous one worked.

---

## Stage 0 — What you're testing (read this first)

The backend has **three inference paths**, and the whole demo is about proving
they route correctly:

| Path | What runs | When it's used |
|---|---|---|
| **LOCAL (GPU)** | Gemma on your AMD GPU via vLLM-ROCm | Online node but you want edge inference, or as the instant answer in HYBRID |
| **LOCAL (heuristic)** | START-triage rule engine (no model) | GPU not available AND offline — the "never goes dark" fallback |
| **CLOUD** | Gemma via Fireworks AI (AMD-hosted) | Online, best quality |
| **HYBRID** | local answer first, then CLOUD reconciles | Online + critical (RED) case |

The **routing badge** (LOCAL / CLOUD / HYBRID) and the **reasoning_trace** are
what you show the judges. Everything below is about confirming those are correct.

---

## Stage 1 — Local unit tests (do this on your laptop, no GPU, no API key)

```bash
cd backend
python -m venv .venv && source .venv/bin/activate   # Windows: .venv\Scripts\activate
pip install -r requirements.txt
python -m pytest tests -v
```

**Expected:** `17 passed`. If anything fails, stop and fix before continuing.

What each test group proves:
- `test_health`, `test_infra_endpoint` — server + AMD-infra reporting works.
- `test_local_triage_*` — the offline START heuristic classifies RED/GREEN correctly.
- `test_mode_*` — routing picks LOCAL when offline, correctly reads node vs global link.
- `test_triage_endpoint_offline` — full request returns `mode: local`, severity RED.
- `test_reasoning_trace_present` — the explainability trace is populated.
- `test_missing_person_*` — semantic matching ranks the right candidate first.
- `test_vision_endpoint_no_cloud_graceful` — vision degrades safely with no key.
- `test_route_*`, `test_allocation_*` — A* pathfinding and resource allocation.

---

## Stage 2 — Run the server locally with a real Fireworks key

1. Get your Fireworks API key (fireworks.ai → dashboard → API Keys).
2. ```bash
   cp .env.example .env
   ```
   Edit `.env`, paste your key into `FIREWORKS_API_KEY=`.
3. **Confirm the Gemma model id is real.** In the Fireworks model catalog,
   check that `gemma-3-27b-it` exists. If the exact id differs, update
   `TRIAGE_MODEL` / `TRANSLATION_MODEL` / `RECONCILE_MODEL` in `.env` — no code
   change needed. Do the same for the vision model.
4. Boot it:
   ```bash
   uvicorn app.main:app --reload --port 8000
   ```
5. In another terminal, run the smoke test:
   ```bash
   ./scripts/smoke_test.sh
   ```

**Expected results, one by one:**
- `/health` → `cloud_available: true` (key is loaded).
- `/v1/infra` → shows Fireworks enabled.
- connectivity OFF → triage returns `"mode": "local"`.
- connectivity ON + RED hint → triage returns `"mode": "hybrid"`, and
  `reasoning_trace` mentions "Gemma via Fireworks".
- missing-person → top match is `v1` (the red-jacket boy).
- route → returns a `path` array reaching `[2,0]`.

**If a Fireworks call errors** (bad model id, no credits), the system should
NOT crash — it degrades to the heuristic and the trace tells you why. That
graceful degradation is itself a feature; but for the demo you want real Gemma
answers, so fix the key/model id until `mode: cloud` returns a real reasoning
sentence.

---

## Stage 3 — Docker (containerization is a hard hackathon requirement)

Still on your laptop OR the AMD instance:

```bash
cd backend
docker compose up --build
```

Then in another terminal: `./scripts/smoke_test.sh`

**Expected:** identical results to Stage 2, but now served from inside the
container. If this works, you've satisfied the "must be containerized" rule.

---

## Stage 4 — Deploy on AMD Developer Cloud (this is your AMD-platform proof)

Once your AMD Developer Cloud credits are approved and you've launched a GPU
instance:

1. SSH into the instance.
2. Clone your repo, `cd OmniMesh/backend`.
3. Run the guided setup:
   ```bash
   ./scripts/amd_setup.sh
   ```
   This checks `rocm-smi`, verifies Docker, launches **both** the backend AND a
   real Gemma model served on the AMD GPU via vLLM-ROCm, then saves
   `rocm-smi-proof.txt`.
4. **Take screenshots** of: `rocm-smi` output (shows the AMD GPU), the running
   containers, and `/v1/infra` now showing `local_gpu.enabled: true`.

> Note: `google/gemma-2-2b-it` is a gated model on Hugging Face. Accept its
> license on the HF model page and `export HF_TOKEN=hf_...` in your shell
> before running, so vLLM can download it. If GPU memory is tight, this small
> 2B model is deliberately chosen to fit comfortably.

**Now the LOCAL path is a real Gemma model on an AMD GPU** — verify by turning
connectivity OFF and confirming the triage `model_used` says `... (ROCm)`, not
`edge-start-heuristic`. **This is the single most important thing to demonstrate
for the AMD/Gemma prizes:** Gemma running on AMD hardware two ways (ROCm locally
+ Fireworks in cloud).

---

## Stage 5 — Wire the dashboard (already done by Cursor) and test the UI

1. ```bash
   cd web
   cp .env.example .env
   ```
   Set `REACT_APP_BACKEND_URL` to your backend URL (localhost:8000, or the AMD
   instance's public URL).
2. `npm start`
3. In the Command center:
   - Switch **Dispatch engine** to **OmniMesh AI backend**.
   - Flip **Connectivity OFF** → send/inject a packet → badge shows **LOCAL**.
   - Flip **Connectivity ON**, inject a **RED** packet → badge shows **HYBRID**.
   - Inject a non-critical packet while online → badge shows **CLOUD**.
4. Confirm the reasoning trace / mode badge updates visibly each time.

---

## Stage 6 — Vision & missing-person demo (the differentiators)

**Vision:**
```bash
curl -X POST http://localhost:8000/v1/vision \
  -H 'Content-Type: application/json' \
  -d '{"image_ref":"https://<a-public-image-of-building-damage>.jpg","note":"post-earthquake"}'
```
Expected: JSON with `damage_level`, `hazards`, `entry_safe`. Use a real damage
photo URL. (Base64 data URLs also work if you want offline-captured photos.)

**Missing-person:** already covered by the smoke test — show the judges a query
matching against a small victim list, with Gemma explaining *why* it matched.

---

## Stage 7 — Demo rehearsal script (what you actually say/do on camera)

Rehearse this exact sequence for the video — it's your winning narrative:

1. "OmniMesh already does offline triage on-device — edge-first disaster mesh."
2. Open dashboard, switch to **OmniMesh AI backend**. "This is our new
   AMD-hosted reasoning layer."
3. Flip connectivity **OFF**. Inject a critical packet. "No internet — the
   node still triages, using **Gemma running locally on an AMD GPU via ROCm**."
   Point to the **LOCAL** badge and the reasoning trace.
4. Flip connectivity **ON**. Inject a RED packet. "Uplink's back — it gives an
   instant local answer, then **Gemma on Fireworks reconciles** it." Point to
   the **HYBRID** badge.
5. Show `/v1/infra` and the `rocm-smi` screenshot. "Gemma on AMD hardware two
   ways — local ROCm and Fireworks cloud."
6. Show vision (damage photo → severity) and missing-person (family reunion).
7. Close on product/market: "Open-weight, vendor-neutral disaster AI —
   deployable by NGOs and governments without proprietary-cloud lock-in."

---

## Troubleshooting quick table

| Symptom | Cause | Fix |
|---|---|---|
| `cloud_available: false` | key not loaded | check `.env`, restart server |
| Fireworks 404 | wrong model id | update `*_MODEL` in `.env` to a real catalog id |
| Fireworks 401 | bad/expired key | regenerate on fireworks.ai |
| triage always heuristic offline | `LOCAL_MODEL_URL` empty | run the rocm compose override on the GPU instance |
| vLLM won't download Gemma | gated model | accept license on HF, `export HF_TOKEN=...` |
| dashboard badge never changes | wrong backend URL | fix `REACT_APP_BACKEND_URL` in `web/.env` |
| CORS error in browser | backend not reachable | confirm backend is up and URL/port match |
