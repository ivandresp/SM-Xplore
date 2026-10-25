# AGENTS.md

## Project state

SM-Xplore is a tourism management platform for Santa Marta, Colombia. The repo started as a design/documentation project and now contains the first backend microservice: `microservices/ms-01-users-and-anomalies` (identity/auth and admin user management implemented; reviews and anomalies pending).

All deliverables, user stories, diagrams, and commit messages are in **Spanish**. Code identifiers are English; domain class names follow the Spanish model (`Usuario`, `Guia`, `PrestadoraServicios`).

## Canonical paths

| Path | What it is |
| --- | --- |
| `docs/user-stories/<role>/us.md` | User stories, one file per role |
| `docs/models/` | Tracked PlantUML + static design images (domain, classes, UC, packages, deployment, DB, E-R) |
| `docs/requirements/SM-Xplore_SRS.docx` | SRS v2.0 (binary Word; no Markdown source) |
| `docs/plan/` | RUP plan (`.xlsx`) and team roles |
| `microservices/ms-01-users-and-anomalies/` | Java 25 + Spring Boot 4.1.2 identity/auth service |
| `microservices/ms-02...ms-04/` | Placeholder READMEs for the remaining services |
| `docs/Documento Entregable 1 - SM-Xplore.md` / `.docx` | Final deliverable; no export script, keep both in sync manually |

Path/naming traps:

- Diagram sources live in `docs/models/` (English), **not** `docs/modelos/`. `docs/modelos/` only contains untracked, stale PNG renders under `_render/`; the deliverable Markdown still links to `docs/modelos/...`.
- The folder on disk is `docs/requirements/` (lowercase). Links using `docs/Requirements/` work on Windows but break on case-sensitive systems.
- `docs/Documento Entregable 1 (6).md` is the assignment template/rubric, not the final deliverable.
- Files are UTF-8. Windows PowerShell 5 output often shows mojibake (`GuÃ­a`, `ReseÃ±as`); use the Read tool or set `[Console]::OutputEncoding` before "fixing" accents that are actually fine.

## Build, run, test

Java 25 and Gradle 9.5.1 are required. Use the wrapper from the repo root:

```bash
docker compose -f microservices/docker-compose.yml up -d
./gradlew :microservices:ms-01-users-and-anomalies:bootRun
./gradlew :microservices:ms-01-users-and-anomalies:test
./gradlew :microservices:ms-01-users-and-anomalies:test --tests "com.smxplore.users.identity.infrastructure.adapter.in.rest.AuthFlowTest"
./gradlew :microservices:ms-01-users-and-anomalies:check
```

- API: `http://localhost:8081`; Swagger: `/swagger-ui.html`; Mailpit: `http://localhost:8025`.
- Integration tests use Testcontainers and **require Docker**.
- `check` runs tests, ArchUnit, and Spotless.
- Default local bootstrap admin: `admin@smxplore.local` / `ChangeMe123!`; override with `BOOTSTRAP_ADMIN_*` outside development.
- PostgreSQL is the only implemented store. MongoDB is reserved for guide/provider reviews.

## Architecture rules

- Clean/hexagonal: `infrastructure -> application -> domain`.
- `domain` must not import Spring, Jakarta/JPA, Mongo, or Jackson. ArcUnit enforces this; application must not depend on infrastructure.
- Application use cases live in `application/service`, implement input ports, and use output ports. Write use cases run inside `TransactionPort` (implemented with `TransactionTemplate`); there is no `@Transactional` in controllers or domain.
- Domain uses the documented inheritance: `Usuario` is the sealed base class and `Turista`, `Guia`, `PrestadoraServicios`, `GestoraDestinos`, and `Admin` extend it. Do not introduce a separate `Profile`/`User` domain entity.
- Adapters own JPA entities and mappers; domain has no persistence annotations. `RoleDetailsView` is an API read DTO, not a domain entity.
- Controllers only translate HTTP ↔ application DTOs. Errors are RFC 9457 `ProblemDetail`.
- No Lombok/MapStruct; use records and explicit mappers. Constructor injection only.

## Domain and planned architecture

- 5 roles: Turista, Guía Turístico, Prestadora de Servicios, Gestora de Destinos, Admin. `Usuario` is the base concept in `docs/models/domain/diagram.puml`.
- 11 level-0 use cases (UC1–UC11) in `docs/models/uc-lvl-0/diagram.puml`. The SRS defines RF01–RF108 with UC/HU/PA traceability.
- 4 microservices: ms-01 users + anomalies (+ guide/provider reviews), ms-02 security reports, ms-03 activities + events (+ reservations/reviews), ms-04 sites + services (+ reservations/reviews).
- Polyglot persistence: PostgreSQL (transactional), MongoDB (semi-structured), Neo4j (relations/recommendations). ms-01 uses PostgreSQL for users and MongoDB later for reviews.
- Chosen stack: Java 25 + Spring Boot 4.1.2; React + JavaScript/TypeScript for the frontend. External integrations: OpenStreetMap, OpenMeteo, PSE, email/Web Push. UI is Spanish/English.
- Ley 1581 de 2012 (Colombian data protection) is a hard compliance constraint (admin US5, RNF09).

