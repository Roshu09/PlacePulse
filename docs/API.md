# PlacePulse API reference

Every service verifies the same JWT (HS256, shared `JWT_SECRET`). Back to the [README](../README.md).

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

Profile and placement registration live in auth-service: `PUT /api/users/me/profile` and `POST /api/users/me/placement-registration` (editing a registered profile needs `acknowledgeDebar: true`, and debars the student's in-progress applications).

Start everything with `./start-backend.sh` (auth :8081, drive :8082, notification :8083). Run each service's tests with `mvn test` inside its folder.

### Analytics (TPR only)

`GET /api/analytics` returns totals (placement rate, offers, average and highest package, average CGPA), the stage funnel, per-drive numbers and a 14-day application timeline. Everything is aggregated in the database (`GROUP BY` queries, no row loading). The finished JSON is cached in Redis under one key; every write that publishes an event, and every drive open/close, deletes that key, and a 60 s TTL is the safety net. If Redis is down the endpoint still works, just slower (the cache fails open, and Redis is excluded from the health check on purpose).

Response headers `X-Cache: HIT|MISS` and `X-Compute-Ms` show what happened. `?nocache=true` skips Redis entirely.

Measure it yourself:

```bash
./scripts/seed-analytics.sh 100000          # fake drives + applications (all marked, removable)
TPR_EMAIL=you@x.com TPR_PASSWORD=... ./scripts/bench-analytics.sh 200
./scripts/seed-analytics.sh --clean         # remove the fake rows
```

For screenshots and demos, `./scripts/seed-demo.sh` loads a small realistic season instead (8 fictional companies, a 75-student batch, offers, debarred students, a 14-day application curve). It is repeatable, and `./scripts/seed-demo.sh --clean` removes it.

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
