import { useEffect, useLayoutEffect, useMemo, useRef, useState } from "react";

function hitTestMesh(cx, cy, nodes, packets) {
  for (let i = packets.length - 1; i >= 0; i -= 1) {
    const p = packets[i];
    const dx = cx - p.x;
    const dy = cy - p.y;
    const d = Math.sqrt(dx * dx + dy * dy);
    const r = p.isCritical ? 11 : 8;
    if (d < r) return { kind: "packet", x: p.x, y: p.y, isCritical: p.isCritical };
  }
  let best = null;
  let bestD = 1e9;
  for (const node of nodes) {
    const dx = cx - node.x;
    const dy = cy - node.y;
    const d = Math.sqrt(dx * dx + dy * dy);
    const maxR = node.type === "critical" ? 18 : node.type === "relay" ? 14 : 11;
    if (d < maxR && d < bestD) {
      bestD = d;
      best = node;
    }
  }
  if (best) return { kind: "node", node: best };
  return null;
}

function buildMeshTooltip(hit, { activePeers, criticalCount }) {
  const apStr = Number(activePeers).toLocaleString();
  if (hit.kind === "packet") {
    const red = hit.isCritical;
    return {
      variant: red ? "critical" : "relay",
      title: red ? "Live packet · Critical lane" : "Live packet · Standard relay",
      lines: [
        "State · In flight (encrypted)",
        red ? "Priority · RED QoS · Head-of-line bypass" : "Priority · Mesh forward · ECN-aware",
        `Visible peers · ${apStr}`,
        "Cipher suite · ChaCha20-Poly1305",
      ],
      nx: hit.x,
      ny: hit.y,
    };
  }
  const node = hit.node;
  const seed = Math.floor(node.x * 7 + node.y * 13) % 100000;
  const meshId = `NODE-${String(seed).padStart(5, "0")}`;
  if (node.type === "critical") {
    return {
      variant: "critical",
      title: "Critical priority node",
      lines: [
        `Endpoint · ${meshId}`,
        `RED casualty lanes · ${criticalCount}`,
        `Mesh peers · ${apStr}`,
        "Policy · Preemptive QoS · TTL pinned",
      ],
      nx: node.x,
      ny: node.y,
    };
  }
  if (node.type === "relay") {
    const lo = 38 + criticalCount * 2;
    const hi = 52 + criticalCount * 4;
    return {
      variant: "relay",
      title: "Relay bridge",
      lines: [`Hop · ${meshId}`, `Latency est · ${lo}–${hi} ms`, "Backhaul · Multi-path mesh", `Peers · ${apStr}`],
      nx: node.x,
      ny: node.y,
    };
  }
  return {
    variant: "healthy",
    title: "Healthy endpoint",
    lines: [
      `Leaf · ${meshId}`,
      "Sync · OK · Beacon nominal",
      `Sector duty · ${Math.min(99, 12 + (Number(activePeers) % 40))}%`,
      `Mesh peers · ${apStr}`,
    ],
    nx: node.x,
    ny: node.y,
  };
}

