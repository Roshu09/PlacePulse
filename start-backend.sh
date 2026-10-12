#!/usr/bin/env bash
# Starts everything the backend needs: Postgres, Redis, Redpanda, then auth-service and drive-service.
# Press Ctrl+C once to stop both services.
set -e
cd "$(dirname "$0")"

docker compose up -d postgres redis redpanda

# load passwords, the JWT secret and the TPR invite code from .env
set -a; source .env; set +a
export DB_USER="$POSTGRES_USER" DB_PASSWORD="$POSTGRES_PASSWORD"

# use Java 21 (the project targets 21, your system default is newer)
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk
export PATH="$JAVA_HOME/bin:$PATH"

# kill both services (and anything they started) when this script exits
trap 'kill 0' EXIT INT TERM

(cd services/auth-service  && mvn -q spring-boot:run 2>&1 | sed -u 's/^/[auth]  /') &
(cd services/drive-service && mvn -q spring-boot:run 2>&1 | sed -u 's/^/[drive] /') &
wait
