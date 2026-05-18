import { useState } from "react";

import AcousticVisualizer from "./AcousticVisualizer";

/** Collapsible “collapse verification” strip — parity with Android responder panel. */
export default function OnDeviceAnalysisPanel() {
  const [expanded, setExpanded] = useState(true);

  return (
    <div className="ondevice-analysis-card">
      <button type="button" className="ondevice-analysis-head" onClick={() => setExpanded((v) => !v)}>
        <span className="ondevice-analysis-head-left">
          <span className="ondevice-analysis-dot" aria-hidden />
          ON-DEVICE AI ANALYSIS · COLLAPSE VERIFICATION
        </span>
        <span className="ondevice-analysis-toggle">{expanded ? "▲ HIDE" : "▼ SHOW"}</span>
      </button>
      <p className="ondevice-analysis-sub">LSTM audio + motion fusion + vision edge narrative</p>
      {expanded ? (
        <div className="ondevice-analysis-expanded">
          <div className="ondevice-acoustic-row">
            <AcousticVisualizer />
            <div className="ondevice-acoustic-copy">
              <div className="ondevice-acoustic-title">Acoustic Signature</div>
              <div className="ondevice-acoustic-alert">Low-frequency stress detected</div>
              <div className="ondevice-acoustic-caption">Impact + scream signature model</div>
            </div>
          </div>
          <div className="ondevice-structural-void">
            <span className="ondevice-structural-label">STRUCTURAL VOID GEOMETRY MATCH</span>
          </div>
        </div>
      ) : null}
    </div>
  );
}
