import { useCallback, useState } from "react";
import { clearTestPackets, seedTestPackets } from "./seedTestPackets";
import { db } from "./firebase";

/**
 * Temporary DEV-only UI for seeding / clearing test packets in Firestore.
 * Stripped from production builds at compile time (NODE_ENV).
 */
export default function OmniMeshSeedDevPanel() {
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState("");
  const [collapsed, setCollapsed] = useState(false);

  const run = useCallback(async (fn, label) => {
    if (!db) {
      setMessage("Firestore not configured.");
      return;
    }
    setBusy(true);
    setMessage("");
    try {
      await fn();
      setMessage(`${label} OK`);
    } catch (e) {
      setMessage(e?.message || String(e));
    } finally {
      setBusy(false);
    }
  }, []);

  if (collapsed) {
    return (
      <button
        type="button"
        className="omni-seed-dev-panel__fab"
        onClick={() => setCollapsed(false)}
        aria-expanded={false}
        aria-controls="omni-seed-dev-panel-content"
        title="Show Firestore seed tools"
      >
        DEV seed
      </button>
    );
  }

  return (
    <div
      id="omni-seed-dev-panel-content"
      className="omni-seed-dev-panel"
      role="region"
      aria-label="OmniMesh dev seed tools"
    >
      <div className="omni-seed-dev-panel__head">
        <div className="omni-seed-dev-panel__title">DEV · Firestore stress seed</div>
        <button
          type="button"
          className="omni-seed-dev-panel__close"
          onClick={() => setCollapsed(true)}
          aria-label="Hide seed tools"
          title="Hide"
        >
          ✕
        </button>
      </div>
      <div className="omni-seed-dev-panel__actions">
        <button
          type="button"
          className="omni-seed-dev-panel__btn"
          disabled={busy}
          onClick={() => run(() => seedTestPackets(5), "Seed 5")}
        >
          Seed 5 Packets
        </button>
        <button
          type="button"
          className="omni-seed-dev-panel__btn"
          disabled={busy}
          onClick={() => run(() => seedTestPackets(20), "Seed 20")}
        >
          Seed 20 Packets
        </button>
        <button
          type="button"
          className="omni-seed-dev-panel__btn omni-seed-dev-panel__btn--danger"
          disabled={busy}
          onClick={() => run(() => clearTestPackets(), "Clear")}
        >
          Clear Test Packets
        </button>
      </div>
      {message ? <div className="omni-seed-dev-panel__msg">{message}</div> : null}
    </div>
  );
}