## MS-01 remaining work

Current branch scope is **only** `microservices/ms-01-users-and-anomalies`; do not scaffold ms-02–ms-04 here.

Implemented: tourist/provider registration, login, RS256 JWT/JWKS with persisted RSA keys, `/users/me`, self profile update, self-service password change, forgot/reset password, bootstrap admin, admin user management (create internal accounts with invitation, approve/reject providers, block/suspend/reactivate, list/search/filter/paginate, admin password reset, audit log), public profiles without identity documents, persistence, tests, ArchUnit, and Spotless.

Still to design/implement:

1. **Admin user management** (RF06–RF13, Admin US1–US8)
   - Change a user’s role, migrating the role subtype correctly.
   - Delete/deactivate and anonymize PII (Ley 1581).
   - RF01 is now complete: Turista/Prestadora self-register and Guía/Gestora/Admin are created by an admin.

2. **Guide/provider reviews in MongoDB** (RF69–RF75, RF77)
   - Add Spring Data MongoDB and the `smxplore_reviews` database.
   - `Review` document, indexes, repository adapter, and mapping.
   - Create/read reviews, guide/provider responses, report, moderation, and reputation summary.
   - Enforce one review per tourist per target.
   - Only guide/provider reviews belong in ms-01; other review targets belong to their owning services.
   - Integration tests with a MongoDB Testcontainer.

3. **Anomalies** (RF99–RF102, Admin US11–US14)
   - Table and API for anomalies.
   - Classify by severity and mark as resolved.
   - Configurable alert thresholds.
   - Detection hooks for suspicious access, service downtime, and request spikes.

4. **Security hardening**
   - Admin/operator MFA (RNF01).
   - Rate limiting or brute-force protection.
   - Complete the soft-delete/anonymization flow.
   - Self-service password change is done; JWT keys are persisted (env PEM or the dev key store).

5. **Tests and infrastructure**
   - Password-reset E2E with Mailpit token capture (admin, provisioning, 401/403, duplicate email/DNI/NIT, and public-profile tests already exist).
   - MongoDB tests and index creation/migration.
   - CI and a Dockerfile for the service.

## JWT contract (ms-01 → ms-02)

- RS256 access-only tokens; no refresh endpoint.
- Claims: `sub` and `id` (same user UUID), `name`, `role`, `ver`, optional `providerStatus`, `iss=sm-xplore-ms-01`, `aud=sm-xplore-services`, `iat`, `exp`, `jti`.
- Public keys: `GET /.well-known/jwks.json`.
- ms-01 validates `ver` against `users.auth_version` (60 s cache). Other services validate signature, `iss`, `aud`, `exp`.
- If ms-02 reads the UUID from `id`/`userId` instead of `sub`, align the contract before integration.
- Role strings: `TURISTA`, `GUIA`, `PRESTADORA_SERVICIOS`, `GESTORA_DESTINOS`, `ADMIN`.

## Diagrams

- All diagrams are PlantUML (`.puml`), except the DB (`postgre.jpg`, `mongoDB.png`, `neo4j.png`) and E-R (`model.png`) images, which are static.
- No `plantuml.jar` is vendored and there is no render script/CI. Render manually with Java:
  `java -jar plantuml.jar -charset UTF-8 -o docs/modelos/_render/domain docs/models/domain/diagram.puml`
- Renders under `docs/modelos/_render/` are older than the `.puml` sources and only cover `domain`, `uc-lvl-0`, and `uc-lvl-1`. `classes`, `packages`, and `deployment` have no renders.
- UC level-1 folders hold a combined `diagram.puml` plus per-actor `<actor>-diagrama.puml`. Naming is inconsistent: `administrar-anomalias/` and `gestionar-destino/` use `diagrama.puml`, and actor slugs vary (`gestoras-*` for Gestora de Destinos). Follow the existing files.
- `gestionar-reseñas/` uses a real UTF-8 `ñ`; do not confuse it with `gestionar-reservas/`.
- When adding/removing diagrams, update `docs/models/README.md` and `docs/models/uc-lvl-1/README.md`.

## Conventions

- User stories use `##/### US# - <title> (Priority: P#)`; most files use `###`, but `docs/user-stories/guia-turistico/us.md` uses `##`. Priorities: P1 must-have, P2 should-have, P3 nice-to-have.
- US numbers restart at US1 in every role file; always cite `<role> US#`, not a bare `US5`.
- The SRS follows ISO/IEC/IEEE 29148:2018; preserve RF ↔ UC ↔ US ↔ PA traceability.
- Git history uses Spanish commit descriptions, often with English prefixes (`Add:`, `Fix:`, `Update:`); changes land through feature branches and pull requests.
