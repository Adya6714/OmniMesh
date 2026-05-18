OmniMesh — Complete Technical & Problem Document
Version 2.0 — Updated May 2026

The Problem: Why People Die Unnecessarily After Disasters
Every year, earthquakes, cyclones, and building collapses kill tens of thousands of people. But here's what most people don't know: the majority of those deaths are not caused by the disaster itself. They are caused by the response failure that follows.
When a major earthquake strikes a city, three things happen simultaneously within the first 10 minutes. Cellular towers lose power or become so overloaded they effectively go dark. First responders arrive at the disaster zone with zero information — they don't know where anyone is, how many people are injured, or how severe those injuries are. And the people who need help most — those buried under rubble, unconscious, bleeding internally — are completely invisible to the rescue system because they cannot signal for help.
This window between the disaster striking and the first coordinated medical response is called the 72-hour Golden Hour. It is the period during which survival rates are highest and intervention has maximum impact. After 72 hours, survivable injuries become unsurvivable. People who could have been saved with timely treatment die waiting to be found.
The scale of this problem is enormous. The 2023 Turkey-Syria earthquake killed over 50,000 people. Post-disaster analysis consistently shows that a significant fraction of those deaths occurred in the first 24–48 hours among people who survived the initial collapse but died waiting for rescue. The Haiti earthquake of 2010, the Nepal earthquake of 2015, Cyclone Amphan in 2020 — the pattern repeats identically every time. Infrastructure collapses. Communication dies. Responders operate blind. People who could be saved, aren't.

Why Existing Technology Fails
The obvious question is: we have smartphones, Bluetooth, WiFi — why can't these be used to create a communication network when cellular fails?
They can. And several apps have tried. Meshtastic, GoTenna, Briar, Bridgefy — these are all real mesh networking applications. But every single one of them fails in a disaster context for the same four fundamental reasons:
They are dumb pipes. They transmit data but have no intelligence about what data matters. A message saying "where are you?" travels through the mesh with exactly the same priority as a message saying "person unconscious, compound fracture, basement level 2, bleeding heavily." In a network with limited bandwidth — Bluetooth Low Energy maxes out around 1–2 Mbps in ideal conditions, far less in a rubble-filled environment — this is fatal. The most critical information doesn't arrive first.
They require conscious action. Every existing mesh app requires the victim to open the app and send a message. An unconscious person cannot do this. A person trapped under a collapsed ceiling with both arms pinned cannot do this. The people who need help most are systematically excluded from every existing solution.
They can't process the data. Even if a mesh network successfully delivers messages to a command post, those messages are raw unstructured text. A first responder receiving 200 text messages from a disaster zone cannot quickly extract which three people are most critical, where they cluster geographically, and what resources they need.
They have no medical intelligence. When a victim sends "I'm hurt" or even "my leg is broken," a mesh app has no ability to assess severity, classify urgency, or determine whether this person needs immediate intervention or can wait. Triage requires medical training. In a sudden mass casualty event, there are never enough trained triageurs.
OmniMesh is built to solve all four of these problems simultaneously.

The Solution: AI-Governed Mesh Infrastructure
OmniMesh transforms every Android phone into an intelligent medical triage node. It does not just create a communication channel — it creates a prioritized, AI-governed, self-healing information network that operates entirely without internet, cellular, or any pre-existing infrastructure.
The system architecture has three distinct layers, each solving a different part of the problem.