function ZoneMeshCanvas({ criticalCount, activePeers }) {
  const canvasRef = useRef(null);
  const rafRef = useRef(null);
  const propsRef = useRef({ criticalCount, activePeers });
  propsRef.current = { criticalCount, activePeers };
  const nodesRef = useRef([]);
  const packetsRef = useRef([]);
  const dimsRef = useRef({ W: 1, H: 1 });
  const [tooltip, setTooltip] = useState(null);

  useEffect(() => {
    const canvas = canvasRef.current;
    const panel = canvas?.closest(".zone-mesh-panel");
    if (!canvas || !panel) return undefined;

    const ctx = canvas.getContext("2d");
    let nodes = [];
    let packets = [];
    let W = 0;
    let H = 0;
    let frame = 0;
    const ripples = [];

    const initGraph = () => {
      const nodeCount = Math.min(120, Math.max(28, Math.floor((W * H) / 4000)));
      nodes = Array.from({ length: nodeCount }, (_, i) => ({
        x: Math.random() * W,
        y: Math.random() * H,
        type:
          i < Math.max(4, Math.floor(nodeCount * 0.035))
            ? "critical"
            : i < Math.max(12, Math.floor(nodeCount * 0.09))
              ? "relay"
              : "healthy",
        opacity: 0.3 + Math.random() * 0.5,
      }));
      packets = Array.from({ length: 12 }, () => {
        const src = nodes[Math.floor(Math.random() * nodes.length)] || { x: W / 2, y: H / 2 };
        let dst = nodes[Math.floor(Math.random() * nodes.length)] || src;
        if (dst === src && nodes.length > 1) dst = nodes[(nodes.indexOf(dst) + 1) % nodes.length];
        return {
          x: src.x,
          y: src.y,
          srcX: src.x,
          srcY: src.y,
          dstX: dst.x,
          dstY: dst.y,
          progress: Math.random(),
          isCritical: Math.random() < 0.35,
        };
      });
      nodesRef.current = nodes;
      setTooltip(null);
    };

    const resize = () => {
      const rect = panel.getBoundingClientRect();
      W = Math.max(200, Math.floor(rect.width));
      H = Math.max(200, Math.floor(rect.height));
      canvas.width = W;
      canvas.height = H;
      dimsRef.current = { W, H };
      initGraph();
    };

    const onCanvasClick = (e) => {
      const rect = canvas.getBoundingClientRect();
      const scaleX = canvas.width / rect.width;
      const scaleY = canvas.height / rect.height;
      const cx = (e.clientX - rect.left) * scaleX;
      const cy = (e.clientY - rect.top) * scaleY;
      const hit = hitTestMesh(cx, cy, nodesRef.current, packetsRef.current);
      if (!hit) {
        setTooltip(null);
        return;
      }
      setTooltip(buildMeshTooltip(hit, propsRef.current));
    };

    canvas.addEventListener("click", onCanvasClick);

    const ro = new ResizeObserver(resize);
    ro.observe(panel);
    resize();

    const draw = () => {
      ctx.clearRect(0, 0, W, H);

      const rg = ctx.createRadialGradient(W * 0.48, H * 0.42, 0, W * 0.48, H * 0.42, Math.max(W, H) * 0.92);
      rg.addColorStop(0, "rgba(23,35,52,0.98)");
      rg.addColorStop(0.55, "rgba(12,18,28,0.99)");
      rg.addColorStop(1, "rgba(4,6,10,1)");
      ctx.fillStyle = rg;
      ctx.fillRect(0, 0, W, H);

      const rg2 = ctx.createRadialGradient(W * 0.5, H * 0.55, 0, W * 0.5, H * 0.55, Math.max(W, H) * 0.62);
      rg2.addColorStop(0, "rgba(66,133,244,0.09)");
      rg2.addColorStop(1, "rgba(66,133,244,0)");
      ctx.fillStyle = rg2;
      ctx.fillRect(0, 0, W, H);

      for (let i = 0; i < nodes.length; i += 1) {
        for (let j = i + 1; j < nodes.length; j += 1) {
          const a = nodes[i];
          const b = nodes[j];
          const dx = b.x - a.x;
          const dy = b.y - a.y;
          const d = Math.sqrt(dx * dx + dy * dy);
          if (d < 160) {
            const alpha = (1 - d / 160) * 0.14;
            ctx.beginPath();
            ctx.moveTo(a.x, a.y);
            ctx.lineTo(b.x, b.y);
            ctx.strokeStyle = `rgba(96,153,255,${alpha})`;
            ctx.lineWidth = 0.85;
            ctx.stroke();
          }
        }
      }

      const sweepX = ((frame % 180) / 180) * W;
      const sweepGrad = ctx.createLinearGradient(sweepX - 28, 0, sweepX + 28, 0);
      sweepGrad.addColorStop(0, "rgba(66,133,244,0)");
      sweepGrad.addColorStop(0.45, "rgba(52,168,83,0.42)");
      sweepGrad.addColorStop(0.55, "rgba(138,180,248,0.22)");
      sweepGrad.addColorStop(1, "rgba(66,133,244,0)");
      ctx.fillStyle = sweepGrad;
      ctx.fillRect(sweepX - 28, 0, 56, H);

      const critPulse = (Math.sin(frame * 0.09) + 1) * 0.5;

      nodes.forEach((node) => {
        const nearSweep = Math.abs(node.x - sweepX) < 32;
        const brightness = nearSweep ? 2.35 : 1;
        let radius;
        let glowAlpha;
        let coreAlpha;
        if (node.type === "critical") {
          radius = 4;
          glowAlpha = (0.18 + critPulse * 0.12) * brightness;
          coreAlpha = 0.82 * brightness;
          ctx.beginPath();
          ctx.arc(node.x, node.y, radius * (3.2 + critPulse * 0.8), 0, Math.PI * 2);
          ctx.fillStyle = `rgba(234,67,53,${glowAlpha})`;
          ctx.fill();
          ctx.beginPath();
          ctx.arc(node.x, node.y, radius * 1.65, 0, Math.PI * 2);
          ctx.fillStyle = `rgba(255,82,82,${0.32 * brightness})`;
          ctx.fill();
          ctx.beginPath();
          ctx.arc(node.x, node.y, radius, 0, Math.PI * 2);
          ctx.fillStyle = `rgba(255,235,238,${coreAlpha})`;
          ctx.fill();
        } else if (node.type === "relay") {
          radius = 3;
          glowAlpha = 0.2 * brightness;
          coreAlpha = 0.62 * brightness;
          ctx.beginPath();
          ctx.arc(node.x, node.y, radius * 2.6, 0, Math.PI * 2);
          ctx.fillStyle = `rgba(52,168,83,${glowAlpha})`;
          ctx.fill();
          ctx.beginPath();
          ctx.arc(node.x, node.y, radius * 1.35, 0, Math.PI * 2);
          ctx.fillStyle = `rgba(66,133,244,${0.22 * brightness})`;
          ctx.fill();
          ctx.beginPath();
          ctx.arc(node.x, node.y, radius * 0.85, 0, Math.PI * 2);
          ctx.fillStyle = `rgba(232,240,254,${coreAlpha})`;
          ctx.fill();
        } else {
          radius = 2.2;
          glowAlpha = 0.16 * brightness;
          coreAlpha = 0.52 * brightness;
          ctx.beginPath();
          ctx.arc(node.x, node.y, radius * 3.2, 0, Math.PI * 2);
          ctx.fillStyle = `rgba(66,133,244,${glowAlpha})`;
          ctx.fill();
          ctx.beginPath();
          ctx.arc(node.x, node.y, radius * 1.5, 0, Math.PI * 2);
          ctx.fillStyle = `rgba(138,180,248,${0.42 * brightness})`;
          ctx.fill();
          ctx.beginPath();
          ctx.arc(node.x, node.y, radius, 0, Math.PI * 2);
          ctx.fillStyle = `rgba(66,133,244,${coreAlpha})`;
          ctx.fill();
        }
      });

      packets.forEach((pkt) => {
        pkt.progress += 0.006;
        if (pkt.progress >= 1) {
          pkt.progress = 0;
          pkt.srcX = pkt.dstX;
          pkt.srcY = pkt.dstY;
          const newDst = nodes[Math.floor(Math.random() * nodes.length)];
          pkt.dstX = newDst.x;
          pkt.dstY = newDst.y;
          ripples.push({ x: pkt.srcX, y: pkt.srcY, r: 0, alpha: 0.55, critical: pkt.isCritical });
        }
        pkt.x = pkt.srcX + (pkt.dstX - pkt.srcX) * pkt.progress;
        pkt.y = pkt.srcY + (pkt.dstY - pkt.srcY) * pkt.progress;
        const base = pkt.isCritical ? "234,67,53" : "66,133,244";
        ctx.beginPath();
        ctx.arc(pkt.x, pkt.y, pkt.isCritical ? 5 : 3.5, 0, Math.PI * 2);
        ctx.fillStyle = `rgba(${base},0.22)`;
        ctx.fill();
        ctx.beginPath();
        ctx.arc(pkt.x, pkt.y, pkt.isCritical ? 3 : 2, 0, Math.PI * 2);
        ctx.fillStyle = `rgba(${base},0.92)`;
        ctx.fill();
      });

      nodesRef.current = nodes;
      packetsRef.current = packets.map((p) => ({
        x: p.x,
        y: p.y,
        isCritical: p.isCritical,
      }));

      for (let i = ripples.length - 1; i >= 0; i -= 1) {
        const r = ripples[i];
        r.r += 2;
        r.alpha *= 0.88;
        if (r.alpha < 0.02) {
          ripples.splice(i, 1);
          continue;
        }
        const rgb = r.critical ? "234,67,53" : "66,133,244";
        ctx.beginPath();
        ctx.arc(r.x, r.y, r.r, 0, Math.PI * 2);
        ctx.strokeStyle = `rgba(${rgb},${r.alpha})`;
        ctx.lineWidth = 1;
        ctx.stroke();
      }

      frame += 1;
      rafRef.current = requestAnimationFrame(draw);
    };

    rafRef.current = requestAnimationFrame(draw);

    return () => {
      canvas.removeEventListener("click", onCanvasClick);
      ro.disconnect();
      if (rafRef.current) cancelAnimationFrame(rafRef.current);
    };
  }, []);

  const { W: tw, H: th } = dimsRef.current;
  const tipLeftPct = tooltip && th > 0 ? (tooltip.nx / tw) * 100 : 0;
  const tipTopPct = tooltip && th > 0 ? (tooltip.ny / th) * 100 : 0;

  return (
    <div className="zone-mesh-panel">
      <div className="zone-mesh-header">
        <span className="zone-mesh-title">ZONE 4 — MESH OVERVIEW</span>
        <span className="zone-mesh-sub">Live packet flow · self-healing routing</span>
      </div>
      <div className="zone-mesh-canvas-wrap">
        <canvas ref={canvasRef} className="zone-mesh-canvas" style={{ cursor: "crosshair" }} />
        {tooltip ? (
          <div
            className={`zone-mesh-tooltip zone-mesh-tooltip--${tooltip.variant}`}
            style={{ left: `${tipLeftPct}%`, top: `${tipTopPct}%` }}
            role="dialog"
            aria-label="Node details"
          >
            <button
              type="button"
              className="zone-mesh-tooltip-close"
              aria-label="Close"
              onClick={() => setTooltip(null)}
            >
              ×
            </button>
            <div className="zone-mesh-tooltip-title">{tooltip.title}</div>
            <ul className="zone-mesh-tooltip-lines">
              {tooltip.lines.map((line, idx) => (
                <li key={`${idx}-${line.slice(0, 24)}`}>{line}</li>
              ))}
            </ul>
          </div>
        ) : null}
      </div>
      <div className="zone-mesh-legend">
        <span className="zone-mesh-legend-title">DATA TRAFFIC</span>
        <div className="legend-row">
          <span className="legend-dot" style={{ background: "#EA4335" }} />
          Critical priority
        </div>
        <div className="legend-row">
          <span className="legend-dot" style={{ background: "#4285F4" }} />
          Standard relay
        </div>
        <div className="legend-row">
          <span className="legend-dot" style={{ background: "#34A853" }} />
          Healthy node
        </div>
        <div className="legend-peers">
          Active peers: {activePeers} · Critical: {criticalCount}
        </div>
      </div>
    </div>
  );
}

