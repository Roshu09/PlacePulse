#!/usr/bin/env bash
# Starts ai-service. The first run creates a virtualenv and installs the dependencies (a few minutes);
# later runs only reinstall when requirements.txt changes.
set -e
cd "$(dirname "$0")"

# pick a Python the ML wheels exist for (newest Pythons sometimes lack them)
PY=""
for candidate in python3.12 python3.13 python3.11 python3; do
  if command -v "$candidate" >/dev/null 2>&1; then PY="$candidate"; break; fi
done
[ -n "$PY" ] || { echo "No python3 found. Install it with: sudo dnf install python3.12"; exit 1; }

if [ ! -d .venv ]; then
  echo "Creating virtualenv with $($PY --version)"
  "$PY" -m venv .venv
fi

stamp=".venv/.requirements.sha"
want=$(sha256sum requirements.txt | cut -d' ' -f1)
if [ ! -f "$stamp" ] || [ "$(cat "$stamp")" != "$want" ]; then
  echo "Installing dependencies..."
  .venv/bin/pip install -q --upgrade pip
  if ! .venv/bin/pip install -q -r requirements.txt; then
    echo "Dependency install failed. If the error mentions onnxruntime or no matching distribution, your Python is too new:"
    echo "  sudo dnf install python3.12 && rm -rf services/ai-service/.venv"
    exit 1
  fi
  echo "$want" > "$stamp"
fi

exec .venv/bin/uvicorn app.main:app --host 0.0.0.0 --port "${AI_PORT:-8084}"