Layer 1: The Edge — Multi-Signal AI Fusion
This is the core technical innovation of OmniMesh and the layer that makes it fundamentally different from anything that exists today.
The central insight is that no single model should make a life-or-death triage decision alone. A camera model might misidentify a shadow as blood. An audio model might confuse crying with wind. A motion model might misread a car crash as a building collapse. But when four independent models assess four different signals simultaneously and a meta-classifier fuses their outputs, the probability of a catastrophic false classification drops dramatically.
Signal 1: Injury Vision Classifier
The phone's camera captures an image of the injury or environment. This image is processed by a custom TFLite model trained on Vertex AI using AutoML with the EfficientNet architecture. The model classifies the image into five categories: RED (life-threatening — arterial bleeding, crush injury, airway compromise, unconscious person), YELLOW (serious but stable — fractures, lacerations, moderate burns), GREEN (minor — walking wounded, psychological distress), BLACK (deceased or incompatible with survival), and STRUCTURAL (building collapse with no person visible).
Training data comes from multiple sources: the ISIC archive for dermatological injury imagery, the PhysioNet trauma database, and disaster imagery from news archives automatically labeled using Gemini 1.5 Pro. AutoML applies aggressive augmentation — rotation, brightness variation, contrast shifts, noise — effectively multiplying the dataset ten times over. The model exports to TFLite format and is bundled directly in the APK, running with zero network dependency.
The key architectural choice is MOBILE_TF_VERSATILE_1 — Vertex AI's AutoML model type specifically optimized for mobile deployment. It produces a model small enough to bundle in an APK (under 10MB) but accurate enough for clinical triage support.
Camera capture in the Android app is exposed through the CAPTURE INJURY button on the Victim screen. After a photo is taken, the vision classifier runs inference and the result updates the triage packet urgency and confidence score in real time, visible immediately in the Responder triage queue and on the Command Center map.
Signal 2: Fine-Tuned Audio Classifier
The phone's microphone continuously samples ambient audio at 16kHz. A YAMNet-based model fine-tuned for disaster acoustics classifies this audio into six classes: fire crackling, human distress sounds (crying, screaming, groaning), structural creaking (building about to fail), machinery, silence in an enclosed space (a critical signal — enclosed silence often means someone is trapped), and normal outdoor ambient sound.
YAMNet recognizes 521 general sound categories but is not calibrated for disaster-specific acoustic signatures. The groan of a person trapped under rubble, the particular resonance of an enclosed concrete space, the specific creak of load-bearing structural failure — these require domain-specific training. We freeze YAMNet's weights entirely and train only a new classification head on top, gaining all of YAMNet's acoustic feature extraction capability while adding disaster-specific output classes. The resulting model is quantized to int8 format, reducing it to approximately 4MB while running 3–4× faster on the phone's NPU chip.
The audio classifier runs passively as part of the foreground service. Its output is surfaced in the ON-DEVICE AI ANALYSIS panel in both the Android Responder screen and the web Field view, displayed as an Acoustic Signature tile showing current detection state — e.g., "Low-frequency stress detected" with an animated audio bar visualization.
Signal 3: Collapse Motion LSTM
A bidirectional Long Short-Term Memory network trained on 5 seconds of accelerometer and gyroscope data (250 samples at 50Hz across 6 axes) classifies the motion state into six categories: collapse with unconscious victim, collapse with moving victim, phone drop, car crash, running, and normal movement.
This requires an LSTM rather than a simple threshold because of the temporal nature of a collapse signature. A phone drop and a building collapse both produce a large G-force spike. But a collapse has a specific temporal sequence: normal baseline movement → massive multi-axis spike (8–25G across all three accelerometer axes simultaneously; a drop typically affects only one or two axes) → high-frequency vibration decay as the structure settles → sustained near-zero movement. This is a sequence classification problem — the model needs to see the entire 5-second window to distinguish collapse from other high-G events.
The bidirectional LSTM processes the sequence in both forward and backward directions simultaneously, capturing both the run-up to the collapse event and the aftermath. The sustained stillness after the spike is the key differentiator between collapse and a dropped phone.
Training uses the WISDM and UCI HAR public accelerometer datasets supplemented with synthetic collapse signatures generated from physics models of building collapse dynamics. The model trains in Vertex AI Workbench and exports to a 2.1MB TFLite file.
A pre-filter ensures the LSTM only runs inference when the ring buffer detects a G-force spike above 4G in the last second. During normal daily use the threshold is almost never crossed, making the battery impact of 24/7 collapse monitoring negligible. The LSTM inference only fires when something unusual happens.
The collapse detector runs as an Android Foreground Service with FOREGROUND_SERVICE_TYPE_LOCATION (required on Android 10+). The service starts on boot and survives app death. On Android 10 and above, startForeground() is called with the explicit ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION type constant; on earlier versions it falls back to the two-argument form without a type flag. This is a required fix from the original implementation where FOREGROUND_SERVICE_TYPE_SPECIAL_USE caused a ForegroundServiceType mismatch crash on physical hardware.
Signal 4: Gemini Nano (On-Device Multimodal LLM)
Gemini Nano runs entirely on-device on compatible hardware (Pixel 8/8a/9 series, Samsung Galaxy S24 series) using the ML Kit Prompt API. It receives the injury photo plus GPS coordinates and timestamp, and produces a clinical narrative: injury description, urgency classification, and location description. Temperature is set to exactly 0.0 for deterministic output, and max tokens is capped at 80 to prevent runaway generation.
For devices without Gemini Nano, a routing system automatically falls back to Firebase AI Logic running Gemini Flash-Lite in the cloud if connectivity is available, or degrades gracefully to a low-confidence UNKNOWN signal that the meta-classifier handles appropriately.
Critically, Gemini Nano is Signal 4 in the fusion pipeline — not the decision-maker. Its output is one input to the meta-classifier alongside three other independent signals. This prevents any single model failure from causing a catastrophic misclassification.
The Meta-Classifier: Fusion Layer
All four signal probability vectors, plus contextual features (hour of day, battery level, GPS accuracy, whether the assessment is auto-generated), are assembled into a feature vector and fed into a lightweight tabular classifier trained on Vertex AI AutoML. This meta-classifier has been trained on human-verified ground truth and has learned to weight signals appropriately. A high-confidence vision signal with a supporting Gemini assessment outweighs an uncertain audio signal. An auto-generated motion signal with no vision confirmation gets appropriately discounted.
The output is a final urgency classification (RED / YELLOW / GREEN / BLACK) with a calibrated confidence score, plus a signal sources string indicating which models contributed meaningfully — e.g., "VAG" means Vision, Audio, and Gemini all had confident outputs; "M" means only Motion, indicating an auto-generated passive detection.
The signal sources string is surfaced in the packet table on both the Android Command screen and the web Command Center, in the triage cards on the Responder queue, and in the DataQualityBadge component — three vertical bars whose fill count matches the number of active signal sources. This gives every responder immediate visual confidence calibration on each packet.
The entire four-model pipeline runs in parallel using Kotlin coroutines — all four models execute simultaneously, and the meta-classifier fires as soon as all four complete. On a mid-range Android device, the entire pipeline completes in under 3 seconds.
The output is a 50-byte TriagePacket JSON: urgency, injury description, location, GPS coordinates, timestamp, unique ID, confidence score, and signal sources string.
Zero-Click Inertial SOS
The collapse detector runs as an Android Foreground Service 24/7. When the LSTM detects COLLAPSE_UNCONSCIOUS with confidence above 85%, it automatically generates a RED triage packet marked isAutoGenerated: true and saves it to the local Room database. The mesh layer picks it up immediately and begins transmitting it to nearby nodes.
The victim's phone notification updates to show SOS AUTO-TRIGGERED on the lock screen — visible to any bystander who finds the phone. A 30-second cancellation window prevents false alarms from triggering permanent alerts.
Voice SOS
The SPEAK YOUR INJURY button activates on-device speech recognition. The victim can describe their injury verbally — the transcription is used as the packet's injury field. This works in darkness, with injured hands, or when the victim cannot see the screen. If transcription fails or the API key is not configured, the packet falls back to voice_sos_triggered as the injury text rather than crashing.
AI Companion
The START COMPANION button activates a Gemini-powered conversational interface. The companion provides real-time first-aid guidance and psychological stability, and extracts clinical state — consciousness level, injury location, breathing status, pain scale — that responders will need on arrival. The companion runs on-device where Gemini Nano is available; otherwise it routes to Gemini Flash via the cloud API. The conversation is rendered as a chat interface with a TTS greeting on activation. A debug trigger button is present in development builds only and is conditionally compiled out of release builds.
START Triage Assessment (Self-Assess)
The SELF-ASSESS MY INJURIES button launches a four-question animated assessment flow. The victim answers YES or NO to each question. After question 4, the result screen shows a color-coded triage category (GREEN, YELLOW, or RED) with a clinical description. Tapping CONTINUE TO COMPANION loads the assessment result into the companion's context.

