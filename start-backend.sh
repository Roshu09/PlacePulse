#!/usr/bin/env bash
# Starts everything the backend needs: Postgres, Redis, Redpanda, then auth-service.
set -e
cd "$(dirname "$0")"

docker compose up -d postgres redis redpanda

# load passwords and the TPR invite code from .env
set -a; source .env; set +a
export DB_USER="$POSTGRES_USER" DB_PASSWORD="$POSTGRES_PASSWORD"

# use Java 21 (the project targets 21, your system default is newer)
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk
export PATH="$JAVA_HOME/bin:$PATH"

cd services/auth-service
mvn spring-boot:run
