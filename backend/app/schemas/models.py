"""Core data contracts for OmniMesh AI backend."""
from __future__ import annotations

from datetime import datetime, timezone
from enum import Enum
from typing import Any, Optional
from uuid import uuid4

from pydantic import BaseModel, Field


class Severity(str, Enum):
    RED = "RED"
    YELLOW = "YELLOW"
    GREEN = "GREEN"
    UNKNOWN = "UNKNOWN"


class ExecutionMode(str, Enum):
    LOCAL = "local"        # edge inference only (offline)
    CLOUD = "cloud"        # Fireworks AI only
    HYBRID = "hybrid"      # local first, cloud reconciles when link returns


class AgentType(str, Enum):
    TRIAGE = "triage"
    TRANSLATION = "translation"
    VISION = "vision"
    ROUTE = "route"
    RESOURCE = "resource"
    MISSING_PERSON = "missing_person"


class GeoPoint(BaseModel):
    lat: float
    lon: float


class TriagePacket(BaseModel):
    """A packet arriving from the mesh (Android edge node or dashboard)."""
    packet_id: str = Field(default_factory=lambda: uuid4().hex)
    timestamp: datetime = Field(default_factory=lambda: datetime.now(timezone.utc))
    location: Optional[GeoPoint] = None
    text_report: Optional[str] = None          # free-text victim/responder report
    language: Optional[str] = None             # ISO code if known
    vitals: dict[str, Any] = Field(default_factory=dict)   # hr, spo2, resp_rate ...
    sensor_flags: dict[str, bool] = Field(default_factory=dict)  # fall_detected, etc.
    image_ref: Optional[str] = None            # URL/path for vision agent
    severity_hint: Severity = Severity.UNKNOWN
    connectivity: bool = True                  # does this node see the internet?


class AgentResponse(BaseModel):
    agent: AgentType
    packet_id: str
    mode_used: ExecutionMode
    model_used: str
    severity: Optional[Severity] = None
    confidence: float = 0.0
    summary: str = ""
    payload: dict[str, Any] = Field(default_factory=dict)
    latency_ms: float = 0.0
    reconciled: bool = False    # True when a cloud pass upgraded a local answer
    uncertainty: float = 0.0            # 1 - confidence; surfaced for triage safety
    needs_human_review: bool = False    # True when the model is not confident enough


class OrchestrationResult(BaseModel):
    packet_id: str
    mode: ExecutionMode
    responses: list[AgentResponse]
    final_severity: Severity
    dispatch_summary: str
    reasoning_trace: list[str] = Field(default_factory=list)
