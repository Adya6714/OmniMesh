#!/usr/bin/env bash
# Push local secrets.properties → GitHub Actions secrets (repo Adya6714/OmniMesh).
# Run once after cloning on a new machine. Requires: gh auth login, secrets.properties at repo root.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
PROPS="${ROOT}/secrets.properties"
REPO="${GITHUB_REPO:-Adya6714/OmniMesh}"

if [[ ! -f "$PROPS" ]]; then
  echo "Missing ${PROPS}" >&2
  echo "Copy secrets.properties.example → secrets.properties and fill in values." >&2
  exit 1
fi

if ! command -v gh >/dev/null 2>&1; then
  echo "Install GitHub CLI: https://cli.github.com/" >&2
  exit 1
fi

if ! gh auth status >/dev/null 2>&1; then
  echo "Run: gh auth login" >&2
  exit 1
fi

python3 << PY
import json, os, pathlib, re, subprocess, sys

props = pathlib.Path("${PROPS}").read_text()
repo = "${REPO}"

m = re.search(
    r"FIREBASE_SERVICE_ACCOUNT_OMNIMESH_COMMAND=\s*(\{.*?\})\s*\n\s*//",
    props,
    re.DOTALL,
)
if not m:
    sys.exit("Could not parse FIREBASE_SERVICE_ACCOUNT_OMNIMESH_COMMAND JSON in secrets.properties")
sa_json = m.group(1)
json.loads(sa_json)

def pick(key, pattern):
    m = re.search(pattern, props, re.MULTILINE)
    if not m:
        sys.exit(f"Missing {key} in secrets.properties")
    return m.group(1).strip()

secrets = {
    "FIREBASE_SERVICE_ACCOUNT_OMNIMESH_COMMAND": sa_json,
    "REACT_APP_FIREBASE_API_KEY": pick("apiKey", r'apiKey:\s*"([^"]+)"'),
    "REACT_APP_FIREBASE_AUTH_DOMAIN": pick("authDomain", r'authDomain:\s*"([^"]+)"'),
    "REACT_APP_FIREBASE_PROJECT_ID": pick("projectId", r'projectId:\s*"([^"]+)"'),
    "REACT_APP_FIREBASE_STORAGE_BUCKET": pick("storageBucket", r'storageBucket:\s*"([^"]+)"'),
    "REACT_APP_FIREBASE_MESSAGING_SENDER_ID": pick("messagingSenderId", r'messagingSenderId:\s*"([^"]+)"'),
    "REACT_APP_FIREBASE_APP_ID": pick("appId", r'appId:\s*"([^"]+)"'),
    "REACT_APP_GEMINI_API_KEY": pick("GEMINI_API_KEY", r"^GEMINI_API_KEY=(.+)$"),
}

for name, value in secrets.items():
    r = subprocess.run(
        ["gh", "secret", "set", name, "-R", repo, "--body", value],
        capture_output=True,
        text=True,
    )
    if r.returncode != 0:
        sys.exit(f"gh secret set {name} failed: {r.stderr}")
    print(f"OK {name}")

print(f"Done — {len(secrets)} secrets synced to {repo}")
PY

gh secret list -R "$REPO"
