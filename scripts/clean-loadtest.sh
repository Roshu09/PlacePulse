#!/usr/bin/env bash
# Removes everything the load test created: its students, drives, applications and notifications.
# Only rows made by loadtest/k6-mixed.js are touched (k6 e-mail domain / "k6 Load Test" drives).
set -e
cd "$(dirname "$0")/.."
set -a; [ -f .env ] && . ./.env; set +a
U="${POSTGRES_USER:-placepulse}"
q() { docker compose exec -T postgres psql -U "$U" -v ON_ERROR_STOP=1 -tA -d "$1" -c "$2"; }

IDS=$(q auth_db "select string_agg(quote_literal(id::text), ',') from users where email like '%@loadtest.placepulse.test';")
q drive_db "delete from applications where drive_id in (select id from drives where company like 'k6 Load Test%');"
q drive_db "delete from drives where company like 'k6 Load Test%';"
q notification_db "delete from notifications where title like '%k6 Load Test%';"
if [ -n "$IDS" ] && [ "$IDS" != "NULL" ]; then
  q notification_db "delete from notifications where recipient_id::text in ($IDS);"
  q notification_db "delete from known_students where id::text in ($IDS);"
  q auth_db "delete from users where email like '%@loadtest.placepulse.test';"
fi
docker compose exec -T redis redis-cli DEL placepulse:drive:analytics:v1 >/dev/null 2>&1 || true
echo "Load test data removed."
