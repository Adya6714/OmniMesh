import pytest
from fastapi.testclient import TestClient

from app.agents.others import allocate_resources, plan_route
from app.agents.triage import _local_triage
from app.main import app
from app.orchestrator import core as orch
from app.orchestrator.core import decide_mode
from app.schemas.models import ExecutionMode, Severity, TriagePacket

client = TestClient(app)


def test_health():
    r = client.get("/health")
    assert r.status_code == 200 and r.json()["status"] == "ok"


def test_local_triage_red():
    p = TriagePacket(text_report="victim unconscious under rubble", vitals={"spo2": 82})
    sev, conf, _ = _local_triage(p)
    assert sev == Severity.RED and conf >= 0.7


def test_local_triage_green():
    p = TriagePacket(text_report="minor scrape, walking fine", vitals={"hr": 80, "spo2": 98})
    assert _local_triage(p)[0] == Severity.GREEN


def test_mode_offline_forces_local():
    orch.STATE["connectivity"] = False
    p = TriagePacket(text_report="x", severity_hint=Severity.RED)
    assert decide_mode(p) == ExecutionMode.LOCAL
    orch.STATE["connectivity"] = True


def test_mode_node_offline_forces_local():
    p = TriagePacket(text_report="x", connectivity=False)
    assert decide_mode(p) == ExecutionMode.LOCAL


def test_triage_endpoint_offline():
    client.post("/v1/connectivity/off")
    r = client.post("/v1/triage", json={"text_report": "severe bleeding leg", "vitals": {"hr": 135}})
    body = r.json()
    assert r.status_code == 200
    assert body["mode"] == "local"
    assert body["final_severity"] == "RED"
    client.post("/v1/connectivity/on")


def test_route_planner():
    grid = [[0, 0, 0], [1, 1, 0], [0, 0, 0]]
    path = plan_route(grid, (0, 0), (2, 0))
    assert path[0] == [0, 0] or path[0] == (0, 0)
    assert path[-1] == (2, 0)


def test_route_unreachable():
    grid = [[0, 1], [1, 0]]
    assert plan_route(grid, (0, 0), (1, 1)) == []


def test_allocation_red_first():
    victims = [
        {"id": "g", "severity": "GREEN", "lat": 0, "lon": 0},
        {"id": "r", "severity": "RED", "lat": 5, "lon": 5},
    ]
    units = [{"id": "u1", "lat": 0, "lon": 0}]
    a = allocate_resources(victims, units)
    assert a[0]["victim_id"] == "r"


def test_connectivity_toggle():
    assert client.post("/v1/connectivity/off").json()["connectivity"] is False
    assert client.post("/v1/connectivity/on").json()["connectivity"] is True


# ---- new-feature tests -------------------------------------------------

def test_infra_endpoint():
    r = client.get("/v1/infra")
    assert r.status_code == 200
    body = r.json()
    assert "cloud" in body and "local_gpu" in body
    assert body["cloud"]["provider"].startswith("Fireworks")


def test_reasoning_trace_present():
    client.post("/v1/connectivity/off")
    r = client.post("/v1/triage", json={"text_report": "trapped, not breathing"})
    body = r.json()
    assert isinstance(body["reasoning_trace"], list) and len(body["reasoning_trace"]) >= 2
    assert any("ROUTE" in line for line in body["reasoning_trace"])
    client.post("/v1/connectivity/on")


def test_missing_person_offline_ranks():
    from app.agents.missing_person import _cosine, _tokens
    a = _tokens("red jacket boy age 8 near school")
    b = _tokens("young boy red jacket found near the school")
    c = _tokens("elderly woman blue saree")
    assert _cosine(a, b) > _cosine(a, c)


def test_missing_person_endpoint_offline():
    client.post("/v1/connectivity/off")
    body = {
        "query": "boy in red jacket age 8 near school",
        "candidates": [
            {"id": "v1", "description": "young boy red jacket found near school"},
            {"id": "v2", "description": "elderly woman blue saree market"},
        ],
    }
    r = client.post("/v1/missing-person", json=body)
    matches = r.json()["payload"]["matches"]
    assert matches[0]["id"] == "v1"
    client.post("/v1/connectivity/on")


def test_vision_endpoint_no_cloud_graceful():
    # With no FIREWORKS_API_KEY set in test env, vision degrades gracefully.
    r = client.post("/v1/vision", json={"image_ref": "https://example.com/x.jpg"})
    assert r.status_code == 200
    assert "damage_level" in r.json()["payload"]


def test_uncertainty_flag_low_confidence():
    # GREEN heuristic has confidence 0.6 (< 0.65) -> should flag for review
    client.post("/v1/connectivity/off")
    r = client.post("/v1/triage", json={"text_report": "minor scrape", "vitals": {"hr": 80}})
    body = r.json()
    tri = next(x for x in body["responses"] if x["agent"] == "triage")
    assert tri["needs_human_review"] is True
    assert tri["uncertainty"] > 0.0
    assert "NEEDS HUMAN REVIEW" in body["dispatch_summary"]
    assert any("UNCERTAINTY" in line for line in body["reasoning_trace"])
    client.post("/v1/connectivity/on")


def test_uncertainty_cleared_high_confidence():
    # RED heuristic has confidence 0.8 (>= 0.65) -> should NOT flag
    client.post("/v1/connectivity/off")
    r = client.post("/v1/triage", json={"text_report": "victim not breathing, trapped"})
    body = r.json()
    tri = next(x for x in body["responses"] if x["agent"] == "triage")
    assert tri["needs_human_review"] is False
    client.post("/v1/connectivity/on")
