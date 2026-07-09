# OmniMesh AI — Master Test Plan (existing + new AMD features)

This is your single source of truth for testing everything, in the right order.
It weaves the **new AMD backend** into your **existing** OmniMesh test flow.

**Rule:** do each stage fully, confirm it works, THEN move on. Report back after
each stage. Don't test on top of something broken.

---

## First: which stages you can do WHEN

| Stage | What | Needs |
|---|---|---|
| A | Backend unit tests | Just your laptop |
| B | Backend live + real Gemma | Fireworks key (you have it) |
| C | New features (vision, missing-person, uncertainty, routing) | Fireworks key |
| D | Web dashboard ↔ backend wiring | Backend running + npm |
| E | Existing web features (Firebase, Gemini dispatch) | Firebase project + Gemini key |
| F | Existing Android features (emulator) | Android Studio |
| G | Docker (containerization requirement) | Docker Desktop OR AMD instance |
| H | AMD Developer Cloud + ROCm (Gemma on AMD GPU) | AMD credits (2–3 day approval) |
| I | Physical 2-phone mesh / drop test | 2 Android phones |

Stages **A–F you can start today**. G needs Docker. H needs your AMD credits. I
needs phones. Do them in this order.

---

## All the API keys / accounts you need (get these in parallel now)

| Key / account | For | Where | Status |
|---|---|---|---|
| Fireworks API key | new backend (Gemma, vision, embeddings) | fireworks.ai → API Keys | ✅ you have it, in backend/.env |
| AMD AI Developer Program | AMD Cloud + extra credits | amd.com → AI Developer Program | ⏳ sign up NOW (2–3 day approval) |
| Hugging Face token | download gated Gemma for ROCm | huggingface.co → Settings → Tokens; accept gemma-2-2b-it license | ⏳ needed for Stage H |
| Gemini API key | EXISTING web/Android dispatch + vision + companion | ai.google.dev | you already have this working |
| Firebase project | EXISTING Firestore/Functions/Auth | Firebase Console | already set up (omnimesh-command) |
| Google Maps SDK key | EXISTING Android responder map | Google Cloud Console → Maps SDK for Android | already set up |
| Cloud Speech API | EXISTING voice SOS transcription | Google Cloud Console | already set up |

Two brand-new things to get today: **AMD AI Developer Program signup** (the lag is
the bottleneck for Stage H) and a **Hugging Face token** with the Gemma license
accepted.

---

## STAGE A — Backend unit tests (laptop only, ~5 min)

```bash
cd path/to/OmniMesh/backend
python3 -m venv .venv
source .venv/bin/activate            # Windows: .venv\Scripts\activate
pip install -r requirements.txt
python -m pytest tests -v
```

**PASS =** last line says `17 passed`.
If it fails: check you're in the `backend` folder (`ls` should show `app`,
`tests`, `requirements.txt`), and that `agents/` has `vision.py` +
`missing_person.py` (confirms you have the final version).

---

## STAGE B — Backend live with real Gemma (laptop, ~10 min)

`.env` already has your Fireworks key. Boot the server:

```bash
uvicorn app.main:app --reload --port 8000
```
Leave it running. In a SECOND terminal:

```bash
cd path/to/OmniMesh/backend
./scripts/smoke_test.sh
```

Check each line of output:
- `/health` → `cloud_available: true`
- `/v1/infra` → Fireworks enabled, shows the Gemma model id
- connectivity OFF → triage `"mode": "local"`
- connectivity ON + RED → triage `"mode": "hybrid"`, trace mentions "Gemma via Fireworks"

**If you get a Fireworks error (404 / model not found):** the Gemma model id in
`.env` doesn't match the catalog. Fix: on fireworks.ai, find the exact Gemma model
name, put it in `TRIAGE_MODEL` / `TRANSLATION_MODEL` / `RECONCILE_MODEL` in `.env`,
restart the server. No code change.
**401 error:** key wrong/expired — regenerate on fireworks.ai.

**PASS =** you see a real Gemma reasoning sentence come back in `mode: cloud`.

---

## STAGE C — New features (laptop, ~15 min)

