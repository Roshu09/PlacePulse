#!/usr/bin/env bash
# Measures GET /api/analytics three ways: straight from the database, from Redis, and from Redis
# with 20 parallel clients. Prints numbers you can paste into the README or a resume bullet.
#
# Usage:  TPR_EMAIL=you@example.com TPR_PASSWORD=... ./scripts/bench-analytics.sh [requests]
# Needs the backend running (./start-backend.sh) and, ideally, data from ./scripts/seed-analytics.sh.
set -e
cd "$(dirname "$0")/.."
N=${1:-200}
AUTH=${AUTH_URL:-http://localhost:8081}
DRIVE=${DRIVE_URL:-http://localhost:8082}
: "${TPR_EMAIL:?set TPR_EMAIL}"; : "${TPR_PASSWORD:?set TPR_PASSWORD}"

TOKEN=$(curl -s -X POST "$AUTH/api/auth/login" -H 'Content-Type: application/json' \
  -d "{\"email\":\"$TPR_EMAIL\",\"password\":\"$TPR_PASSWORD\"}" | python3 -c 'import sys,json; print(json.load(sys.stdin)["token"])')
[ -n "$TOKEN" ] || { echo "Login failed"; exit 1; }

hit() { curl -s -o /dev/null -D - -H "Authorization: Bearer $TOKEN" "$DRIVE/api/analytics$1" | tr -d '\r' | awk -F': ' 'tolower($1)=="x-cache"{print $2}'; }
timeit() { curl -s -o /dev/null -w '%{time_total}\n' -H "Authorization: Bearer $TOKEN" "$DRIVE/api/analytics$1"; }
export -f timeit; export TOKEN DRIVE

stats() { python3 -c '
import sys, statistics as st
v = sorted(float(x) * 1000 for x in sys.stdin if x.strip())
p = lambda q: v[min(len(v) - 1, int(len(v) * q))]
print(f"  requests {len(v):4d} | avg {st.mean(v):7.1f} ms | p50 {p(.50):7.1f} ms | p95 {p(.95):7.1f} ms | max {v[-1]:7.1f} ms")'; }

echo "Rows in play:"
curl -s -H "Authorization: Bearer $TOKEN" "$DRIVE/api/analytics?nocache=true" | python3 -c '
import sys, json
t = json.load(sys.stdin)["totals"]
print("  %d drives, %d applications" % (t["drives"], t["applications"]))'

echo; echo "1) No cache (every request runs the aggregate queries), sequential:"
for _ in 1 2 3; do timeit "?nocache=true" >/dev/null; done   # warm the JVM and connection pool
for _ in $(seq "$N"); do timeit "?nocache=true"; done | stats

echo; echo "2) With Redis, sequential:"
echo "  first request was: $(hit '')   second: $(hit '')"
for _ in $(seq "$N"); do timeit ""; done | stats

echo; echo "3) No cache, 20 parallel clients:"
seq "$N" | xargs -P 20 -I{} bash -c 'timeit "?nocache=true"' | stats
echo; echo "4) With Redis, 20 parallel clients:"
seq "$N" | xargs -P 20 -I{} bash -c 'timeit ""' | stats
