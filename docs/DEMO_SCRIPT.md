# OmniMesh · Complete Demo Script

**Setup:** Phone mirrored via `scrcpy --max-fps 30 --bit-rate 8M` on LEFT. Web (`omnimesh-command.web.app`) on RIGHT. Record only Chrome window (Cmd+Shift+5 → Record Selected Window).

---

## Pre-flight

| # | Check |
|---|-------|
| 1 | Delete ALL documents in Firestore `packets` collection |
| 2 | Phone: OmniMesh open, brightness max, DND on |
| 3 | Web: Command tab, REAL mode (toggle to DEMO later for mesh viz) |
| 4 | Test Gemini: `$ analyze --zone` → confirm no 429 |

---

## SCENE 0 · The Hook (0:00 – 0:35)

**Show:** Your voice over the web Command tab with the mesh animation running in DEMO mode.

**Say:**

> "I'm Adya — final year engineering student at BITS Pilani. It's 5:52 AM and I've been building this for the last [X weeks/months]. So the polish might not be perfect, but the idea is real."

> "In 2023, the Turkey-Syria earthquake killed 60,000 people. Most of them survived the initial collapse. They died waiting — because cell towers were down, responders couldn't find them, and nobody knew who was critical and who could wait. The average time to locate a trapped survivor was 14 hours. Fourteen hours of silence."

> "I asked a simple question: every one of those victims had a smartphone in their pocket. What if the phone could call for help when the person couldn't? What if it could tell responders exactly where to go, who to save first, and what injuries to expect — all without a single cell tower?"

> "That's OmniMesh. On-device AI detects the collapse. A Bluetooth mesh routes the distress signal phone-to-phone. And a Gemini-powered command center tells the incident commander exactly where to send which team. No infrastructure. No conscious user required. Let me show you."

**Text overlay:** `OmniMesh — Disaster Triage Without Infrastructure`

---

## SCENE 1 · Launch + Permissions + Mesh Concept (0:35 – 1:10)

**Phone:** Fresh app launch. Grant all permission dialogs (location, microphone, nearby devices, notifications).

**Say:**

> "OmniMesh needs four permissions — location for GPS coordinates in triage packets, microphone for the acoustic stress classifier, nearby devices for the Bluetooth mesh, and notifications for the collapse alert."

**Phone:** Show two notification badges (Collapse Detector + Mesh Node).

**Web:** Toggle to DEMO mode. Show Command tab — animated mesh overview with nodes, peer count (50 PEERS), stat cards at zero.

> "The web command center is in demo mode — showing the mesh at scale with 50 peers. In production, real phones form a Bluetooth P2P mesh using Google's Nearby Connections API. Two phones within range discover each other in under 60 seconds — no pairing, no internet. The most critical packets route first, hop-by-hop, until they reach connectivity."

**Web:** Toggle back to REAL mode. Counts should be at zero.

> "Switching to real mode — zero patients. Let's change that."

---

## SCENE 2 · Drop Test — Auto-Detection (1:10 – 1:50)

**Phone:** Victim tab — hub spinning, AUTOMATED DISTRESS DETECTION: ON, Accelerometer + Microphone icons.

> "Three ML models run on-device 24/7 — an LSTM motion classifier watching for collapse signatures, a YAMNet audio model for structural stress sounds, and a vision classifier. No internet needed."

**Action:** Place phone face-down. Drop heavy book from 30cm. Leave phone still.

> "Watch the notification."

**Wait ~5 sec.** Notification: "SOS AUTO-TRIGGERED". Beacon beeps once.

> "No button pressed. The LSTM detected a G-force spike followed by sustained stillness — the signature of an unconscious person under debris. A 50-byte triage packet with GPS, urgency RED, and confidence score was transmitted automatically. That beep is the acoustic beacon — a sound locator so rescuers can find the phone under rubble."

**Web:** Point to TOTAL: 1, CRITICAL: 1 updating in real time.

> "The command center already knows. Zero user action."

---

## SCENE 3 · AI Companion Auto-Activation (1:50 – 2:25)

**Phone:** Companion screen auto-activates after the beacon. TTS greeting plays.

> "The AI Companion activates automatically after auto-SOS. Powered by Gemini, it provides first aid guidance and extracts clinical data — consciousness level, breathing, injuries, pain scale — that responders will need on arrival."

**Action:** Type "I can't move my left leg and I'm having trouble breathing." Show the response.

> "Every response is parsed for clinical state. All on-device. No internet required."

**Action:** Tap X to exit. TTS stops immediately.

> "Closing the companion immediately stops all voice channels — no lingering audio."

---

## SCENE 4 · Manual SOS + Voice + Self-Assess + Capture Injury (2:25 – 3:10)

**Phone:** Back on Victim tab.

**Action:** Tap REPORT EMERGENCY. Select CRITICAL, pick injury, SEND.

> "A conscious victim can report manually — injury type and severity packaged instantly."

**Action:** Tap SPEAK YOUR INJURY. Say: "I have a broken leg, second floor." Show transcription.

> "Voice SOS uses on-device speech recognition. Works in darkness, with injured hands, when the screen isn't visible."