Backend still running. Test each new feature with a curl (copy-paste whole block):

**1. Uncertainty-awareness** (the winning feature)
```bash
curl -s -X POST localhost:8000/v1/triage -H 'Content-Type: application/json' \
  -d '{"text_report":"minor scrape on arm","vitals":{"hr":78}}' | python3 -m json.tool
```
Look for: `"needs_human_review": true`, an `"uncertainty"` number, and the
`dispatch_summary` containing `[NEEDS HUMAN REVIEW]`. Now try a clearly-critical
case and confirm `needs_human_review` is `false`:
```bash
curl -s -X POST localhost:8000/v1/triage -H 'Content-Type: application/json' \
  -d '{"text_report":"victim not breathing, trapped under rubble"}' | python3 -m json.tool
```

**2. Reasoning trace** — in both responses above, look at `reasoning_trace`. It
should read like a decision log: ROUTE → LOCAL/CLOUD → UNCERTAINTY → DISPATCH.

**3. Missing-person matching**
```bash
curl -s -X POST localhost:8000/v1/missing-person -H 'Content-Type: application/json' \
  -d '{"query":"boy red jacket age 8 near school","candidates":[{"id":"v1","description":"young boy in red jacket found near the school gate"},{"id":"v2","description":"elderly woman blue saree at market"}]}' | python3 -m json.tool
```
Look for: `matches` list with `v1` ranked first (highest score), and (if cloud is
up) a one-sentence explanation of why.

