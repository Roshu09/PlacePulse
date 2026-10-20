#!/usr/bin/env bash
# Runs the k6 load test (loadtest/k6-mixed.js) through Docker, so k6 does not need to be installed,
# then checks that every application and every notification made it through.
#
# Usage:   TPR_EMAIL=you@x.com TPR_PASSWORD=... ./scripts/loadtest.sh
#          SMOKE=1 TPR_EMAIL=... TPR_PASSWORD=... ./scripts/loadtest.sh      # tiny run to check the setup
# Options: STUDENTS=50 DRIVES=10 RAMP=20 HOLD=60 (seconds)
# Needs the backend running (./start-backend.sh). Clean up afterwards with ./scripts/clean-loadtest.sh
set -e
cd "$(dirname "$0")/.."
: "${TPR_EMAIL:?set TPR_EMAIL}"; : "${TPR_PASSWORD:?set TPR_PASSWORD}"
set -a; [ -f .env ] && . ./.env; set +a

if [ -n "$SMOKE" ]; then STUDENTS=3; DRIVES=2; RAMP=5; HOLD=10; fi
STUDENTS=${STUDENTS:-50}; DRIVES=${DRIVES:-10}; RAMP=${RAMP:-20}; HOLD=${HOLD:-60}

# the host network lets the container reach the services on localhost
set +e
docker run --rm -i --network host \
  -e TPR_EMAIL -e TPR_PASSWORD -e STUDENTS="$STUDENTS" -e DRIVES="$DRIVES" -e RAMP="$RAMP" -e HOLD="$HOLD" \
  grafana/k6:latest run - < loadtest/k6-mixed.js
K6_EXIT=$?
set -e

echo
echo "== Did anything get lost? =="
sleep 5   # let the notification consumer finish
PSQL() { docker compose exec -T postgres psql -U "${POSTGRES_USER:-placepulse}" -tA -d "$1" -c "$2"; }
EXPECTED=$((STUDENTS * DRIVES))
APPS=$(PSQL drive_db "select count(*) from applications a join drives d on d.id = a.drive_id where d.company like 'k6 Load Test%';")
TPRS=$(PSQL notification_db "select count(*) from known_tprs;")
NOTES=$(PSQL notification_db "select count(*) from notifications where type = 'application.submitted' and title like 'New application for k6 Load Test%';")
echo "applications stored:            $APPS (expected $EXPECTED)"
echo "TPR notifications for them:     $NOTES (expected $((APPS * TPRS)): $APPS applications x $TPRS TPR(s))"
[ "$APPS" -eq "$EXPECTED" ] && echo "OK: no application lost" || echo "MISMATCH: applications"
[ "$NOTES" -eq $((APPS * TPRS)) ] && echo "OK: every Kafka event produced its notification" || echo "MISMATCH: notifications (wait a few seconds and re-run the checks, the consumer may still be catching up)"
echo
echo "Clean up with: ./scripts/clean-loadtest.sh"
exit $K6_EXIT