function easeOutCubic(t) {
  return 1 - Math.pow(1 - t, 3);
}

/** Count-up via requestAnimationFrame over 800ms (ease-out cubic). First tick animates from 0 → target. */
function CountUpStat({ value, className }) {
  const [display, setDisplay] = useState(0);
  const settledRef = useRef(0);
  const frameRef = useRef(null);

  useEffect(() => {
    const end = Math.round(Number(value)) || 0;
    const start = settledRef.current;
    if (frameRef.current) cancelAnimationFrame(frameRef.current);
    if (start === end) {
      setDisplay(end);
      return undefined;
    }
    const t0 = performance.now();
    const dur = 800;
    const tick = (now) => {
      const t = Math.min(1, (now - t0) / dur);
      const v = Math.round(start + (end - start) * easeOutCubic(t));
      setDisplay(v);
      if (t < 1) frameRef.current = requestAnimationFrame(tick);
      else {
        settledRef.current = end;
        setDisplay(end);
      }
    };
    frameRef.current = requestAnimationFrame(tick);
    return () => {
      if (frameRef.current) cancelAnimationFrame(frameRef.current);
    };
  }, [value]);

  return <div className={className}>{display}</div>;
}

function formatSessionClock(ms) {
  const s = Math.floor(ms / 1000);
  const h = Math.floor(s / 3600);
  const m = Math.floor((s % 3600) / 60);
  const sec = s % 60;
  const pad = (n) => String(n).padStart(2, "0");
  return `${pad(h)}:${pad(m)}:${pad(sec)}`;
}

