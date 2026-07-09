#!/usr/bin/env bash
# Run this ON the AMD Developer Cloud GPU instance, from the backend/ folder.
set -euo pipefail

echo "==> 1. Verifying AMD GPU is visible (ROCm)"
rocm-smi || { echo "rocm-smi not found -- is this an AMD GPU instance?"; exit 1; }

echo "==> 2. Verifying Docker + compose"
docker --version
docker compose version

echo "==> 3. Preparing environment file"
[ -f .env ] || cp .env.example .env
echo "   Edit .env now: set FIREWORKS_API_KEY (and HF_TOKEN in your shell for gated Gemma)."
read -rp "   Press Enter once .env is filled in..."

echo "==> 4. Building and launching backend + Gemma-on-ROCm"
docker compose -f docker-compose.yml -f docker-compose.rocm.yml up --build -d

echo "==> 5. Waiting for services..."
sleep 20
echo "   Backend health:"; curl -s localhost:8000/health || true
echo; echo "   Infra status:"; curl -s localhost:8000/v1/infra || true

echo; echo "==> 6. Capturing rocm-smi proof (rocm-smi-proof.txt)"
rocm-smi > rocm-smi-proof.txt
echo "   Saved. Screenshot this + the terminal for your README/submission."
echo "==> DONE."
