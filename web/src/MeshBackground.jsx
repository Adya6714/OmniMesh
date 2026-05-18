import { useEffect, useMemo, useRef, useState } from "react";

const MODE_CONFIG = {
  Victim: {
    nodeColor: "#4285F4",
    edgeColor: "#4285F4",
    nodeAlpha: 0.08,
    edgeAlpha: 0.03,
    nodeCount: 20,
    connectionDistance: 220,
    interactionRadius: 160,
  },
  Responder: {
    nodeColor: "#4285F4",
    edgeColor: "#34A853",
    nodeAlpha: 0.1,
    edgeAlpha: 0.04,
    nodeCount: 24,
  },
  Command: {
    nodeColor: "#174EA6",
    edgeColor: "#4285F4",
    nodeAlpha: 0.14,
    edgeAlpha: 0.06,
    nodeCount: 40,
  },
};

/** Align caps with Android MeshBackground heap tiers. */
function getEffectiveNodeCount(requested) {
  const isMobile =
    typeof window !== "undefined" && window.matchMedia && window.matchMedia("(max-width: 768px)").matches;
  let n = Math.min(requested, isMobile ? 30 : 60);
  const heapLimit = performance?.memory?.jsHeapSizeLimit ?? 512 * 1024 * 1024;
  const maxMb = Math.floor(heapLimit / (1024 * 1024));
  if (maxMb < 128) n = Math.min(n, 15);
  else if (maxMb < 256) n = Math.max(8, Math.floor(n * 0.66));
  return Math.max(6, n);
}

function randomPhysicsNode(w, h) {
  let vx = Math.random() * 2 - 1;
  let vy = Math.random() * 2 - 1;
  const len = Math.hypot(vx, vy) || 0.01;
  const mag = Math.random() * 0.45 + 0.35;
  vx = (vx / len) * mag;
  vy = (vy / len) * mag;
  const width = Math.max(w, 1);
  const height = Math.max(h, 1);
  return {
    x: Math.random() * width,
    y: Math.random() * height,
    vx,
    vy,
    baseSpeed: Math.random() * 0.35 + 0.45,
    baseOpacity: Math.random() * 0.55 + 0.35,
    isTouched: false,
  };
}

function hexRgb(hex) {
  return [
    parseInt(hex.slice(1, 3), 16),
    parseInt(hex.slice(3, 5), 16),
    parseInt(hex.slice(5, 7), 16),
  ];
}