function jitter(base, pct = 5) {
  const delta = (Math.random() * 2 - 1) * (pct / 100);
  return base * (1 + delta);
}

export default function CommandPanel({
  packets = [],
  peerCountEstimate,
  demoMode = true,
  analysis,
  analyzing,
  onAnalyze,
}) {
  const now = new Date();
  const localStamp = now.toLocaleString("en-US", {
    weekday: "short",
    month: "short",
    day: "numeric",
    hour: "numeric",
    minute: "numeric",
    second: "numeric",
    hour12: true,
  });
  const zone = now
    .toLocaleTimeString("en-IN", { timeZoneName: "shortOffset" })
    .split(" ")
    .slice(-1)[0]
    .replace("GMT", "IST ");
  const stats = useMemo(() => {
    const list = Array.isArray(packets) ? packets : [];
    const total = list.length;
    const redCount = list.filter((packet) => packet.urgency === "RED").length;
    const yellowCount = list.filter((packet) => packet.urgency === "YELLOW").length;
    const greenCount = list.filter((packet) => packet.urgency === "GREEN").length;
    const autoCount = list.filter((packet) => packet.isAutoGenerated).length;
    return { total, redCount, yellowCount, greenCount, autoCount };
  }, [packets]);

  const urgencyPercents = useMemo(() => {
    const { total, redCount, yellowCount, greenCount } = stats;
    if (!total) return { red: 0, yellow: 0, green: 0 };
    return {
      red: (redCount / total) * 100,
      yellow: (yellowCount / total) * 100,
      green: (greenCount / total) * 100,
    };
  }, [stats]);

  const pctLabel = (num, den) => (den ? Math.round((num / den) * 100) : 0);

  const [timelineEvents, setTimelineEvents] = useState([]);
  const seenRedIdsRef = useRef(new Set());
  const seenAutoIdsRef = useRef(new Set());
  const analysisTimelineSigRef = useRef(null);

  useEffect(() => {
    const list = Array.isArray(packets) ? packets : [];
    const hhmm = () =>
      new Date().toLocaleTimeString([], { hour: "2-digit", minute: "2-digit", hour12: false });

    setTimelineEvents((prev) => {
      const additions = [];
      for (const p of list) {
        if (p.urgency === "RED" && p.id != null && !seenRedIdsRef.current.has(p.id)) {
          seenRedIdsRef.current.add(p.id);
          additions.push({
            id: `tl-red-${p.id}`,
            time: hhmm(),
            severity: "red",
            text: `RED packet detected — ${p.injury || "Unknown"}`,
          });
        }
        if (p.isAutoGenerated && p.id != null && !seenAutoIdsRef.current.has(p.id)) {
          seenAutoIdsRef.current.add(p.id);
          additions.push({
            id: `tl-auto-${p.id}`,
            time: hhmm(),
            severity: "amber",
            text: "Auto-SOS triggered — passive detection",
          });
        }
      }
      if (!additions.length) return prev;
      const merged = [...additions.reverse(), ...prev];
      return merged.slice(0, 20);
    });
  }, [packets]);

  useEffect(() => {
    if (!analysis) {
      analysisTimelineSigRef.current = null;
      return;
    }
    const sig = `${analysis.estimated_casualties ?? ""}|${(analysis.analysis || "").slice(0, 120)}`;
    if (analysisTimelineSigRef.current === sig) return;
    analysisTimelineSigRef.current = sig;
    const hhmm = () =>
      new Date().toLocaleTimeString([], { hour: "2-digit", minute: "2-digit", hour12: false });
    setTimelineEvents((prev) =>
      [
        {
          id: `tl-gemini-${Date.now()}`,
          time: hhmm(),
          severity: "blue",
          text: "Gemini dispatch analysis updated",
        },
        ...prev,
      ].slice(0, 20),
    );
  }, [analysis]);

  const sessionStartRef = useRef(Date.now());
  const [sessionTime, setSessionTime] = useState(() => formatSessionClock(0));
  const [liveMeshMetrics, setLiveMeshMetrics] = useState({
    latencyMs: 42,
    hopAvg: 3.2,
    lossPct: 0.8,
  });

  useEffect(() => {
    const id = setInterval(() => setSessionTime(formatSessionClock(Date.now() - sessionStartRef.current)), 1000);
    return () => clearInterval(id);
  }, []);

  useEffect(() => {
    const tick = () => {
      setLiveMeshMetrics({
        latencyMs: Math.max(12, Math.round(jitter(42, 5))),
        hopAvg: Number(jitter(3.2, 5).toFixed(1)),
        lossPct: Number(Math.min(99, Math.max(0, jitter(0.8, 5))).toFixed(2)),
      });
    };
    tick();
    const id = setInterval(tick, 3000);
    return () => clearInterval(id);
  }, []);
  const meshPeers = peerCountEstimate ?? Math.max(packets.length, 0);
  const routingPct = useMemo(() => {
    if (demoMode) return 94;
    return Math.min(98, 82 + Math.min(packets.length * 2, 14));
  }, [demoMode, packets.length]);

  const analysisBody = analysis?.analysis || "";
  const [bootLines, setBootLines] = useState([]);
  const [displayedAnalysis, setDisplayedAnalysis] = useState("");
  const [genTs, setGenTs] = useState("");

  useEffect(() => {
    if (!analyzing) {
      setBootLines([]);
    }
  }, [analyzing]);

  useLayoutEffect(() => {
    if (!analyzing) return;
    setBootLines(["> INITIALIZING GEMINI 1.5 FLASH..."]);
  }, [analyzing]);

  useEffect(() => {
    if (!analyzing) return undefined;
    const t1 = setTimeout(() => {
      setBootLines((prev) => [...prev, `> LOADING ${packets.length} CASUALTY PACKETS...`]);
    }, 600);
    const t2 = setTimeout(() => {
      setBootLines((prev) => [...prev, "> RUNNING SPATIAL CLUSTER ANALYSIS..."]);
    }, 1200);
    return () => {
      clearTimeout(t1);
      clearTimeout(t2);
    };
  }, [analyzing, packets.length]);

  useEffect(() => {
    if (analyzing || !analysisBody) {
      if (!analysisBody) setDisplayedAnalysis("");
      return undefined;
    }
    let i = 0;
    setDisplayedAnalysis("");
    const id = setInterval(() => {
      i += 1;
      setDisplayedAnalysis(analysisBody.slice(0, i));
      if (i >= analysisBody.length) clearInterval(id);
    }, 15);
    return () => clearInterval(id);
  }, [analysisBody, analyzing]);

  useEffect(() => {
    if (analysis && !analyzing) {
      setGenTs(new Date().toLocaleTimeString());
    }
  }, [analysis, analyzing]);

  const showPostAnalysis = Boolean(analysis) && !analyzing;

  return (
    <div className="command-panel-root">
      <div className="command-panel-inner">
        <div className="command-header command-header-rich">
          <div className="command-header-main">
            <h1 className="command-page-title">COMMAND CENTER</h1>
          </div>
          <div className="command-time-stack">
            <span className="command-local-label">LOCAL TIME</span>
            <span className="command-local-time">{localStamp}</span>
            <span className="command-timezone-meta">{zone}</span>
          </div>
        </div>

        <div className="command-kpi-row" aria-label="Triage totals">
          <div className="command-kpi-card command-kpi-card--total">
            <span className="command-kpi-label">TOTAL</span>
            <CountUpStat value={stats.total} className="command-kpi-value c-neutral" />
          </div>
          <div
            className={`command-kpi-card command-kpi-card--critical ${stats.redCount > 0 ? "command-kpi-card--pulse" : ""}`}
          >
            <span className="command-kpi-label">CRITICAL</span>
            <CountUpStat value={stats.redCount} className="command-kpi-value c-red" />
          </div>
          <div className="command-kpi-card command-kpi-card--auto">
            <span className="command-kpi-label">AUTO-SOS</span>
            <CountUpStat value={stats.autoCount} className="command-kpi-value c-amber" />
          </div>
        </div>

        <div className="command-urgency-distribution" aria-label="Urgency distribution">
          <div className="command-urgency-distribution-label">URGENCY DISTRIBUTION</div>
          <div className="command-urgency-track">
            {stats.total === 0 ? (
              <div className="command-urgency-empty">No packets in queue</div>
            ) : (
              <>
                {stats.redCount > 0 ? (
                  <div
                    className="command-urgency-seg command-urgency-seg--red"
                    style={{ flexGrow: stats.redCount, flexBasis: 0 }}
                    title={`RED ${urgencyPercents.red.toFixed(1)}%`}
                  >
                    {urgencyPercents.red >= 14 ? <span>{pctLabel(stats.redCount, stats.total)}%</span> : null}
                  </div>
                ) : null}
                {stats.yellowCount > 0 ? (
                  <div
                    className="command-urgency-seg command-urgency-seg--yellow"
                    style={{ flexGrow: stats.yellowCount, flexBasis: 0 }}
                    title={`YELLOW ${urgencyPercents.yellow.toFixed(1)}%`}
                  >
                    {urgencyPercents.yellow >= 14 ? (
                      <span>{pctLabel(stats.yellowCount, stats.total)}%</span>
                    ) : null}
                  </div>
                ) : null}
                {stats.greenCount > 0 ? (
                  <div
                    className="command-urgency-seg command-urgency-seg--green"
                    style={{ flexGrow: stats.greenCount, flexBasis: 0 }}
                    title={`GREEN ${urgencyPercents.green.toFixed(1)}%`}
                  >
                    {urgencyPercents.green >= 14 ? (
                      <span>{pctLabel(stats.greenCount, stats.total)}%</span>
                    ) : null}
                  </div>
                ) : null}
              </>
            )}
          </div>
        </div>

        <div className="command-upper-row command-upper-row--mesh-first">
          <div className="command-mesh-col command-mesh-col--with-metrics">
            <ZoneMeshCanvas criticalCount={stats.redCount} activePeers={meshPeers} />
            <div className="command-mesh-metric-bar" aria-label="Live mesh telemetry">
              <span className="command-mesh-chip">
                MESH LATENCY: <strong className="command-mesh-chip-val">~{liveMeshMetrics.latencyMs}ms</strong>
              </span>
              <span className="command-mesh-chip-sep" aria-hidden>
                ·
              </span>
              <span className="command-mesh-chip">
                HOP AVG: <strong className="command-mesh-chip-val">{liveMeshMetrics.hopAvg}</strong>
              </span>
              <span className="command-mesh-chip-sep" aria-hidden>
                ·
              </span>
              <span className="command-mesh-chip">
                PACKET LOSS: <strong className="command-mesh-chip-val">{liveMeshMetrics.lossPct}%</strong>
              </span>
              <span className="command-mesh-chip-sep" aria-hidden>
                ·
              </span>
              <span className="command-mesh-chip">
                UPTIME: <strong className="command-mesh-chip-val">{sessionTime}</strong>
              </span>
            </div>
          </div>
          <div className="command-stats-col command-stats-col--sidebar">
            <div className="command-stat-card corner-neutral command-resilience-card">
              <span className="command-stat-label">NETWORK RESILIENCE</span>
              <CountUpStat value={meshPeers} className="command-stat-number c-blue" />
              <div className="command-stat-sub">Active peer connections</div>
              <div className="command-stat-sub muted">Hop coverage 4.6 avg</div>
              <div className="command-stat-sub muted">Failover routes 3.1 avg</div>
            </div>
            <div className="command-stat-card corner-yellow command-efficiency-card">
              <span className="command-stat-label">PACKET ROUTING EFFICIENCY</span>
              <div className="command-stat-number c-green">{routingPct}%</div>
              <div className="command-efficiency-track">
                <div className="command-efficiency-fill" style={{ width: `${routingPct}%` }} />
              </div>
              <div className="command-stat-sub">Average across all relays</div>
            </div>
            <div
              className={`command-stat-card corner-red stat-red-glow command-priority-card ${stats.redCount > 0 ? "has-critical command-priority-card--glow" : ""}`}
            >
              <span className="command-stat-label">CRITICAL DATA PRIORITIZATION</span>
              <div className={`command-priority-status ${stats.redCount > 0 ? "is-active" : ""}`}>
                {stats.redCount > 0 ? "Active" : "Standby"}
              </div>
            </div>
          </div>
        </div>

        {stats.autoCount >= 3 ? (
          <div className="collapse-protocol-banner">
            <span className="collapse-banner-diamond" aria-hidden />
            <div className="collapse-protocol-track">
              <span className="collapse-protocol-text">
                STRUCTURAL COLLAPSE PROTOCOL — {stats.autoCount} AUTO-SOS SIGNALS DETECTED — AWAIT ENGINEERING
                CLEARANCE BEFORE GROUND ENTRY
              </span>
            </div>
          </div>
        ) : null}

        <div className="gemini-terminal">
          <div className="gemini-titlebar">
            <div className="gemini-traffic" aria-hidden>
              <span />
              <span />
              <span />
            </div>
            <span className="gemini-title-text">GEMINI DISPATCH AGENT</span>
            <div className="gemini-titlebar-spacer" />
            <button type="button" className="gemini-analyze-btn" onClick={onAnalyze} disabled={analyzing}>
              $ analyze --zone
            </button>
          </div>
          <div className="gemini-body">
            {analyzing ? (
              bootLines.map((line, idx) => (
                <p key={`${line}-${idx}`} className="gemini-line">
                  {line}
                  {idx === bootLines.length - 1 ? (
                    <span className="gemini-cursor">_</span>
                  ) : null}
                </p>
              ))
            ) : analysisBody ? (
              <p className="gemini-line">
                {displayedAnalysis}
                <span className="gemini-cursor">_</span>
              </p>
            ) : (
              <p className="gemini-line">
                {"> AWAITING ZONE DATA"}
                <span className="gemini-cursor">_</span>
              </p>
            )}

            {showPostAnalysis && analysis?.critical_alert ? (
              <div className="gemini-critical">
                ⚑ {analysis.critical_alert}
              </div>
            ) : null}

            {showPostAnalysis
              ? (analysis?.zone_assignments || []).map((zone, idx) => (
                  <div key={`${zone.zone}-${idx}`} className="gemini-zone-row">
                    <div className="gemini-zone-name">{zone.zone}</div>
                    <div className="gemini-zone-team">Resources: {zone.team}</div>
                    <div className="gemini-zone-reason">{zone.reason}</div>
                  </div>
                ))
              : null}

            {showPostAnalysis ? (
              <>
                <div className="gemini-meta-line">{`> EST_CASUALTIES: ${analysis?.estimated_casualties ?? "N/A"}`}</div>
                <div className="gemini-ts">{`> GENERATED: ${genTs}`}</div>
              </>
            ) : null}
          </div>
        </div>

        <div className="cmd-table-wrap">
          <div className="cmd-table-head">
            <span>#</span>
            <span>Urgency</span>
            <span>Injury</span>
            <span>Conf</span>
            <span>Type</span>
          </div>
          {packets.map((packet, index) => {
            const u = packet.urgency === "RED" ? "red" : packet.urgency === "YELLOW" ? "yellow" : "green";
            const abbrev =
              packet.urgency === "RED" ? "RED" : packet.urgency === "YELLOW" ? "YEL" : "GRN";
            return (
              <div
                key={packet.id}
                className={`cmd-table-row ${packet.urgency === "RED" ? "row-red" : ""}`}
                style={{
                  animation: `slideInRow 200ms ease-out forwards`,
                  animationDelay: `${index * 40}ms`,
                  opacity: 0,
                }}
              >
                <span className="cmd-priority">{index + 1}</span>
                <span className={`cmd-badge ${u}`}>{abbrev}</span>
                <span className="cmd-injury">
                  {packet.injury}
                  {packet.isDemo ? (
                    <span className="cmd-demo-chip" title="Demo scenario packet">
                      DEMO
                    </span>
                  ) : null}
                </span>
                <span className="cmd-conf">
                  {packet.confidence != null ? `${Math.round(Number(packet.confidence) * 100)}%` : "—"}
                </span>
                <span className={packet.isAutoGenerated ? "cmd-type-auto" : "cmd-type-manual"}>
                  {packet.isAutoGenerated ? "AUTO" : "MANUAL"}
                </span>
              </div>
            );
          })}
        </div>

        <section className="command-incident-timeline" aria-label="Live incident timeline">
          <div className="command-incident-timeline-head">LIVE INCIDENT TIMELINE</div>
          {timelineEvents.length === 0 ? (
            <p className="command-incident-timeline-empty">No timeline events yet.</p>
          ) : (
            <ul className="command-incident-timeline-list">
              {timelineEvents.map((evt) => (
                <li key={evt.id} className={`command-incident-timeline-item command-incident-timeline-item--${evt.severity}`}>
                  <span className="command-incident-dot" aria-hidden />
                  <span className="command-incident-time">[{evt.time}]</span>
                  <span className="command-incident-text">{evt.text}</span>
                </li>
              ))}
            </ul>
          )}
        </section>
      </div>
    </div>
  );
}
