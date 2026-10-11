# PlacePulse

AI-powered campus placement platform built as event-driven microservices.

> Status: scaffolding (Week 0). Replace the placeholders below with real numbers as you measure them.

## Architecture

```
React (Vite + Tailwind + shadcn/ui)
        |
Spring Cloud Gateway  (:8080)
        |
  +-----+---------------+------------------+
  |                     |                  |
auth-service        drive-service     ai-service
(Spring Boot)       (Spring Boot)     (Python + LangChain)
  |                     |                  |
Postgres (auth)     Postgres (drive)   Vector store
                        |
                     Kafka topics ---> notification-service (Node.js)
```

Each service owns its own database. Services communicate through the gateway (sync) and Kafka events (async).

## Services

| Service | Stack | Responsibility |
|---|---|---|
| gateway | Spring Cloud Gateway | Routing, JWT validation |
| auth-service | Spring Boot, Spring Security, PostgreSQL | Login, roles (STUDENT / TPR), profiles |
| drive-service | Spring Boot, PostgreSQL, Kafka | Drives, eligibility rules, applications |
| notification-service | Node.js, Kafka | Email / Telegram alerts from events |
| ai-service | Python, FastAPI, LangChain | RAG resume vs JD matcher |
| frontend | React, Tailwind, shadcn/ui | Student and TPR dashboards |

## Why these choices

- **Kafka:** applying to a drive should not wait on notifications or AI scoring. `application.submitted` is published once, and consumers react independently.
- **Microservices:** notification and AI work are slow and failure-prone, so they are isolated from the apply flow.
- **Redis:** caches hot reads (drive lists) and holds sessions.

## Run locally

```bash
cp .env.example .env
docker compose up -d
# Kafka-compatible broker console: http://localhost:8089
# auth-service health:             http://localhost:8081/actuator/health
```

## Frontend (React + Vite + Tailwind, :5173)

Needs Node 20.19+ (`node -v`).

```bash
cd frontend
cp .env.example .env      # optional, defaults to http://localhost:8081
npm install
npm run dev               # http://localhost:5173
```

Sign-up and sign-in are wired to auth-service. Students see their application stages and open drives; TPRs see a setup checklist and extra Students and Analytics pages. Pages for drives, applications, students and analytics are placeholders until their services exist. The dev server is pinned to port 5173 because auth-service only allows CORS from that origin.

## Auth API (auth-service, :8081)

| Method | Path | Auth | Notes |
|---|---|---|---|
| POST | `/api/auth/register` | none | `email, password (8-72 chars), fullName, inviteCode?` returns `{token, user}`. Role is STUDENT unless `inviteCode` equals `TPR_INVITE_CODE`. |
| POST | `/api/auth/login` | none | `email, password` returns `{token, user}` |
| GET | `/api/users/me` | Bearer JWT | current user |
| GET | `/api/tpr/ping` | Bearer JWT, role TPR | role check demo |

Run the tests with `mvn test` (they use an in-memory H2 database, so Docker is not needed).

## Roadmap

- [x] Week 0: repo, infra, auth-service skeleton
- [x] Week 1a: auth + JWT (register, login, roles, tests)
- [x] Week 1b: React shell (login, register, role-based layout, dark mode)
- [ ] Week 2: drive-service, apply flow
- [ ] Week 3: Kafka events, notification-service
- [ ] Week 4: LangChain RAG matcher
- [ ] Week 5: TPR analytics, Redis caching, tests
- [ ] Week 6: deploy, CI/CD, k6 load test, demo video

## Metrics (fill with measured values only)

| Metric | Value |
|---|---|
| Students onboarded | TBD |
| Drives managed | TBD |
| Kafka throughput (k6) | TBD |
| p95 latency before / after Redis | TBD |
| Test count | TBD |
| Resume-match agreement vs manual | TBD |
