#!/usr/bin/env bash
# Quick end-to-end smoke test against a running backend (default localhost:8000).
set -euo pipefail
BASE="${1:-http://localhost:8000}"
echo "== health =="; curl -s "$BASE/health"; echo
echo "== infra =="; curl -s "$BASE/v1/infra"; echo
echo "== connectivity OFF =="; curl -s -X POST "$BASE/v1/connectivity/off"; echo
echo "== triage (expect LOCAL) =="
curl -s -X POST "$BASE/v1/triage" -H 'Content-Type: application/json' \
  -d '{"text_report":"trapped, severe bleeding","vitals":{"hr":140,"spo2":85}}'; echo
echo "== connectivity ON =="; curl -s -X POST "$BASE/v1/connectivity/on"; echo
echo "== triage RED hint (expect HYBRID) =="
curl -s -X POST "$BASE/v1/triage" -H 'Content-Type: application/json' \
  -d '{"text_report":"chest pain","severity_hint":"RED","vitals":{"hr":120}}'; echo
echo "== missing-person =="
curl -s -X POST "$BASE/v1/missing-person" -H 'Content-Type: application/json' \
  -d '{"query":"boy red jacket age 8","candidates":[{"id":"v1","description":"young boy red jacket near school"},{"id":"v2","description":"elderly woman blue saree"}]}'; echo
echo "== route =="
curl -s -X POST "$BASE/v1/route" -H 'Content-Type: application/json' \
  -d '{"grid":[[0,0,0],[1,1,0],[0,0,0]],"start":[0,0],"goal":[2,0]}'; echo
echo "DONE."
