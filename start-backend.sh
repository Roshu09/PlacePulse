#!/usr/bin/env bash
# Starts everything the backend needs: Postgres, Redis, Redpanda, then auth, drive, notification and ai services.
# Press Ctrl+C once to stop all the services.
set -e
cd "$(dirname "$0")"

docker compose up -d --wait postgres redis redpanda

# load passwords, the JWT secret and the TPR invite code from .env
set -a; source .env; set +a
export DB_USER="$POSTGRES_USER" DB_PASSWORD="$POSTGRES_PASSWORD"
# services run on this machine, so they reach Redpanda through the port it publishes on localhost
# (an old .env may still say redpanda:9092, a name that only exists inside Docker)
export KAFKA_BROKERS=localhost:9092
# ai-service reads the same database settings (host port 5433) and the Groq key
export DB_HOST=localhost DB_PORT=5433

# init.sql only runs when the Postgres volume is first created, so make sure every database exists
for db in auth_db drive_db notification_db ai_db; do
  exists=$(docker compose exec -T postgres psql -U "$POSTGRES_USER" -tAc "SELECT 1 FROM pg_database WHERE datname='$db'")
  if [ "$exists" != "1" ]; then
    echo "Creating database $db"
    docker compose exec -T postgres psql -U "$POSTGRES_USER" -c "CREATE DATABASE $db" >/dev/null
  fi
done

# use Java 21 (the project targets 21, your system default is newer)
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk
export PATH="$JAVA_HOME/bin:$PATH"

# kill all services (and anything they started) when this script exits
trap 'kill 0' EXIT INT TERM

(cd services/auth-service         && mvn -q spring-boot:run 2>&1 | sed -u 's/^/[auth]   /') &
(cd services/drive-service        && mvn -q spring-boot:run 2>&1 | sed -u 's/^/[drive]  /') &
(cd services/notification-service && mvn -q spring-boot:run 2>&1 | sed -u 's/^/[notify] /') &
(cd services/ai-service           && ./run.sh 2>&1 | sed -u 's/^/[ai]     /') &
wait
