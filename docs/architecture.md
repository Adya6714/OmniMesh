# OmniMesh system architecture

OmniMesh connects edge devices in disaster scenarios through opportunistic mesh relay and a cloud command plane. The stack splits into **Edge** (sense + classify + originate triage data), **Mesh** (offline-capable relay), and **Command** (authoritative sync + operator tooling + AI-assisted dispatch).

**Frozen PNG export** (for decks, README gallery, or PDFs where Mermaid does not render):

<p align="center">
  <img src="screenshots/architecture-render.png" alt="OmniMesh three-layer architecture diagram" width="720" />
</p>

_Source:_ [`screenshots/architecture-render.png`](screenshots/architecture-render.png) _(same graphic as repo-root `Edge Device Packet Dispatch May 10 2026.png`; regenerate when the diagram below changes.)_

---

## Mermaid source (editable)

```mermaid
flowchart TB
  subgraph EDGE["Edge layer — devices"]
    direction TB
    SENS["Phone sensors<br/>IMU · microphone · camera · GNSS · barometer"]
    ML["On-device ML<br/>motion · audio · vision · fusion pipeline · LSTM / TFLite"]
    TP["Triage packet<br/>urgency · injury · confidence · lat/lon · signals · AUTO-SOS flags"]
    SENS -->|"sensor frames / features"| ML
    ML -->|"fused triage outcome"| TP
  end

  subgraph MESH["Mesh layer — opportunistic P2P"]
    direction TB
    NC["P2P Nearby Connections<br/>discovery · links · walkie / relay services"]
    PQ["Priority queue<br/>RED-first ordering · PacketQueue DAO"]
    SF["Store-and-forward<br/>Room DB · hop relay · offline retention"]
    NC -->|"receive / forward frames"| PQ
    PQ -->|"dequeue by severity"| SF
    SF -->|"transmit to peers"| NC
  end

  subgraph COMMAND["Command layer — cloud + operators"]
    direction TB
    FS["Firestore sync<br/>`packets` collection · SyncWorker · security rules"]
    WEB["Web dashboard<br/>React · Victim / Responder / Command · Leaflet maps"]
    GEM["Gemini dispatch agent<br/>Cloud Functions · structured dispatch analysis"]
    FS <-->|"listeners · writes · demo merge"| WEB
    WEB -->|"analyze(displayPackets)"| GEM
    GEM -->|"critical_alert · zones · estimates"| WEB
  end

  TP -->|"new packet enters mesh"| NC
  SF -->|"sync uplink"| FS
  FS -->|"pull / broadcast mirror"| SF
  WEB -->|"manual + simulation writes"| FS

  linkStyle default stroke-width:2px
```

## Layer summary

| Layer | Role | Key technical components |
| ----- | ---- | ------------------------ |
| **Edge** | Capture signals and produce standardized triage artifacts | Sensor pipelines, TFLite (or similar) classifiers, fusion logic, `TriagePacket`-shaped payloads |
| **Mesh** | Move packets device-to-device when infrastructure is degraded | Android Nearby-style P2P, mesh relay services, Room/local queues, priority-ordered forwarding |
| **Command** | Shared truth, visualization, and AI-assisted coordination | Firestore, React web app, Cloud Functions (or hosted agents) calling Gemini for dispatch-style analysis |

## Cross-cutting flows

1. **Edge → Mesh**: A completed triage packet enters the mesh egress path (broadcast or targeted relay depending on implementation).
2. **Mesh ↔ Command**: When connectivity allows, packets sync to Firestore; other nodes may receive updates from the cloud as well as from peers.
3. **Command → operators**: The dashboard reflects live packet streams and triggers Gemini-backed dispatch analysis without replacing the authoritative packet store in Firestore.