**4. Vision damage assessment** — needs a public image URL of building damage:
```bash
curl -s -X POST localhost:8000/v1/vision -H 'Content-Type: application/json' \
  -d '{"image_ref":"https://upload.wikimedia.org/wikipedia/commons/earthquake-damage.jpg","note":"post-earthquake building"}' | python3 -m json.tool
```
Replace the URL with any real damage photo URL. Look for: `damage_level`,
`hazards`, `entry_safe`. (If no cloud, it degrades gracefully — that's expected.)

**5. Route + allocation** (offline, zero tokens — always works)
```bash
curl -s -X POST localhost:8000/v1/route -H 'Content-Type: application/json' \
  -d '{"grid":[[0,0,0],[1,1,0],[0,0,0]],"start":[0,0],"goal":[2,0]}'
```
Look for a `path` array reaching `[2,0]`.

**PASS =** all five return sensible JSON.

---

## STAGE D — Web dashboard ↔ new backend (Cursor already wired this)

Backend still running on :8000. New terminal:
```bash
cd path/to/OmniMesh/web
cp .env.example .env       # ensure REACT_APP_BACKEND_URL=http://localhost:8000
npm install                # if you haven't before
npm start
```
Browser opens to the dashboard. In the **Command** center:
1. Switch **Dispatch engine** to **OmniMesh AI backend**.
2. Flip **Connectivity OFF** → inject/create a packet → badge shows **LOCAL**.
3. Flip **Connectivity ON**, inject a **RED** packet → badge shows **HYBRID**.
4. Inject a non-critical packet while online → badge shows **CLOUD**.
5. Confirm the routing badge and (if wired) the reasoning trace update visibly.

**PASS =** the badge changes LOCAL → CLOUD → HYBRID as you flip the toggle. This
is your live demo centerpiece — rehearse it here.

**Ask Cursor** to also surface the new fields if not already shown: "On the
dispatch result card, display needs_human_review as a warning badge and show the
uncertainty value; add a small panel that lists reasoning_trace lines."

---

## STAGE E — EXISTING web features (your original test doc, condensed)

Now confirm the old stack still works (it's untouched, but verify). Full detail is
in your existing test plan; the must-checks:
1. Load omnimesh-command.web.app → DevTools Console shows zero red errors.
2. Victim / Responder / Command tabs each render (no white screen).
3. Firebase: Network tab shows WebSocket to firestore.googleapis.com; Anonymous
   Auth is ON in Firebase Console.
4. Manually add a RED doc to Firestore `packets` → it appears on Responder tab
   within 3s → Command shows total 1, critical 1.
5. **Gemini dispatch (the EXISTING engine):** on Command, switch Dispatch engine
   back to **Gemini**, click Run Analysis → typewriter output with zone
   assignments appears. This confirms both engines coexist.
6. `/dashboard` → New Deployment → join code appears; Declare/Close Incident works.

**PASS =** existing Gemini flow AND new backend flow both work, switchable via the
toggle. That coexistence is a strong story for judges (vendor-neutral fallback).

---

## STAGE F — EXISTING Android features (emulator)

Follow your existing Phase-2 emulator plan. The critical ones to confirm nothing
regressed:
1. App launches → two service notifications (OmniMesh Active + Mesh).
2. Permissions all granted.
3. SOS button → packet appears in Firestore.
4. Voice SOS → transcription in packet.
5. Capture Injury → VisionClassifier logs Gemini call, packet urgency updates.
6. Collapse sim (Virtual Sensors accelerometer spike) → auto-SOS fires.
7. START triage 4-question flow → color-coded result → companion.
8. Responder map renders, pins show, tap → detail sheet + QR card.
9. Command tab → Gemini re-analyze → timeline populates.

**PASS =** the emulator flow works end-to-end (this is your existing, proven
system — you've done this before).

> Note: Android is NOT yet wired to the new backend — that's a later, optional
> step. For the hackathon, the web dashboard showing the backend is enough. If
> time allows, we add an Android toggle that calls the backend too.

---

## STAGE G — Docker (containerization is a HARD hackathon requirement)

On your laptop (if you install Docker Desktop) OR on the AMD instance:
```bash
cd path/to/OmniMesh/backend
docker compose up --build
```
Then `./scripts/smoke_test.sh` in another terminal — same results as Stage B, but
now served from the container.

**PASS =** smoke test passes against the containerized backend. This satisfies
"all submissions must be containerized."

---

## STAGE H — AMD Developer Cloud + ROCm (the AMD-platform proof + Gemma prize)

Once your AMD credits are approved and you've launched a GPU instance:
```bash
# SSH into the instance, clone repo, then:
cd OmniMesh/backend
export HF_TOKEN=hf_...     # accept gemma-2-2b-it license on HF first
./scripts/amd_setup.sh
```
This checks `rocm-smi`, launches the backend + **Gemma on the AMD GPU via
vLLM-ROCm**, and saves `rocm-smi-proof.txt`.

Then verify the LOCAL path is now a REAL model on AMD hardware:
```bash
curl -s -X POST localhost:8000/v1/connectivity/off
curl -s -X POST localhost:8000/v1/triage -H 'Content-Type: application/json' \
  -d '{"text_report":"leg fracture, conscious"}' | python3 -m json.tool
```
Look at `model_used` — it should say `... (ROCm)`, NOT `edge-start-heuristic`.

**Screenshot for submission:** `rocm-smi` output, the running containers, and
`/v1/infra` showing `local_gpu.enabled: true`.

**PASS =** Gemma answering from the AMD GPU with connectivity off. This is the
single most important thing for the AMD + Gemma prizes: **Gemma on AMD hardware
two ways — ROCm locally and Fireworks in the cloud.**

---

## STAGE I — Physical 2-phone tests (existing system, for the demo video)

Follow your existing Phase-3 plan: install APK on 2 phones, battery whitelist,
drop test (target 90%+ true positive), 2-device mesh (airplane mode → offline
delivery), priority ordering (RED before GREEN), walkie-talkie. These are for the
demo video, not the backend.

---

## Sign-off checklist (everything green before submission)

- [ ] A: 17 unit tests pass
- [ ] B: real Gemma answer via Fireworks
- [ ] C: uncertainty flag, reasoning trace, missing-person, vision all return JSON
- [ ] D: dashboard badge flips LOCAL/CLOUD/HYBRID live
- [ ] E: existing Gemini dispatch still works, both engines switchable
- [ ] F: emulator flow unbroken
- [ ] G: containerized backend passes smoke test
- [ ] H: Gemma on AMD GPU (ROCm) confirmed, rocm-smi screenshot captured
- [ ] I: drop test + offline mesh recorded for the video