Layer 2: The Mesh — Priority-Ordered Packet Routing
Architecture
OmniMesh uses Google's Nearby Connections API with P2P_CLUSTER strategy. P2P_CLUSTER means all devices are equal peers — there is no hub, no coordinator, no single point of failure. Any phone can talk directly to any other phone. If one phone dies, the mesh automatically routes around it.
Each phone simultaneously advertises its presence and discovers other nodes. When two OmniMesh devices come within range (Bluetooth range is approximately 10–30 meters in open space, less in rubble), they automatically connect without any user interaction. Auto-acceptance of connections is a deliberate design decision — in a disaster there is no time for confirmation dialogs.
The NEARBY_WIFI_DEVICES permission (Android 12+) is declared in the manifest with android:usesPermissionFlags="neverForLocation" and is requested at runtime alongside Location and Bluetooth permissions. Without this permission, discovery falls back to BLE-only rather than the preferred BLE + WiFi LAN combination. The runtime permission request in MainActivity.kt includes this permission in the list passed to the permission launcher.
The peer count is exposed in the Android app's Victim screen under the hub as MESH CONNECTED: N PEERS and in the Responder and Command screen headers as a green pill. On the web, the peer count is shown in the Command and Responder headers. In Demo mode, the web displays a stable preset peer count (DEMO_PEER_COUNT) so screen recordings look consistent. In Real mode, the peer count reflects the actual observed packet count until live mesh telemetry is wired to the web layer.
The Priority Queue
The local Room database stores all TriagePacket records with a priority-ordered SQL query: RED first (priority 1), then YELLOW (2), GREEN (3), BLACK (4). Within the same urgency level, older packets go first — they've waited longer.
Every 5 seconds, the mesh relay service broadcasts this priority queue to all connected peers. When a new peer connects, RED packets are immediately flushed to them — no waiting for the next broadcast.
The result: in a network with 10 phones and 50 packets, a newly arriving RED packet from a bleeding victim reaches the command center before any GREEN packets — regardless of when the phones connected, regardless of mesh topology.
This priority ordering is enforced at the Room database SQL layer, not just the application display layer. The mesh relay service reads from the priority-ordered DAO query when constructing the broadcast payload. This is what distinguishes OmniMesh from Meshtastic, GoTenna, and Briar — the critical information travels fastest as a property of the data infrastructure, not a UI decoration.
Deduplication and Loop Prevention
Every TriagePacket has a unique 8-character ID. Before inserting any received packet, the system checks whether that ID already exists in the local database. If it does, the packet is silently dropped. When relaying a received packet to other peers, the system explicitly excludes the endpoint it received the packet from. Packets only flow forward, never back.
Exponential Backoff Retry
Failed packet sends retry up to three times with exponential backoff: 2 seconds, then 4 seconds, then 8 seconds.
Walkie-Talkie PTT
The HOLD TO TALK bar in the Responder screen activates push-to-talk audio capture. Audio is encoded at 8kHz mono PCM in 160ms frames. Each audio frame is prepended with the prefix "AUDIO|" before being sent as a Nearby Connections BYTES payload. The mesh relay layer routes payloads with this prefix to WalkieTalkieManager rather than attempting to deserialize them as TriagePacket JSON. On the receiving device, audio frames are played back in order. Two-way voice over Bluetooth mesh, no infrastructure required.
Buddy Groups
The buddy group system allows families or response teams to register a shared group. The group creator taps SETUP BUDDIES, creates a group with a display name, and receives a 6-character join code. Other devices enter the code to join. Group documents are stored in Firestore under the buddy_groups collection and are authenticated using Firebase Anonymous Auth. When any member's device triggers an auto-SOS, all other group members receive an FCM push notification containing GPS coordinates — delivered even with the screen off.

