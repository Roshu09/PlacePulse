#!/usr/bin/env bash
# Saves your Groq API key into .env (creating .env from .env.example if needed).
# The key is typed hidden, never printed, and .env is git-ignored.
#
# Usage:   ./scripts/set-groq-key.sh
set -e
cd "$(dirname "$0")/.."

if [ ! -f .env ]; then
  if [ -f .env.example ]; then
    cp .env.example .env
    echo "Created .env from .env.example (remember to set real passwords in it)."
  else
    touch .env
  fi
fi

echo "Get a free key at https://console.groq.com/keys  (it starts with gsk_)"
read -r -s -p "Paste your Groq API key (hidden): " key
echo
key="$(printf '%s' "$key" | tr -d '[:space:]')"

if [ -z "$key" ]; then
  echo "No key entered. Nothing changed."; exit 1
fi
case "$key" in
  gsk_*) ;;
  *) echo "That does not look like a Groq key (they start with gsk_). Nothing changed."; exit 1 ;;
esac

# replace the line if it exists, otherwise append it. Done with python so no character in the key can break a sed pattern.
KEY="$key" python3 - <<'PY'
import os, re
path = ".env"
key = os.environ["KEY"]
text = open(path).read() if os.path.exists(path) else ""
line = f"GROQ_API_KEY={key}"
if re.search(r"^GROQ_API_KEY=.*$", text, re.M):
    text = re.sub(r"^GROQ_API_KEY=.*$", lambda m: line, text, flags=re.M)
else:
    text = text.rstrip("\n") + ("\n" if text else "") + line + "\n"
open(path, "w").write(text)
PY
chmod 600 .env

echo "Saved GROQ_API_KEY in .env (${key:0:4}...${key: -4}). Restart ./start-backend.sh to use it."
