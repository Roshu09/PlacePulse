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
                     Kafka topic ---> notification-service (Spring Boot)
```

Each service owns its own database. Services communicate through the gateway (sync) and Kafka events (async).

## Services

| Service | Stack | Responsibility |
|---|---|---|
| gateway | Spring Cloud Gateway | Routing, JWT validation |
| auth-service | Spring Boot, Spring Security, PostgreSQL | Login, roles (STUDENT / TPR), profiles |
| drive-service | Spring Boot, PostgreSQL, Kafka | Drives, eligibility rules, applications |
| notification-service | Spring Boot, Kafka, PostgreSQL | In-app notifications built from events (new drive, new application, stage change) |
| ai-service | Python, FastAPI, LangChain, Groq, fastembed | RAG resume vs JD matcher |
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

## Drive API (drive-service, :8082)

Uses the JWT issued by auth-service (same `JWT_SECRET`). When a student applies, drive-service asks auth-service for their profile, passing the student's own token.

| Method | Path | Who | Notes |
|---|---|---|---|
| POST | `/api/drives` | TPR | JSON, or multipart (`drive` JSON part + optional `logo` PNG part). company, roleTitle, minCgpa, deadline, optional package / location / `eligibleBatches` (pass-out years, empty = any) |
| PUT | `/api/drives/{id}` | TPR | edit a drive (same body; `removeLogo` deletes the logo). Existing applications stay. Publishes `drive.updated` |
| GET | `/api/drives/{id}/logo` | public | the PNG (max 500 KB, checked by file signature) |
| GET | `/api/drives` | any | TPR: all drives with applicant counts. Student: only drives still open to apply |
| GET | `/api/drives/{id}` | any | one drive |
| PATCH | `/api/drives/{id}/status` | TPR | `OPEN` or `CLOSED` |
| POST | `/api/drives/{id}/apply` | student | 422 not eligible, 409 closed / already applied, 503 profile service down |
| GET | `/api/drives/{id}/applications` | TPR | applicants with CGPA, course, batch |
| GET | `/api/applications/mine` | student | own applications with stage |
| DELETE | `/api/applications/{id}` | TPR | removes an application so the student can apply again. Publishes `application.removed` |
| PATCH | `/api/applications/{id}/stage` | TPR | `APPLIED, SHORTLISTED, TEST, INTERVIEW, OFFER, REJECTED, DEBARRED` (DEBARRED is set automatically when a registered student edits their profile; moving back reinstates) |
| PATCH | `/api/applications/stage` | TPR | `{ids, stage}` moves many applications at once; only real changes are published |
| GET | `/api/drives/{id}/applications.csv` | TPR | applicants as a CSV download |

Auth-service also gained `PUT /api/users/me/profile` (`cgpa`, `course`, `batch`).

Start everything with `./start-backend.sh` (auth :8081, drive :8082, notification :8083). Run each service's tests with `mvn test` inside its folder.

## Events (Kafka)

drive-service publishes to one topic, `placepulse.events` (3 partitions, keyed by application or drive id so one application's events stay in order). Every message is an envelope: `{eventId, type, occurredAt, payload}`.

| type | When | Who gets notified |
|---|---|---|
| `drive.created` | a TPR posts a drive | every student the service has seen |
| `application.submitted` | a student applies | the TPR who posted the drive |
| `application.stage-changed` | a TPR moves an applicant | that student |

Reliability choices:
- **Published after the database commit**, and a broker outage is logged, never shown to the user: applying still works with Kafka down.
- **Live push (SSE):** a notification is pushed to the open browser tab right after it is stored, so dashboards update without a refresh. The browser reads the stream with `fetch` (so the JWT stays in a header, not a URL), reconnects with backoff, and re-fetches everything after a reconnect. While the stream is down it falls back to polling every 15s.
- **At-least-once, idempotent consumer:** a notification is unique per `(eventId, recipient)`, so a redelivered event never notifies twice.
- **Retries then dead-letter:** a failing message is retried 3 times, then parked on `placepulse.events.DLT`. A message that can never work (broken JSON, missing field) goes there at once. Browse both topics in the console at http://localhost:8089.
- **Loose coupling:** the consumer reads JSON by field name and shares no classes with drive-service.

## Notification API (notification-service, :8083)

Same JWT as the other services. Everyone, student or TPR, sees only their own.

| Method | Path | Notes |
|---|---|---|
| GET | `/api/notifications` | latest 30 plus `unreadCount` |
| GET | `/api/notifications/stream` | live Server-Sent Events: `ready`, `ping` every 20s, `notification` when one is saved |
| POST | `/api/notifications/{id}/read` | 404 if it isn't yours |
| POST | `/api/notifications/read-all` | returns `{updated}` |

A student becomes reachable for "new drive" notifications the first time they open the app (the bell calls this API on every page).

## Roadmap

- [x] Week 0: repo, infra, auth-service skeleton
- [x] Week 1a: auth + JWT (register, login, roles, tests)
- [x] Week 1b: React shell (login, register, role-based layout, dark mode)
- [x] Week 2a: drive-service (drives, eligibility, apply flow, tests)
- [x] Week 2b: drives UI (post a drive, apply, track applications, student profile)
- [x] Week 3: Kafka events, notification-service, notification bell
- [x] Week 4: resume matcher (PDF upload, local embeddings, retrieval, Groq analysis, TPR ranking)
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

## Resume matcher (ai-service, port 8084)

A student uploads a text PDF. The service cuts it into sections, embeds each one **locally and for free** (fastembed, `BAAI/bge-small-en-v1.5`, downloaded once) and stores them in `ai_db`.
For a drive, it embeds the job description, **retrieves** the six most relevant resume sections, and sends only those (about 1,500 tokens) with the job text to a **Groq** model through LangChain. The model returns a score, matched skills, missing skills and suggestions as JSON, which is validated before use.

- Results are cached per (student, drive) and reused until the resume or the job text changes, so the free Groq quota is spent once per pair.
- If Groq is unreachable, rate-limited or has no key, the match falls back to a text-similarity score and says so.
- The TPR ranking uses the cheap similarity score (plus the AI score when the student already ran it), so ranking 75 applicants costs no Groq calls.
- The resume text is untrusted: the prompt tells the model to ignore instructions inside it, and the reply must pass a strict JSON check.

| Method | Path | Who | What |
|---|---|---|---|
| GET / POST / DELETE | `/api/resume` | student | resume info / upload PDF (max 2 MB) / delete |
| POST | `/api/matches/drives/{id}` | student | match my resume to this drive |
| GET | `/api/matches/drives/{id}/applicants` | TPR | applicants ranked by fit |

Set the key with `./scripts/set-groq-key.sh` (free key from console.groq.com). `cd services/ai-service && ./run.sh` starts it on its own; `.venv/bin/python -m pytest` runs its tests (needs `pip install -r requirements-dev.txt`).