Layer 3: The Command Center — Semantic Intelligence
Firebase Sync
The moment any phone in the mesh gains internet connectivity — even a single bar of signal — WorkManager triggers a batch upload to Firebase Firestore. Firestore's batch write API consolidates up to 500 individual document writes into a single network request, critical for efficiency on a weak signal.
All packets are authenticated using Firebase Anonymous Auth. No signup, no password, no user friction — each device gets a unique anonymous UID automatically. Firestore security rules validate that all incoming packets have valid urgency values (RED, YELLOW, GREEN, BLACK), required fields (urgency, injury, lat, lon), and valid confidence ranges (0–1). The dispatch_analysis collection is read-only to clients — only Cloud Functions can write to it.
WorkManager's network constraint ensures the sync job only runs when connectivity is actually available. If the phone loses signal mid-sync, WorkManager queues the job and retries with exponential backoff when connectivity returns — even if the app has been killed in the interim.
The Room database uses a version number that is incremented when schema changes are deployed. fallbackToDestructiveMigration() is set on the Room builder for development builds to handle schema hash mismatches without crashing. Production builds will use explicit migrations.
Maps
Android: The Responder screen renders GPS-referenced triage markers on Google Maps using the Maps Compose library. RED pins pulse with an animation. Clusters of RED auto-detected packets in a small area are the visual signature of a structural collapse zone.
Web: The web Responder (Field) view renders the same markers on a Leaflet + OpenStreetMap dark tile layer. This requires no Google Maps API key or billing setup. The map renders pulsing RED triangle markers with a Leaflet heatmap overlay showing casualty density. Dark popup styling matches the overall dark UI. The Leaflet integration replaced an earlier Google Maps JavaScript API implementation to eliminate billing requirements for the hosted demo.
Gemini Dispatch Agent
Web
The $ analyze --zone button in the web Command Center calls Gemini 1.5 Flash directly from the React client. The prompt sends all active zone packets and forces structured JSON output with explicit schema requirements — zone assignments, confidence levels, a critical_alert string, and an est_casualties count. Temperature is set to 0.0 for deterministic output. Markdown is explicitly prohibited. If the response cannot be parsed, the system falls back to a simple priority sort.
The output renders in the terminal with a typewriter character-reveal animation. Zone assignments are displayed as cards with a blue left border. The critical_alert field renders as a red-bordered highlight box. est_casualties and generation timestamp appear at the bottom.
The Gemini API key is loaded from REACT_APP_GEMINI_API_KEY in the .env file. The model string is gemini-1.5-flash. The endpoint is https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent. In Demo mode, the analyze button returns a deterministic DEMO_ANALYSIS response after a 1.4-second artificial delay, preserving quota and ensuring consistent screen recordings.
Android
The RUN ANALYSIS button on the Android Command screen calls the same Gemini 1.5 Flash endpoint from the BuildConfig.GEMINI_API_KEY field populated from secrets.properties. The dispatch result renders in the same terminal UI format as the web.
Cloud Function
The onRedPacketTriggerDispatch Cloud Function triggers on any new Firestore document write to the packets collection where urgency == "RED". It calls Gemini 1.5 Flash server-side and writes the result to dispatch_analysis/latest. The web client can subscribe to this document to receive backend-owned analysis rather than running the agent on the client. The sendBuddyAlerts function handles FCM delivery when a buddy group member triggers SOS.
Web Command Center — Additional Features
Collapse Protocol Banner: A horizontally scrolling ticker rendered at the top of the Command screen. It activates when stats.autoCount >= 3 — when three or more packets with isAutoGenerated: true are present. The ticker reads "STRUCTURAL COLLAPSE PROTOCOL — N AUTO-SOS SIGNALS DETECTED — AWAIT ENGINEERING CLEARANCE BEFORE GROUND ENTRY — SECONDARY COLLAPSE RISK ELEVATED." This warning exists because a cluster of auto-SOS signals in a small area is the acoustic and motion signature of a floor collapse — and secondary collapse risk is highest in the same zone. The banner warns command not to send ground teams before engineering clearance.
Live Incident Timeline: A chronological event log at the bottom of the Command screen. Events are generated for every packet creation, every auto-SOS trigger, and every dispatch analysis update. Each event has a timestamp, a color-coded dot, and a description string.
Urgency Distribution Bar: A horizontal stacked bar showing the proportion of RED, YELLOW, and GREEN packets in the current dataset. Updates in real time as packets arrive.
Mesh Overview Visualization: An animated canvas showing nodes as dots (red for critical, blue for relay, green for healthy) with connecting lines drawn between nodes within a proximity threshold. A radar sweep animation overlays the canvas. Packets animate as moving dots traveling between nodes along the connection lines. All motion is driven by requestAnimationFrame with no external dependencies. Mesh metrics (latency ~43ms, hop average 3.1, packet loss 0.79%, uptime) are displayed below the canvas.
REAL/DEMO Toggle: A two-state pill in the top bar of all three web tabs. In DEMO mode, the web merges DEMO_PACKETS with any live Firestore data (or shows demo data alone if Firestore is empty), uses DEMO_ANALYSIS for the Gemini terminal, and shows DEMO_PEER_COUNT. In REAL mode, only live Firestore data is shown. The toggle state persists to localStorage. Switching modes clears the analysis to prevent stale demo zone assignments appearing against real data.
Deployment Dashboard (/dashboard route): A separate route accessible from the bottom nav or direct URL. Shows TOTAL DEPLOYMENTS, ACTIVE DEVICES, and ACTIVE INCIDENTS stat cards with real-time Firestore listeners. The + New Deployment button creates a deployment document in Firestore with a 6-character alphanumeric join code. Declare Incident sets incidentActive: true on the deployment document and shows a red hazard stripe banner on the card. Close Incident removes the banner. The dashboard is intended for NGO deployment coordinators managing multiple simultaneous incidents.
Android Command Center — Additional Features
Packet Table: All active packets displayed in a numbered table with columns for urgency badge, injury text, confidence percentage, and type (AUTO / MANUAL). Sorted by urgency priority matching the Room SQL order.
Incident Timeline: The same chronological event log as the web, backed by Room rather than browser-local state. Expandable from a collapsed header showing the event count.
DataQualityBadge: Shown on each triage card in the Responder queue. Three vertical bars whose fill count matches the number of active signal sources in the packet's signalSources string. Packets confirmed by a responder show a green checkmark and CONFIRMED label. Packets marked false positive show a red ✕ and FALSE+ label.

