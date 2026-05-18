import React, { useEffect, useMemo, useState } from "react";
import { collection, doc, onSnapshot, setDoc, updateDoc } from "firebase/firestore";
import { db } from "./firebase";

const COLORS = {
  medBlue: "#4285F4",
  medRed: "#EA4335",
  medGreen: "#34A853",
  grey: "#9AA0A6",
  surface: "#1C2025",
  bg: "#0D1117",
};

function StatCard({ title, value, accent }) {
  return (
    <div className={`deployment-stat-card deployment-stat-card--${accent}`}>
      <div className="deployment-stat-card__accent" aria-hidden />
      <div className="deployment-stat-card__inner">
        <span className="deployment-stat-card__title">{title}</span>
        <span className="deployment-stat-card__value">{value}</span>
      </div>
    </div>
  );
}

export default function DeploymentDashboard() {
  const [deployments, setDeployments] = useState([]);
  const [showCreateForm, setShowCreateForm] = useState(false);
  const [newDeploymentName, setNewDeploymentName] = useState("");
  const [newOrganizerName, setNewOrganizerName] = useState("");

  useEffect(() => {
    if (!db) return undefined;
    const unsub = onSnapshot(collection(db, "deployments"), (snap) => {
      setDeployments(snap.docs.map((d) => ({ id: d.id, ...d.data() })));
    });
    return unsub;
  }, []);

  const { totalDeployments, activeIncidents, activeDevices } = useMemo(() => {
    const list = Array.isArray(deployments) ? deployments : [];
    const totalDeploymentsCount = list.length;
    const activeIncidentsCount = list.filter((d) => d?.isIncidentActive === true).length;
    const activeDevicesCount = list.reduce((sum, d) => {
      const n = Number(d?.activeDevices);
      if (!Number.isNaN(n) && n >= 0) return sum + n;
      const ids = d?.registeredDeviceIds;
      return sum + (Array.isArray(ids) ? ids.length : 0);
    }, 0);

    return {
      totalDeployments: totalDeploymentsCount,
      activeIncidents: activeIncidentsCount,
      activeDevices: activeDevicesCount,
    };
  }, [deployments]);

  async function createDeployment() {
    if (!db || !newDeploymentName || !newOrganizerName) return;
    const joinCode = Math.random().toString(36).substring(2, 8).toUpperCase();
    await setDoc(doc(db, "deployments", joinCode), {
      name: newDeploymentName,
      organizerName: newOrganizerName,
      joinCode,
      isIncidentActive: false,
      createdAt: Date.now(),
      registeredDeviceIds: [],
    });
    setShowCreateForm(false);
    setNewDeploymentName("");
    setNewOrganizerName("");
  }

  async function declareIncident(deploymentId) {
    if (!db) return;
    await updateDoc(doc(db, "deployments", deploymentId), {
      isIncidentActive: true,
      incidentDeclaredAt: Date.now(),
    });
    await setDoc(doc(db, "active_incidents", deploymentId), {
      deploymentId,
      declaredAt: Date.now(),
      status: "ACTIVE",
    });
  }

  async function closeIncident(deploymentId) {
    if (!db) return;
    await updateDoc(doc(db, "deployments", deploymentId), { isIncidentActive: false });
    await updateDoc(doc(db, "active_incidents", deploymentId), {
      status: "CLOSED",
      closedAt: Date.now(),
    });
  }

  if (!db) {
    return (
      <div className="deployment-dashboard-root deployment-dashboard-root--error">
        <p>Firestore is not configured. Add Firebase env vars and rebuild.</p>
      </div>
    );
  }

  return (
    <div className="deployment-dashboard-root" style={{ background: COLORS.bg, color: "#E8EAED" }}>
      <div className="deployment-dashboard-header">
        <h2 className="deployment-dashboard-title">OmniMesh Deployment Dashboard</h2>
        <button type="button" className="deployment-dashboard-btn-primary" onClick={() => setShowCreateForm(true)}>
          + New Deployment
        </button>
      </div>

      <section className="deployment-stats-row" aria-label="Operational metrics">
        <StatCard title="Total Deployments" value={totalDeployments} accent="cyan" />
        <StatCard title="Active Devices" value={activeDevices} accent="blue" />
        <StatCard title="Active Incidents" value={activeIncidents} accent="red" />
      </section>

      {showCreateForm && (
        <div className="deployment-create-panel">
          <input
            value={newDeploymentName}
            onChange={(e) => setNewDeploymentName(e.target.value)}
            placeholder="Deployment name"
          />
          <input
            value={newOrganizerName}
            onChange={(e) => setNewOrganizerName(e.target.value)}
            placeholder="Organizer name"
          />
          <button type="button" onClick={createDeployment}>
            Create
          </button>
        </div>
      )}

      <div className="deployment-list">
        {deployments.map((dep) => (
          <div
            key={dep.id}
            className={`deployment-card ${dep.isIncidentActive ? "deployment-card--incident" : ""}`}
            style={{
              borderColor: dep.isIncidentActive ? COLORS.medRed : "#2c2c2e",
            }}
          >
            <div className="deployment-card__top">
              <div>
                <div style={{ fontWeight: 600 }}>{dep.name}</div>
                <div style={{ color: COLORS.grey, fontSize: 12 }}>{dep.organizerName}</div>
                <div style={{ marginTop: 6, color: COLORS.medBlue, fontFamily: "monospace" }}>Join code: {dep.id}</div>
              </div>
              <div>
                {!dep.isIncidentActive ? (
                  <button type="button" className="deployment-btn-incident" onClick={() => declareIncident(dep.id)}>
                    Declare Incident
                  </button>
                ) : (
                  <button type="button" className="deployment-btn-close" onClick={() => closeIncident(dep.id)}>
                    Close Incident
                  </button>
                )}
              </div>
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}