export default function MeshBackground({ mode }) {
  const canvasRef = useRef(null);
  const rafRef = useRef(0);
  const nodesRef = useRef([]);
  const touchRef = useRef(null);
  const lastFrameMsRef = useRef(-1);
  const dprRef = useRef(1);
  const cfgRef = useRef(MODE_CONFIG.Command);
  const countRef = useRef(6);

  const [ready, setReady] = useState(false);
  const cfg = MODE_CONFIG[mode] || MODE_CONFIG.Command;
  const effectiveCount = useMemo(() => getEffectiveNodeCount(cfg.nodeCount), [cfg.nodeCount]);

  cfgRef.current = cfg;
  countRef.current = effectiveCount;

  useEffect(() => {
    nodesRef.current = [];
    lastFrameMsRef.current = -1;
  }, [mode, effectiveCount]);

  useEffect(() => {
    const canvas = canvasRef.current;
    if (!canvas) return undefined;

    const ctx = canvas.getContext("2d");

    const resize = () => {
      const { width, height } = canvas.getBoundingClientRect();
      if (width < 8 || height < 8) {
        setReady(false);
        return;
      }
      const dpr = typeof window !== "undefined" ? window.devicePixelRatio || 1 : 1;
      dprRef.current = dpr;
      const wPx = Math.floor(width * dpr);
      const hPx = Math.floor(height * dpr);
      canvas.width = wPx;
      canvas.height = hPx;

      const nodes = nodesRef.current;
      const need = countRef.current;
      if (!nodes.length || nodes.length !== need) {
        nodesRef.current = Array.from({ length: need }, () => randomPhysicsNode(width, height));
      } else {
        nodes.forEach((n) => {
          n.x = Math.min(Math.max(n.x, 6), Math.max(width - 6, 6));
          n.y = Math.min(Math.max(n.y, 6), Math.max(height - 6, 6));
        });
      }
      setReady(true);
    };

    resize();
    const ro = typeof ResizeObserver !== "undefined" ? new ResizeObserver(resize) : null;
    if (ro) ro.observe(canvas);
    window.addEventListener("resize", resize);

    const step = (now) => {
      const c = cfgRef.current;
      const connectionDistance = c.connectionDistance ?? 250;
      const interactionRadius = Math.max(40, c.interactionRadius ?? 300);
      const edgeStroke = c.edgeStrokeWidth ?? 2;
      const pad = 6;

      const cssW = canvas.clientWidth;
      const cssH = canvas.clientHeight;
      const w = cssW;
      const h = cssH;

      if (w <= 0 || h <= 0) {
        rafRef.current = requestAnimationFrame(step);
        return;
      }

      let nodes = nodesRef.current;
      const need = countRef.current;
      if (!nodes.length || nodes.length !== need) {
        nodesRef.current = Array.from({ length: need }, () => randomPhysicsNode(w, h));
        nodes = nodesRef.current;
      }

      const dtMs =
        lastFrameMsRef.current < 0 ? 16 : Math.min(48, Math.max(4, now - lastFrameMsRef.current));
      lastFrameMsRef.current = now;
      const dtNorm = dtMs / 16;

      const touch = touchRef.current;

      for (const node of nodes) {
        if (touch) {
          const dx = touch.x - node.x;
          const dy = touch.y - node.y;
          const dist = Math.hypot(dx, dy);
          if (dist < interactionRadius && dist > 2) {
            node.isTouched = true;
            const falloff = Math.max(0, Math.min(1, 1 - dist / interactionRadius));
            const pull = falloff * 0.11 * dtNorm;
            node.vx += (dx / dist) * pull * interactionRadius * 0.18;
            node.vy += (dy / dist) * pull * interactionRadius * 0.18;
          } else {
            node.isTouched = false;
          }
        } else {
          node.isTouched = false;
        }

        let speed = Math.hypot(node.vx, node.vy);
        const damping = Math.min(0.999, Math.max(0.94, 1 - 0.022 * dtNorm));
        node.vx *= damping;
        node.vy *= damping;

        speed = Math.hypot(node.vx, node.vy);
        if (speed > node.baseSpeed * 1.15) {
          node.vx *= 0.985;
          node.vy *= 0.985;
        } else if (speed < node.baseSpeed * 0.35 && speed > 0.001) {
          const acc = 1 + 0.018 * dtNorm;
          node.vx *= acc;
          node.vy *= acc;
        }

        node.vx += (Math.random() - 0.5) * 0.018 * dtNorm;
        node.vy += (Math.random() - 0.5) * 0.018 * dtNorm;

        node.x += node.vx * dtNorm;
        node.y += node.vy * dtNorm;

        if (node.x < pad) {
          node.x = pad;
          node.vx = Math.abs(node.vx);
        }
        if (node.x > w - pad) {
          node.x = Math.max(w - pad, pad);
          node.vx = -Math.abs(node.vx);
        }
        if (node.y < pad) {
          node.y = pad;
          node.vy = Math.abs(node.vy);
        }
        if (node.y > h - pad) {
          node.y = Math.max(h - pad, pad);
          node.vy = -Math.abs(node.vy);
        }

        const cap = node.baseSpeed * 5;
        const sp = Math.hypot(node.vx, node.vy);
        if (sp > cap && sp > 0) {
          node.vx = (node.vx / sp) * cap;
          node.vy = (node.vy / sp) * cap;
        }
      }

      const dpr = dprRef.current;
      ctx.setTransform(1, 0, 0, 1, 0, 0);
      ctx.clearRect(0, 0, canvas.width, canvas.height);
      ctx.setTransform(dpr, 0, 0, dpr, 0, 0);

      const [er, eg, eb] = hexRgb(c.edgeColor);
      const threshold = Math.max(80, connectionDistance);

      for (let i = 0; i < nodes.length; i += 1) {
        for (let j = i + 1; j < nodes.length; j += 1) {
          const a = nodes[i];
          const b = nodes[j];
          const dist = Math.hypot(b.x - a.x, b.y - a.y);
          if (dist < threshold && dist > 0.5) {
            const fade = Math.max(0, Math.min(1, 1 - dist / threshold));
            const blend = ((a.baseOpacity + b.baseOpacity) / 2) * c.edgeAlpha * fade;
            ctx.strokeStyle = `rgba(${er}, ${eg}, ${eb}, ${blend})`;
            ctx.lineWidth = edgeStroke;
            ctx.beginPath();
            ctx.moveTo(a.x, a.y);
            ctx.lineTo(b.x, b.y);
            ctx.stroke();
          }
        }
      }

      const [nr, ng, nb] = hexRgb(c.nodeColor);
      for (const node of nodes) {
        const nearTouch =
          touch && Math.hypot(touch.x - node.x, touch.y - node.y) < interactionRadius;
        let brightness = 1;
        if (node.isTouched) brightness = 2.45;
        else if (nearTouch) brightness = 1.55;

        const alphaMul = Math.min(1, c.nodeAlpha * node.baseOpacity * brightness);
        const brCap = Math.min(brightness, 2.8);
        const outerR = 7 * brCap;
        const coreR = 3 * Math.min(brightness, 2.6);

        ctx.fillStyle = `rgba(${nr}, ${ng}, ${nb}, ${alphaMul * 0.42})`;
        ctx.beginPath();
        ctx.arc(node.x, node.y, outerR, 0, Math.PI * 2);
        ctx.fill();

        ctx.fillStyle = `rgba(${nr}, ${ng}, ${nb}, ${alphaMul})`;
        ctx.beginPath();
        ctx.arc(node.x, node.y, coreR, 0, Math.PI * 2);
        ctx.fill();
      }

      rafRef.current = requestAnimationFrame(step);
    };

    rafRef.current = requestAnimationFrame(step);

    return () => {
      cancelAnimationFrame(rafRef.current);
      window.removeEventListener("resize", resize);
      if (ro) ro.disconnect();
    };
  }, []);

  return (
    <canvas
      ref={canvasRef}
      className={`mesh-background ${ready ? "ready" : ""}`}
      style={{ touchAction: "none" }}
      onPointerDown={(e) => {
        e.currentTarget.setPointerCapture(e.pointerId);
        const rect = e.currentTarget.getBoundingClientRect();
        touchRef.current = { x: e.clientX - rect.left, y: e.clientY - rect.top };
      }}
      onPointerMove={(e) => {
        if (!touchRef.current) return;
        const rect = e.currentTarget.getBoundingClientRect();
        touchRef.current = { x: e.clientX - rect.left, y: e.clientY - rect.top };
      }}
      onPointerUp={(e) => {
        try {
          e.currentTarget.releasePointerCapture(e.pointerId);
        } catch {
          /* ignore */
        }
        touchRef.current = null;
      }}
      onPointerCancel={() => {
        touchRef.current = null;
      }}
      onLostPointerCapture={() => {
        touchRef.current = null;
      }}
    />
  );
}