Platform Architecture: Three Tabs × Two Surfaces
OmniMesh is a three-layer product with full feature parity across Android and web:
Feature
Android
Web
Victim hub + passive detection
✓
✓ (simulation)
Manual SOS with injury selection
✓
✓
Voice SOS (STT)
✓
—
Camera + vision classification
✓
—
AI Companion (Gemini)
✓
—
Self-Assess triage flow
✓
—
Responder triage queue (RED-first)
✓
✓
GPS-referenced map with markers
✓ (Google Maps)
✓ (Leaflet/OSM)
On-device AI analysis panel
✓
✓
Walkie-talkie PTT
✓
—
Buddy groups + FCM alerts
✓
—
Collapse protocol banner
✓
✓
Gemini dispatch terminal
✓
✓
Mesh topology visualization
✓
✓
Live incident timeline
✓ (Room)
✓ (browser)
Urgency distribution bar
✓
✓
Packet table
✓
✓
DataQualityBadge
✓
—
Deployment Dashboard
—
✓ (/dashboard)
Simulation controls
—
✓ (Demo mode)
Real/Demo toggle
✓ (DATA button)
✓ (pill toggle)
Firestore real-time sync
✓
✓
Cloud Functions
—
✓ (triggers)

Technical Stack — Complete
Android
Language: Kotlin
UI: Jetpack Compose + Material 3
Architecture: ViewModel + LiveData + Room
Networking: Firebase Firestore SDK, WorkManager for sync scheduling
Mesh: Google Nearby Connections API (P2P_CLUSTER)
ML: TFLite (Vision Classifier, Audio Classifier, Motion LSTM, Meta-Classifier), ML Kit Gemini Nano Prompt API, MediaPipe Tasks Vision and Audio
Audio: Android AudioRecord at 8kHz PCM for walkie-talkie; 16kHz for audio classifier
Maps: Google Maps Platform, Maps Compose library
Sensors: SensorManager (Accelerometer + Gyroscope at 50Hz), FusedLocationProviderClient
Services: CollapseDetectorService (foreground, FOREGROUND_SERVICE_TYPE_LOCATION), MeshRelayService (foreground)
Push: Firebase Cloud Messaging (FCM) for buddy group alerts
Persistence: Room with priority-ordered SQL, fallbackToDestructiveMigration
Build: Gradle Version Catalogs, secrets.properties for API keys, BuildConfig injection
Min SDK: Android 8.0 (API 26)
Web
Framework: React (Create React App)
State: useState, useMemo, useRef hooks
Firebase: Firestore real-time onSnapshot listeners, Anonymous Auth
Maps: Leaflet + OpenStreetMap dark tiles (no API key required)
AI: Gemini 1.5 Flash via generativelanguage.googleapis.com/v1beta
Routing: React Router (/, /dashboard)
Hosting: Firebase Hosting with GitHub Actions CI (firebase deploy --only hosting on push to main)
Fonts: Space Grotesk (display), Inter (body), JetBrains Mono (tactical labels, GPS, IDs, terminal)
Cloud / Backend
Database: Firebase Firestore (native mode)
Auth: Firebase Anonymous Auth
Functions: Cloud Functions v2 — onRedPacketTriggerDispatch (Firestore trigger on RED packet write), sendBuddyAlerts (FCM delivery)
AI Backend: Gemini 1.5 Flash via Gemini API (both client-side and server-side paths)
ML Training: Vertex AI AutoML (Vision Classifier), Vertex AI Workbench (LSTM training), Google Colab (YAMNet fine-tuning)
CI: GitHub Actions (web build + Firebase Hosting deploy on main push)
Design System
Color palette: Google Marketing exact values — Blue #174EA6 / #4285F4 / #D2E3FC, Red #A50E0E / #EA4335 / #FAD2CF, Yellow #FBBC04, Green #0D652D / #34A853 / #CEEAD6, Dark backgrounds #0D1117 / #1C2026 / #161B22
Typography: Outfit (display/headlines), Inter (body), JetBrains Mono (all tactical labels, GPS coordinates, IDs, terminal output)
Triage color mapping: RED = #EA4335 with #202124 text, YELLOW = #FBBC04 with #202124 text (not white — accessibility), GREEN = #34A853

