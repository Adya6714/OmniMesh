import { useMemo, useState } from "react";

import FieldMap, {
  formatResponderPacketId,
  priorityHeading,
  signalsLine,
  sourceLine,
} from "./FieldMap";
import OnDeviceAnalysisPanel from "./OnDeviceAnalysisPanel";

const FALLBACK_CENTER = { lat: 37.7749, lng: -122.4194 };

export default function ResponderPanel({
  packets = [],
  demoMode = true,
  analysis,
  analyzing,
  dispatchEngine = "gemini",
  onAnalyze,
}) {
  const [selectedId, setSelectedId] = useState(null);

  const sortedPackets = useMemo(() => {
    const priority = { RED: 0, YELLOW: 1, GREEN: 2 };
    return [...packets].sort((a, b) => (priority[a.urgency] ?? 3) - (priority[b.urgency] ?? 3));
  }, [packets]);

  const counts = useMemo(() => {
    let red = 0;
    let yellow = 0;
    let green = 0;
    for (const p of packets) {
      if (p.urgency === "RED") red += 1;
      else if (p.urgency === "YELLOW") yellow += 1;
      else green += 1;
    }
    return { red, yellow, green };
  }, [packets]);

  const mapCenter = useMemo(() => {
    const valid = packets.filter((p) => p.lat != null && (p.lng ?? p.lon) != null);
    if (valid.length === 0) return FALLBACK_CENTER;
    const lat = valid.reduce((s, p) => s + p.lat, 0) / valid.length;
    const lng = valid.reduce((s, p) => s + (p.lng ?? p.lon), 0) / valid.length;
    return { lat, lng };
  }, [packets]);

  const markerCount = useMemo(
    () => packets.filter((p) => p.lat != null && (p.lng ?? p.lon) != null).length,
    [packets],
  );

  const estCasualties =
    analysis?.estimated_casualties != null ? String(analysis.estimated_casualties) : "—";

  return (
    <div className="responder-panel-root">
      <header className="responder-header">
        <div className="responder-title">RESPONDER · SECTOR G-4</div>
        <div className="responder-subline">
          {mapCenter.lat.toFixed(5)}°, {mapCenter.lng.toFixed(5)}° · Maps: connected.
        </div>
      </header>

      <div className="responder-stats-bar">
        <div className="responder-count-chip red">
          <span className="responder-chip-dot" aria-hidden />
          <span>
            {counts.red} RED
          </span>
        </div>
        <div className="responder-count-chip yellow">
          <span className="responder-chip-dot" aria-hidden />
          <span>
            {counts.yellow} YEL
          </span>
        </div>
        <div className="responder-count-chip green">
          <span className="responder-chip-dot" aria-hidden />
          <span>
            {counts.green} OK
          </span>
        </div>
      </div>

      <div className="responder-body-grid">
        <aside className="responder-queue-aside">
          <div className="responder-queue-heading">
            <div className="responder-queue-title">LIVE TRIAGE QUEUE</div>
            <div className="responder-queue-sub">
              Sorted by severity. Green = stable / deprioritised (standard START triage).
            </div>
          </div>
          <div className="responder-list-zone responder-queue-scroll">
            {sortedPackets.map((packet, index) => {
              const u = packet.urgency === "RED" ? "red" : packet.urgency === "YELLOW" ? "yellow" : "green";
              const cls = packet.urgency === "RED" ? "u-red" : packet.urgency === "YELLOW" ? "u-yellow" : "u-green";
              const triageCls =
                packet.urgency === "RED"
                  ? "triage-card triage-card-red"
                  : packet.urgency === "YELLOW"
                    ? "triage-card triage-card-yellow"
                    : "triage-card triage-card-green";
              const lat = packet.lat ?? FALLBACK_CENTER.lat;
              const lng = packet.lng ?? packet.lon ?? FALLBACK_CENTER.lng;
              return (
                <div
                  key={packet.id}
                  data-urgency={packet.urgency}
                  className={`responder-packet-row ${cls} ${triageCls}${selectedId === packet.id ? " is-map-selected" : ""}`}
                  style={{
                    ...(packet.urgency === "YELLOW"
                      ? { background: "#FBBC04", color: "#202124" }
                      : {}),
                    animationDelay: `${Math.min(index, 14) * 35}ms`,
                    WebkitTapHighlightColor: "transparent",
                  }}
                  onClick={() => setSelectedId(packet.id)}
                  role="button"
                  tabIndex={0}
                  onKeyDown={(e) => {
                    if (e.key === "Enter" || e.key === " ") setSelectedId(packet.id);
                  }}
                >
                  <div className={`responder-row-accent ${u}`} aria-hidden />
                  <div className="responder-row-inner responder-row-inner--detail">
                    <div className="responder-priority-line">
                      {priorityHeading(packet)}
                      {packet.isDemo ? (
                        <span className="responder-demo-chip" title="Demo scenario packet">
                          DEMO
                        </span>
                      ) : null}
                    </div>
                    <div className="responder-detail-main">
                      ID {formatResponderPacketId(packet)} · {packet.injury}
                    </div>
                    <div className="responder-detail-gps">
                      GPS {lat.toFixed(4)}°, {lng.toFixed(4)}° · {signalsLine(packet)}
                    </div>
                    <div className="responder-detail-source">Source: {sourceLine(packet)}</div>
                  </div>
                  <span className="responder-chevron">›</span>
                </div>
              );
            })}
          </div>
        </aside>

        <section className="responder-map-section">
          <div className="responder-map-zone responder-map-zone-leaflet">
            <div className="responder-map-shell">
              <div className="responder-map-overlay">
                <div>FIELD MAP</div>
                <div>
                  Center: {mapCenter.lat.toFixed(4)}°, {mapCenter.lng.toFixed(4)}° · {markerCount}{" "}
                  cases with GPS
                </div>
              </div>
              <FieldMap packets={sortedPackets} selectedId={selectedId} setSelectedId={setSelectedId} />
              {counts.red > 0 ? (
                <div className="responder-map-proximity" role="status">
                  <span className="responder-map-proximity-icon" aria-hidden>
                    ⚠
                  </span>
                  Proximity: {counts.red} critical on wire.
                </div>
              ) : null}
            </div>
          </div>
        </section>

        <aside className="responder-side-panel responder-analysis-panel">
          <OnDeviceAnalysisPanel />
          <div className="responder-analysis-head">
            <span className="responder-analysis-title">ON-DEMAND ANALYSIS</span>
            <button type="button" className="responder-rerun-btn" onClick={onAnalyze} disabled={analyzing}>
              Re-run
            </button>
          </div>
          <p className="responder-analysis-sub">
            {demoMode
              ? "Demo · stable dispatch response"
              : dispatchEngine === "backend"
                ? "Live · OmniMesh AI backend (AMD + Gemma)"
                : "Live · Gemini dispatch agent"}
          </p>

          {analysis?.mode_used ? (
            <div className={`om-routing-mode-badge om-routing-mode-badge--${analysis.mode_used} om-routing-mode-badge--panel`}>
              Routing: {analysis.mode_used.toUpperCase()}
            </div>
          ) : null}

          {analysis?.critical_alert ? (
            <div className="responder-analysis-critical">
              <div className="responder-analysis-critical-title">⚠ Critical alert</div>
              <p>{analysis.critical_alert}</p>
            </div>
          ) : null}

          {analysis?.analysis ? (
            <div className="responder-analysis-block">
              <div className="responder-analysis-block-head">
                <span>Dispatch analysis</span>
                <span className="responder-est-badge">EST {estCasualties}</span>
              </div>
              <p className="responder-analysis-body">{analysis.analysis}</p>
            </div>
          ) : (
            <p className="responder-side-hint">Run analysis to generate dispatch recommendations.</p>
          )}

          {analysis?.zone_assignments?.length ? (
            <div className="responder-zone-block">
              <div className="responder-zone-block-title">Zone assignments</div>
              <div className="responder-zone-cards">
                {analysis.zone_assignments.map((z, idx) => (
                  <div key={`${z.zone}-${idx}`} className="responder-zone-card">
                    <div className="responder-zone-card-name">{z.zone}</div>
                    <div className="responder-zone-card-team">{z.team}</div>
                    <div className="responder-zone-card-reason">{z.reason}</div>
                  </div>
                ))}
              </div>
            </div>
          ) : null}
        </aside>
      </div>
    </div>
  );
}
