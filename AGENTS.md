# AGENTS.md

## Project

SM-Xplore is a tourism management platform for Santa Marta, Colombia. This repository is a monorepo of backend services under `microservices/`, plus shared event contracts and local infrastructure. Java 25 + Spring Boot 4.1.2 + Spring WebFlux, Gradle 9.8.2 (Kotlin DSL). Each service is self-contained: its own Gradle wrapper, build, `Dockerfile`, `docker-compose.yml` and `.env.example`. There is no root Gradle build.

Integration branch: `microservicios` (the branch `microservices` does not exist). This file is tracked and shared: keep it accurate when the repository state changes.

## Repository state (2026-11-03)

| Service | Folder | Status | Host port | Auth |
|---|---|---|---|---|
| ms-01 users and anomalies | `microservices/ms-01-users-and-anomalies/` | Merged in `microservicios`; identity, MFA, admin, reviews, anomalies | 8081 | RS256/JWKS (issuer) |
| ms-02 security reports | `microservices/ms-02-security-reports/` | Merged (PR #33) | 8080 | HS256, migration pending |
| ms-03 activities and events | `microservices/ms-03-activities-and-events/` | Current work, branch `ms-03-symbolic-reservations` | 8082 | RS256 (JWKS or PEM) |
| ms-04 sites and services | `microservices/ms-04-sites-and-services/` | Merged (PR #34); catalog, reservations, notifications, outbox | 8084 | RS256 (JWKS or PEM) |
| ms-05 recommendations | `microservices/ms-05-recommendations/` | Branch `ms-recommendations`, not merged | 8085 | RS256 |

Shared at the repository root:

- `contracts/`: event envelope, seven versioned schemas and fixtures.
- `infrastructure/`: Kafka, Kafka UI and Redis compose for local integration.
- `.github/workflows/`: `ms-01-ci.yml`, `ms-03-ci.yml` and `ms-04-ci.yml`.
- `docs/`: read-only reference (models, user stories, SRS).

`ms-03-activities-and-events` is the older ms-03 branch, contained in `ms-03-symbolic-reservations`. Never merge it separately.

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

- Integration tests use Testcontainers and require Docker running.
- ms-03: API `http://localhost:8082`, Swagger `/swagger-ui.html`, health `/actuator/health`.
- ms-02: `8080`; ms-04: `8084`; ms-05: `8085`; ms-01: `8081`.

## Architecture rules

- Clean/hexagonal: `infrastructure -> application -> domain`.
- `domain` must not import Spring, Jakarta/JPA, R2DBC, Reactor, Mongo or Jackson.
- Adapters own SQL/Mongo documents and mappers; controllers only translate HTTP; constructor injection.
- Errors must be RFC 9457 `ProblemDetail`. ms-03 still uses a custom `ApiErrorResponse`; the fix is pending.
- Persistence: ms-01 PostgreSQL + MongoDB; ms-02 MongoDB; ms-03 PostgreSQL (events, activities, reservations) + MongoDB (reviews); ms-04 PostgreSQL/MongoDB + outbox; ms-05 Neo4j + Kafka.
- Neo4j remains deferred in ms-03.

## JWT contract

Canonical contract, with ms-01 as issuer:

- RS256 only for new integrations; no shared HS256 secret.
- `iss=sm-xplore-ms-01`, `aud=sm-xplore-services`.
- Public keys at `GET /.well-known/jwks.json`.
- Claims: `sub` and `id` (the same user UUID), `name`, `role`, `ver`, `mfa`, optional `providerStatus`, plus `iss`, `aud`, `iat`, `exp`, `jti`.
- Role wire values (Spanish): `TURISTA`, `GUIA`, `PRESTADORA_SERVICIOS`, `GESTORA_DESTINOS`, `ADMIN`.
- Tokens carrying `scope=mfa` are not access tokens; reject them.
- ms-01 enforces `ver` revocation; the other services validate signature, `iss`, `aud` and `exp` only.
- ms-01, ms-03, ms-04 and ms-05 validate with `JWK_SET_URI` (production) or a PEM public key (local/tests). ms-02 still uses HS256 and must migrate.
- Never copy ms-01 signing code or private keys into another service.

## Events and contracts

- Every event uses `contracts/events/envelope.v1.json`: `eventId`, `eventType`, `eventVersion`, `occurredAt`, `aggregateId`, `correlationId`, `producer` and `payload`.
- Schemas are versioned; renaming, removing or retyping a field requires `v2`.
- Producers: `catalog.*` from ms-04; `activities.*` from ms-03 (pending); `reviews.review.changed` from ms-03/ms-04; `bookings.reservation.changed` from ms-03/ms-04.
- ms-04 has a transactional outbox with relay, retries, metrics and admin reprocess endpoints; use it as the reference.
- ms-03 does not publish events yet, and its model lacks fields the current schemas require (`categoryId`, `latitude`, `longitude`, `updatedAt`; `items[]` for bookings; `status` and `updatedAt` for reviews). Resolve by contract v2 or model enrichment before building the outbox.
- Data copied from another service is eventually consistent; document how it is refreshed.

## ms-03 activities and events

- Branch: `ms-03-symbolic-reservations`. Scope: activities, availability, events, reservations, reviews and review reports. Do not modify ms-01, ms-02 or ms-04.
- Symbolic reservations: one item per reservation (`itemType` + `itemId`), born `CONFIRMED`, capacity taken atomically, payment resolved outside the platform.
- Rules: optional `Idempotency-Key` (1-64) on `POST /api/v1/reservations`; maximum 5 active reservations per tourist; tourist cancellation up to 24 h before start; attendance and no-show after start; daily auto-close at 03:15 `America/Bogota` with `P1D` grace.
- Listings use `ReservationSummaryResponse` without free-text `notes`; only the detail returns them.
- Persistence: PostgreSQL/R2DBC for events, activities and reservations; reactive MongoDB for reviews and reports.
- Flyway: `V1` baseline, `V2` symbolic reservations. `V2` archives originals in `reservations_legacy`/`reservation_items_legacy` and quarantines unrepresentable rows; review `reservations_quarantine` after upgrading.
- Port 8082. Key environment variables: `R2DBC_URL`, `MONGODB_URI`, `JWK_SET_URI`, `JWT_PUBLIC_KEY_LOCATION`, `JWT_ISSUER`, `JWT_AUDIENCE`, `FLYWAY_URL`, `FLYWAY_ENABLED`, `CORS_ALLOWED_ORIGINS`, `RESERVATION_AUTO_CLOSE_CRON`, `RESERVATION_AUTO_CLOSE_GRACE`.
- Current gaps, in order:
  1. `TODO(ms-04)`: validate the site on event creation and refresh `siteName`/`managerId`; manager-scoped listings need a `managerId` filter in ms-04, which does not exist today.
  2. Events/outbox: resolve the contract mismatch first, then publish.
  3. Hardening: `ProblemDetail`, pagination, status filtering in SQL, hide non-published items in public detail, `managerId` accepted from the body for ADMIN, required `paymentMethod`, fail-closed null principals, required `aud`, and least-privilege DB/Mongo users.
- 185 tests as of 2026-11-03, including Testcontainers PostgreSQL tests.

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
- This file is tracked in `microservicios`; update it when the repository state changes instead of keeping a private copy.