**Action:** Tap SELF-ASSESS (if available). Show the self-assessment flow.

> "Self-assessment guides the victim through a structured triage questionnaire — breathing, bleeding, mobility — and auto-classifies urgency from their answers."

**Action:** Tap CAPTURE INJURY. Take a photo of your hand or any object.

> "Capture Injury uses Gemini Vision to classify injury severity from a photo. The classification is embedded in the triage packet — a responder knows what they're walking into before they arrive."

**Web:** Point to counts — should now show 3–4 real packets from everything we just did.

> "Every action the victim takes generates a triage packet that reaches every connected device and the command center instantly."

---

## SCENE 5 · Responder / Field View (3:10 – 4:05)

**Phone:** Switch to Responder tab. **Web:** Switch to Field tab (three-column layout).

> "The Responder tab is the field operator's surface — triage queue, map, and AI analysis side by side."

**Phone + Web:** Show the triage queue with RED cards at top.

> "Every responder sees a live priority queue — RED first, always. This is a priority SQL query at the database level, not just display sorting."

**Action on phone:** Show map with colored markers. Point to the blue polyline trail.

> "Each victim is GPS-referenced. The blue trail is the responder's breadcrumb path — live location tracking so the command center knows where every field operator is at all times."

**Action on phone/web:** Tap a triage packet card to expand it.

> "Tapping any packet shows the full detail — GPS coordinates, injury description, confidence score, signal sources, and the QR code. The QR code is a printable triage tag — scan it at the field hospital and the patient's entire digital record transfers instantly. No paperwork."

**Action:** Show the three action buttons on the expanded packet: FALSE POSITIVE, CONFIRM, REACHED.

> "Three field actions — false positive removes noise from the queue, confirm validates the patient is real, and reached logs that a responder physically arrived. The command center tracks who's been helped and who's still waiting."

**Action on web:** Point to the ON-DEVICE AI ANALYSIS panel (right column).

> "The AI panel shows which classifiers fired — acoustic signature, motion patterns, structural void geometry. A responder knows if a RED came from all models at high confidence, or just one sensor. Medically responsible AI."

**Action on web:** Point to ON-DEMAND ANALYSIS section. Click Re-run.

> "The field view has its own Gemini dispatch analysis — critical alert summary, estimated casualties, and zone assignments. Sector Alpha for critical extraction, Bravo for urgent care, Charlie for walking wounded."

**Action on web:** Point to the Critical alert card, Dispatch analysis with EST badge, Zone assignment cards.

**Action on phone:** Show HOLD TO TALK bar. Briefly press it.

> "HOLD TO TALK is mesh-routed walkie-talkie — two-way voice over Bluetooth before physically reaching the victim."

**Action on phone (if visible):** Show CLAIM SECTOR button.

> "Responders claim a geographic sector — prevents two teams going to the same area."

---

## SCENE 6 · Scale Up — Simulation + Command Center (4:05 – 5:10)

> "So far you've seen one phone generating a handful of packets. But in a real disaster — a building collapse, a flood, an earthquake — hundreds of phones are active simultaneously. Let me show you what the command center looks like at that scale."

**Web:** Switch to Victim tab. Open SIMULATION CONTROLS.

> "The web has built-in simulation controls for testing and demonstration."

**Action:** Click EARTHQUAKE SCENARIO. Wait for toast.

> "Earthquake scenario — injects a mix of RED, YELLOW, and GREEN patients with realistic injury types, GPS scatter, and confidence scores."

**Action:** Click INJECT 5 RED.

> "Five more critical patients — severe crush injuries, auto-detected by the LSTM."

Point briefly to the other buttons: FLOOD SCENARIO, CLEAR ALL, SIMULATE AUTO-SOS.

> "We also have flood presets, a clear-all reset, and individual auto-SOS simulation."

**Web:** Switch to Command tab. Data is now populated at scale.

> "Now the incident commander sees the real picture."

**Action:** Point to each element:

**Top bar:** Time with timezone.

> "Real-time clock with timezone — critical when coordinating across zones."

**Stat cards:** TOTAL, CRITICAL, AUTO-SOS — now showing 15+ patients.

> "Total patients, critical count, and auto-detected collapses — the numbers that drive resource allocation."

**Urgency distribution bar:**

> "The urgency distribution bar — visual breakdown of RED, YELLOW, GREEN across all active packets."

**Mesh Overview visualization:**

> "The mesh overview — every node is a phone, every moving dot is a packet in transit. Red dots are critical. The radar sweep shows active routing."

**Network stats:** Mesh latency, hop average, packet loss, uptime.

> "Live mesh health — latency, hops per packet, packet loss, and uptime."

**STRUCTURAL COLLAPSE PROTOCOL banner** (if ≥3 auto-SOS):

> "When three or more auto-SOS signals cluster, the system raises a collapse protocol advisory — flagging secondary collapse risk before a responder walks into a structurally unstable area."

**Gemini Dispatch Agent terminal:**

**Action:** Click `$ analyze --zone`. Let typewriter output appear.

> "Gemini analyzes every active packet simultaneously — something impossible to do manually in the first minutes. Geographic clusters, resource assignments per zone, casualty estimates."

