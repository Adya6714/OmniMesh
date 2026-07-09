"""Translation (Gemma/Fireworks), route planning (A*), resource allocation (greedy)."""
from __future__ import annotations

import heapq
import time

from app.inference import clients
from app.schemas.models import AgentResponse, AgentType, ExecutionMode, TriagePacket

TRANSLATION_SYSTEM = (
    "You are a disaster-response translator. Translate the victim report to English, "
    "preserving medical detail exactly. Reply with the translation only."
)


async def translate(packet: TriagePacket, mode: ExecutionMode) -> AgentResponse:
    t0 = time.perf_counter()
    if mode == ExecutionMode.LOCAL or not clients.cloud_available():
        return AgentResponse(
            agent=AgentType.TRANSLATION, packet_id=packet.packet_id,
            mode_used=ExecutionMode.LOCAL, model_used="passthrough",
            summary="Offline: report kept in original language, queued for cloud reconcile.",
            payload={"text": packet.text_report or ""},
            latency_ms=(time.perf_counter() - t0) * 1000,
        )
    text, model, latency = await clients.cloud_chat(
        "translation", TRANSLATION_SYSTEM, packet.text_report or ""
    )
    return AgentResponse(
        agent=AgentType.TRANSLATION, packet_id=packet.packet_id,
        mode_used=mode, model_used=model,
        summary="Translated to English via Gemma.",
        payload={"text": text.strip()}, latency_ms=latency,
        reconciled=(mode == ExecutionMode.HYBRID),
    )


def plan_route(grid: list[list[int]], start: tuple[int, int], goal: tuple[int, int]) -> list[tuple[int, int]]:
    """A* on an occupancy grid (0 = free, 1 = blocked). Deterministic, offline, zero tokens."""
    rows, cols = len(grid), len(grid[0])

    def h(a, b):
        return abs(a[0] - b[0]) + abs(a[1] - b[1])

    open_set = [(h(start, goal), 0, start, [start])]
    seen = set()
    while open_set:
        _, g, cur, path = heapq.heappop(open_set)
        if cur == goal:
            return path
        if cur in seen:
            continue
        seen.add(cur)
        for dr, dc in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            nr, nc = cur[0] + dr, cur[1] + dc
            if 0 <= nr < rows and 0 <= nc < cols and grid[nr][nc] == 0 and (nr, nc) not in seen:
                heapq.heappush(open_set, (g + 1 + h((nr, nc), goal), g + 1, (nr, nc), path + [(nr, nc)]))
    return []


def allocate_resources(victims: list[dict], units: list[dict]) -> list[dict]:
    """Greedy severity-first assignment of response units to victims."""
    order = {"RED": 0, "YELLOW": 1, "GREEN": 2, "UNKNOWN": 3}
    victims = sorted(victims, key=lambda v: order.get(v.get("severity", "UNKNOWN"), 3))
    free = list(units)
    assignments = []
    for v in victims:
        if not free:
            break
        # nearest free unit by manhattan distance
        best = min(free, key=lambda u: abs(u["lat"] - v["lat"]) + abs(u["lon"] - v["lon"]))
        free.remove(best)
        assignments.append({"victim_id": v["id"], "unit_id": best["id"], "severity": v.get("severity")})
    return assignments