What Makes OmniMesh Unique
No existing solution combines all of these:
Passive detection without user action. No other disaster app does this. Meshtastic requires manual message sending. GoTenna requires the user to be conscious and have the app open. OmniMesh generates triage alerts for unconscious victims automatically.
Multi-signal fusion with confidence scoring. Every triage decision comes with a calibrated confidence score and a record of which signals contributed. A first responder knows whether a RED alert came from four independent models (very high confidence) or just the motion sensor alone (treat with more caution). This is medically responsible AI.
Priority-ordered mesh routing enforced at the data layer. The fundamental problem of existing mesh apps is solved at the SQL layer, not the display layer. The most critical packets always travel fastest regardless of network topology.
Graceful degradation at every layer. If Gemini Nano isn't available, fall back to cloud. If cloud isn't available, fall back to local model. If the motion model isn't loaded, return NORMAL. If the Dispatch Agent API call fails, fall back to priority sort. If Firestore sync fails, packets queue in Room and retry on connectivity return. At no point does any single failure bring down the system.
Zero infrastructure dependency. Any two Android phones within Bluetooth range form a mesh automatically. The minimum viable deployment is two phones.
Full-stack AI pipeline across three hardware surfaces. On-device ML (TFLite, Gemini Nano), mesh relay (Nearby Connections), cloud sync (Firestore, WorkManager), and AI command intelligence (Gemini 1.5 Flash) all operate as a coherent system. The web Command Center is not a dashboard bolted onto an Android app — it is a fully realized second surface with its own real-time Firestore subscription, its own Gemini terminal, its own map, and its own Deployment Dashboard.