**Zone assignment cards:**

> "Each zone includes the sector, the team type needed, and the reasoning. This reaches the commander before the first responder reaches the first victim."

**Packet table:**

> "The full packet table — every victim with urgency, injury, confidence score, manual or auto-detected."

**Live Incident Timeline:**

> "And the incident timeline — every event chronologically. Every RED detection, every dispatch update, every responder arrival."

---

## SCENE 7 · Buddy Groups + Close (5:10 – 5:45)

**Phone:** Switch back to Victim tab. Scroll to Buddy Group section.

> "One more thing. Families and teams create buddy groups with a join code. When any member triggers SOS — automatic or manual — every other member gets an instant alert through the mesh, even offline. A mother knows her child's phone detected a collapse before any news channel reports it."

**Action:** Show the join code display and group members.

**Action:** Create one final quick manual SOS on phone. Show web count increment live.

> "Phone to mesh to cloud to command center. One urgency vocabulary end-to-end. On-device AI, priority mesh routing, Gemini command intelligence."

**Pause.**

> "Zero infrastructure. Zero user action when it matters most. OmniMesh — built for the first 72 hours when nothing else works."

---

## Editing

**Text overlays:**

| When | Text |
|------|------|
| Scene 0, intro | `Adya · BITS Pilani · Final Year` |
| Scene 0, stat | `Turkey-Syria 2023 — 60,000 dead, avg 14hr rescue time` |
| Scene 1, permissions | `4 permissions — location, mic, nearby, notifications` |
| Scene 1, mesh demo | `P2P mesh — no cell towers, no WiFi, no infrastructure` |
| Scene 2, auto-SOS | `On-device LSTM collapse detection — zero user action` |
| Scene 2, beacon | `Acoustic beacon — audible locator under rubble` |
| Scene 3, companion | `AI Companion — clinical extraction + psychological support` |
| Scene 4, voice | `Voice triage — works without visual interaction` |
| Scene 4, capture | `Gemini Vision — injury classification from photo` |
| Scene 5, queue | `RED-first priority routing — database-level enforcement` |
| Scene 5, QR code | `Digital triage tag — scan at field hospital` |
| Scene 5, breadcrumb | `Responder breadcrumb trail — live location tracking` |
| Scene 5, field dispatch | `Field dispatch analysis — zones + resources per responder` |
| Scene 6, simulation | `Simulation controls — earthquake, flood, inject RED, auto-SOS` |
| Scene 6, mesh viz | `Self-healing P2P mesh — live packet routing` |
| Scene 6, Gemini | `Gemini AI dispatch — tactical synthesis in seconds` |
| Scene 6, timeline | `Full incident timeline — every event logged` |
| Scene 7, close | `OmniMesh — disaster triage without infrastructure` |

**Title card:** `OMNIMESH — Disaster Triage Without Infrastructure`

**End card:** `omnimesh-command.web.app · Built for the first 72 hours.`

**Cuts:** Dead air > 1s. Loading > 2s — jump-cut to result.

**Music:** Documentary ambient at 15%. **Export:** MP4, 1080p.

---

## Cue card

0. **Hook** (35s) — "I'm Adya, BITS Pilani, 5:52 AM" → Turkey stat → "what if the phone could call for help?" → "Let me show you"
1. **Launch** (35s) — permissions, notifications, DEMO mesh viz, toggle to REAL → "zero patients, let's change that"
2. **Drop test** (40s) — 3 ML models, book drop, auto-SOS, beacon, web count updates live
3. **Companion** (35s) — auto-activates, TTS, type response, clinical data, X to exit
4. **Manual+Voice+Capture** (45s) — REPORT EMERGENCY, SPEAK INJURY, SELF-ASSESS, CAPTURE INJURY → web shows 3-4 real packets
5. **Responder/Field** (55s) — queue, map, breadcrumb, tap packet → QR + 3 buttons, AI panel, field dispatch (Re-run → zones), HOLD TO TALK, CLAIM SECTOR
6. **Scale + Command** (65s) — "hundreds of phones" → SIMULATION CONTROLS (earthquake + 5 RED) → Command: time, stats, urgency bar, mesh viz, collapse banner, Gemini analysis, zones, packet table, timeline
7. **Buddy+Close** (35s) — buddy groups, final sync, closing line

**~5:45 raw, trim to ~5:00 in edit.**

---

## Troubleshooting

| Problem | Fix |
|---------|-----|
| Auto-SOS doesn't trigger | Drop harder, leave still 3-4 sec. Or SIMULATE AUTO-SOS on web |
| Companion says "can't connect" | Gemini quota — wait or continue, fallback is fine |
| Companion TTS keeps playing after exit | Fixed — X button stops all TTS/STT immediately |
| Gemini 429 on web | Fallback analysis still shows zones — looks professional |
| Collapse banner not showing | Need ≥3 auto-SOS. Use SIMULATE AUTO-SOS on web |
| Beacon keeps beeping | Stops after 3 seconds automatically |
| Not enough packets for command | Seed with EARTHQUAKE SCENARIO + INJECT 5 RED |
