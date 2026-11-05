# AGENTS.md

## Project

SM-Xplore is a tourism management platform for Santa Marta, Colombia. This repository is a monorepo of backend services under `microservices/`, plus shared event contracts and local infrastructure. Java 25 + Spring Boot 4.1.2 + Spring WebFlux, Gradle 9.8.2 (Kotlin DSL). Each service is self-contained: its own Gradle wrapper, build, `Dockerfile`, `docker-compose.yml` and `.env.example`. There is no root Gradle build.

Integration branch: `microservicios` (the branch `microservices` does not exist). This file is tracked and shared: keep it accurate when the repository state changes.

## Repository state (2026-11-04)

| Service | Folder | Status | Host port | Auth |
|---|---|---|---|---|
| ms-01 users and anomalies | `microservices/ms-01-users-and-anomalies/` | Merged in `microservicios`; identity, MFA, admin, reviews, anomalies | 8081 | RS256/JWKS (issuer) |
| ms-02 security reports | `microservices/ms-02-security-reports/` | Merged (PR #33); reports, comments, outbox, Kafka events | 8080 | RS256 (JWKS or PEM) |
| ms-03 activities and events | `microservices/ms-03-activities-and-events/` | Current work, branch `ms-03-symbolic-reservations` | 8082 | RS256 (JWKS or PEM) |
| ms-04 sites and services | `microservices/ms-04-sites-and-services/` | Merged (PR #34); catalog with the `managerId` filter (PR #36), reservations, notifications, outbox | 8084 | RS256 (JWKS or PEM) |
| ms-05 recommendations | `microservices/ms-05-recommendations/` | Merged (PR #35); Neo4j, Kafka consumers, Redis cache, metrics | 8085 | RS256 |

Shared at the repository root:

- `contracts/`: event envelope, eight versioned schemas and fixtures.
- `infrastructure/`: Kafka, Kafka UI and Redis compose for local integration.
- `.github/workflows/`: `ms-01-ci.yml`, `ms-02-ci.yml`, `ms-03-ci.yml`, `ms-04-ci.yml` and `ms-05-ci.yml`.
- `docs/`: read-only reference (models, user stories, SRS).

`ms-03-activities-and-events` is the older ms-03 branch, contained in `ms-03-symbolic-reservations`. Never merge it separately. The remote `microservicios` still carries only that older code inside `microservices/ms-03-activities-and-events/`.

`AGENTS.md` was deleted on `microservicios` (`42fdcf6`); this branch keeps it and the integration merge restores it, so the pull request that brings ms-03 also re-adds this file. Keep it as the single shared copy.

## Canonical paths

| Path | What it is |
|---|---|
| `microservices/ms-01-users-and-anomalies/` | Users, identity/auth, anomalies (other owner) |
| `microservices/ms-02-security-reports/` | Security reports and comments |
| `microservices/ms-03-activities-and-events/` | Activities, events, reservations and reviews (this work) |
| `microservices/ms-04-sites-and-services/` | Sites, services, reservations, notifications and outbox |
| `docs/models/` | PlantUML sources and static design images |
| `docs/models/deployment/module_3.puml` | Deployment reference for ms-03 |
| `docs/user-stories/<role>/us.md` | User stories, one file per role |
| `docs/requirements/SM-Xplore_SRS.docx` | SRS v2.0 |
| `contracts/events/` | Versioned event schemas and examples |

Path traps: diagram sources live in `docs/models/` (not `docs/modelos/`); `docs/requirements/` is lowercase; `docs/models/uc-lvl-1/gestionar-reseñas/` uses a real UTF-8 ñ. Files are UTF-8; verify with the Read tool before fixing apparent mojibake.

## Stack and conventions

- Java 25, Spring Boot 4.1.2, Gradle 9.8.2 wrapper inside each service.
- WebFlux, R2DBC PostgreSQL, reactive MongoDB, OAuth2 resource server, Actuator, springdoc 3.1.0.
- `bootJar` must produce `app.jar`.
- Lombok + MapStruct are allowed in ms-02, ms-03, ms-04 and ms-05; ms-01 forbids them. The split is a pending convention decision.
- Package roots: ms-01 `com.smxplore.users`, ms-02 `com.smxplore.securityreports`, ms-03 `com.smxplore.activitiesevents`, ms-04 `com.smxplore.sitesandservices`, ms-05 `com.smxplore.recommendations`.
- Constructor injection; records for DTOs; explicit mappers where Lombok/MapStruct are not used.

## Build, run, test

Run inside the service folder; there is no root build:

```powershell
$env:JAVA_HOME = "C:\Users\USUARIO\.jdks\temurin-25\jdk-25.0.4.1+1"
cd microservices/ms-03-activities-and-events
cp .env.example .env
docker compose up --build
./gradlew test
./gradlew build
```

- Integration tests use Testcontainers; without Docker they are skipped, so CI is the only place the migrations are guaranteed to run.
- ms-03: API `http://localhost:8082`, health `/actuator/health`. Swagger is off by default; enable it with `SPRINGDOC_API_DOCS_ENABLED=true` and `SPRINGDOC_SWAGGER_UI_ENABLED=true`.
- ms-02: `8080`; ms-04: `8084`; ms-05: `8085`; ms-01: `8081`.

## Architecture rules

- Clean/hexagonal: `infrastructure -> application -> domain`.
- `domain` must not import Spring, Jakarta/JPA, R2DBC, Reactor, Mongo or Jackson.
- Adapters own SQL/Mongo documents and mappers; controllers only translate HTTP; constructor injection.
- Errors use RFC 9457 `ProblemDetail` (`application/problem+json`).
- Persistence: ms-01 PostgreSQL + MongoDB; ms-02 MongoDB; ms-03 PostgreSQL (events, activities, reservations) + MongoDB (reviews); ms-04 PostgreSQL/MongoDB + outbox; ms-05 Neo4j + Kafka.
- Neo4j remains deferred in ms-03.

## JWT contract

Canonical contract, with ms-01 as issuer:

- RS256 only for new integrations; no shared HS256 secret.
- `iss=sm-xplore-ms-01`, `aud=sm-xplore-services`.
- Public keys at `GET /.well-known/jwks.json`.
- Claims: `sub` and `id` (the same user UUID), `name`, `role`, `ver`, `mfa`, optional `providerStatus`, plus `iss`, `aud`, `iat`, `exp`, `jti`.
- Role wire values (English): `TOURIST`, `GUIDE`, `SERVICE_PROVIDER`, `DESTINATION_MANAGER`, `ADMIN`. ms-01 migrated them in `V10__english_role_codes`; the Spanish codes are read-only aliases while tokens issued before the migration expire. Every service must accept the English code first and the legacy Spanish alias second, and must never emit the Spanish one.
- Tokens carrying `scope=mfa` are not access tokens; reject them.
- ms-01 enforces `ver` revocation; the other services validate signature, `iss`, `aud` and `exp` only.
- ms-01, ms-02, ms-03, ms-04 and ms-05 validate with `JWK_SET_URI` (production) or a PEM public key (local/tests). No service uses HS256 any more.
- Never copy ms-01 signing code or private keys into another service.

## Events and contracts

- Every event uses `contracts/events/envelope.v1.json`: `eventId`, `eventType`, `eventVersion`, `occurredAt`, `aggregateId`, `correlationId`, `producer` and `payload`.
- Schemas are versioned; renaming, removing or retyping a field requires `v2`.
- Producers: `catalog.*` from ms-04; `activities.*` from ms-03 (pending); `reviews.review.changed` from ms-04 (ms-03 pending); `bookings.reservation.changed` from ms-03/ms-04; `security.report.changed` from ms-02 (ms-05 does not consume it).
- ms-04 has a transactional outbox with relay, retries, metrics and admin reprocess endpoints; use it as the reference.
- ms-03 publishes `bookings.reservation.changed` v1 for reservation create/cancel/attendance through the V4 outbox. `NO_SHOW` is skipped because v1 does not include it. Activity/event/review events still need contract v2 or model enrichment (`categoryId`, `latitude`, `longitude`, `updatedAt`, review `status`).
- `reviews.review.changed` v1 now allows `PUBLISHED`, `PENDING_REVIEW`, `HIDDEN` and `DELETED`. `PENDING_REVIEW` is a review retained by the ms-04 AI filter and must not be counted as published. ms-03 does not model review status yet.
- `catalog.site.changed` v1 has no `managerId`, so it cannot refresh the manager snapshot ms-03 keeps on events; that needs a contract v2 or a re-read of `GET /api/v1/sites/{siteId}`.
- Data copied from another service is eventually consistent; document how it is refreshed.

## ms-03 activities and events

- Branch: `ms-03-symbolic-reservations`. Scope: activities, availability, events, reservations, reviews and review reports. Do not modify ms-01, ms-02 or ms-04.
- Symbolic reservations: one item per reservation (`itemType` + `itemId`), born `CONFIRMED`, capacity taken atomically, payment resolved outside the platform.
- Rules: optional `Idempotency-Key` (1-64) on `POST /api/v1/reservations`; maximum 5 active reservations per tourist; tourist cancellation up to 24 h before start; attendance and no-show after start; daily auto-close at 03:15 `America/Bogota` with `P1D` grace.
- Listings use `ReservationSummaryResponse` without free-text `notes`; only the detail returns them.
- Persistence: PostgreSQL/R2DBC for events, activities and reservations; reactive MongoDB for reviews and reports. PostgreSQL roles: `ms03_flyway` (DDL) and `ms03_app` (DML); MongoDB app user `reviews_app` (`readWrite` on `reviews`). Root users are only for container init.
- Flyway: `V1` baseline, `V2` symbolic reservations, `V3` query indexes and `NOT VALID` integrity checks, `V4` outbox. `V2` archives originals in `reservations_legacy`/`reservation_items_legacy` and quarantines unrepresentable rows (no item, multi-item, missing catalog, start before booking, non-divisible total, `PENDING` over capacity); it preserves `total` and deletes only the IDs captured in that run. Review `reservations_quarantine` after upgrading.
- Port 8082. Key environment variables: `R2DBC_URL`, `MONGODB_URI`, `JWK_SET_URI`, `JWT_PUBLIC_KEY_LOCATION`, `JWT_ISSUER`, `JWT_AUDIENCE`, `FLYWAY_URL`, `FLYWAY_ENABLED`, `POSTGRES_USER`, `POSTGRES_PASSWORD`, `FLYWAY_USER`, `FLYWAY_PASSWORD`, `MONGO_APP_USER`, `MONGO_APP_PASSWORD`, `KAFKA_BOOTSTRAP_SERVERS`, `OUTBOX_ENABLED`, `OUTBOX_POLL_INTERVAL_MS`, `MS04_BASE_URL`, `MS04_TIMEOUT`, `CORS_ALLOWED_ORIGINS`, `RESERVATION_AUTO_CLOSE_CRON`, `RESERVATION_AUTO_CLOSE_GRACE`.
- Current gaps, in order:
  1. `TODO(ms-04)`: the `managerId` filter on `GET /api/v1/sites` is merged (PR #36) and ms-03 consumes it, forwards the user token and scopes the reservation listing by the manager's sites. What remains is the `siteName`/`managerId` snapshot ms-03 stores on each event: `catalog.site.changed` v1 does not carry `managerId`, so a manager change needs a contract v2 or a re-read of `GET /api/v1/sites/{siteId}` before authorizing a review response.
  2. Events/outbox: reservation lifecycle events use the V4 outbox and `bookings.reservation.changed` v1. `NO_SHOW` is skipped because v1 lacks it; activity/event/review events still need contract v2 or model enrichment.
  3. Hardening: required `paymentMethod`, and a real DB-level pagination for the largest listings.
- 213 tests as of 2026-11-04, including Testcontainers PostgreSQL/MongoDB tests and a Spring context test.

## Cross-service rules

- Mark every dependency on another service with a `TODO(ms-XX):` comment that states the missing contract and the warning. Never silently assume another service's data.
- Snapshots are eventually consistent; document the refresh strategy.
- Every implementation block is reviewed by at least two independent subagents: `cavecrew-reviewer` plus a security-focused `general` agent. Fix findings before closing the block.
- Before closing a milestone, run the Cloudflare `security-audit` skill against ms-03.

## Documentation

- `docs/` is read-only. Never modify it; ask for explicit approval if an edit is strictly necessary.
- Deliverables, user stories and documentation are in Spanish; commit messages are in English.
- Keep each service `README.md` and `CHANGELOG.md` current: purpose, endpoints, run instructions and environment variables.

## Git rules

- Local commits only. Never push and never open a pull request without an explicit instruction.
- Work stays on the service branch (`ms-03-symbolic-reservations` for ms-03).
- Commit messages are in English with the prefixes `Add:`, `Fix:` or `Update:`.
- Do not commit secrets, `.env` files, build artifacts or IDE files.
- This file was deleted on `microservicios`; keep the branch copy as the single shared version and update it when the repository state changes instead of keeping a private variant.