UN SDG Alignment
Goal 3 — Good Health and Well-Being: Direct reduction in preventable mortality during the 72-hour golden hour through faster, more accurate triage and resource allocation.
Goal 11 — Sustainable Cities and Communities: Disaster resilience infrastructure that requires no permanent installation, no maintenance, no power grid. Every Android phone in a city is latent OmniMesh infrastructure.
Goal 13 — Climate Action: As climate change increases the frequency and severity of natural disasters globally, the need for offline-first disaster response infrastructure scales proportionally. OmniMesh addresses the systematic communication failure that climate-driven disasters will increasingly cause.

The Demo Moment
Place a phone face-down on a table and knock something heavy onto it — simulating a collapse. Within 8 seconds, without touching the phone, a RED triage packet with GPS coordinates appears on the Command Center map on both the web and any connected Android device. The notification on the phone reads SOS AUTO-TRIGGERED. A second phone on the same network receives the packet via mesh and displays it in the Responder triage queue — automatically sorted to the top above any previously existing YELLOW or GREEN packets.
No button pressed. No app opened. No human action. A person who just became unconscious is now visible to the rescue system.
The web demo is live at omnimesh-command.web.app. Toggle to Demo mode for simulation controls (Earthquake Scenario, Simulate Auto-SOS, Inject 5 RED, Clear). Toggle to Real mode to see live Firestore data. The Structural Collapse Protocol banner activates when three or more auto-SOS packets are present — trigger Earthquake Scenario followed by Simulate Auto-SOS once to reach this threshold.

Built for Google Solution Challenge 2026 — SDGs 3, 11, 13 Android · React · Firebase · Gemini · TFLite · Nearby Connections · Vertex AI
