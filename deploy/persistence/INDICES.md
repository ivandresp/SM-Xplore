# Indices y constraints de SM-Xplore (P1-7)

Referencia unica de los indices, constraints y garantias de unicidad que existen
**realmente** en la persistencia del monorepo, por motor y por servicio. Sirve
para dos cosas:

1. Saber que esperar tras un despliegue, una restauracion de backup o una
   migracion, sin tener que releer las migraciones Flyway y las anotaciones de
   Spring Data.
2. Tener los comandos exactos para verificar que la base restaurada quedo con
   los mismos objetos que la original.

Docker Desktop no es necesario para leer este documento; los comandos asumen el
stack levantado (`deploy/prod/compose.yaml` en produccion o `compose.yaml` de la
raiz en desarrollo). En los ejemplos se usa el proyecto `smxplore-prod`; en dev
sustituye el nombre por `smxplore` y los puertos por los publicados en el
compose de la raiz.

## Resumen por motor

| Motor | Instancia / base | Consumidores | Objetos |
| --- | --- | --- | --- |
| PostgreSQL 17 | `smxplore_users` | ms-01 | 9 tablas + indices unicos/parciales + checks |
| PostgreSQL 17 | `activities_events` | ms-03 | evento, actividad, reservations, outbox_events |
| PostgreSQL 17 | `smxplore` | ms-04 | zones, reservations, reservation_items, reservation_outbox |
| MongoDB 8.2 | `smxplore_reviews` | ms-01 | `reviews` |
| MongoDB 8.2 | `security_reports` | ms-02 | `security_reports`, `security_report_comments`, `security_report_outbox` |
| MongoDB 8.2 | `reviews` | ms-03 | `event_reviews`, `activity_reviews`, `review_reports` |
| MongoDB 8.2 | `smxplore` | ms-04 | categories, sites, services, reviews, review_reports, outbox_event, notifications, notification_contacts |
| Neo4j 5.26 | `neo4j` | ms-05 | 9 constraints UNIQUE + 10 indices secundarios |

Proyecto Compose y nombres de contenedor por defecto (sin `container_name`):
`<proyecto>-<servicio>-1`, p.ej. `smxplore-prod-postgres-1`,
`smxplore-prod-mongo1-1`, `smxplore-prod-neo4j-1`. En dev el proyecto es
`smxplore` y el servicio Mongo es `mongo` (no `mongo1`).

---

## 1. PostgreSQL

Las migraciones viven en
`microservices/<servicio>/src/main/resources/db/migration/` y las aplica Flyway
al arrancar cada servicio. Un rollback de imagenes **no revierte** Flyway: para
volver atras hay que restaurar el backup (ver `deploy/backup/restore.sh`).

### 1.1 ms-01 - base `smxplore_users`

Migraciones: `V1` a `V10`.

Tablas: `users`, `tourist_profiles`, `guide_profiles`, `provider_profiles`,
`destination_manager_profiles`, `admin_profiles`, `password_reset_tokens`,
`audit_logs`, `anomalies`, `anomaly_thresholds`, `login_attempts`,
`mfa_enrollments`, `mfa_recovery_codes`.

Indices y constraints relevantes:

| Objeto | Tipo | Definicion |
| --- | --- | --- |
| `ux_users_email_lower` | UNIQUE | `users (LOWER(email))` |
| `ux_users_phone` | UNIQUE parcial | `users (phone) WHERE phone IS NOT NULL` |
| `ix_users_role_status` | indice | `users (role, status)` |
| `ix_users_full_name_lower` | indice (V3) | `users (LOWER(full_name))` |
| `ix_users_signed_up_at` | indice (V3) | `users (signed_up_at DESC)` |
| `ux_tourist_profiles_dni` | UNIQUE | `tourist_profiles (dni_number)` |
| `ux_guide_profiles_dni` | UNIQUE | `guide_profiles (dni_number)` |
| `ux_guide_profiles_license` | UNIQUE | `guide_profiles (license)` |
| `ux_provider_profiles_nit` | UNIQUE | `provider_profiles (nit)` |
| `ix_provider_profiles_status` | indice | `provider_profiles (registration_status)` |
| `ux_destination_manager_profiles_nit` | UNIQUE | `destination_manager_profiles (nit)` |
| `ux_password_reset_tokens_hash` | UNIQUE | `password_reset_tokens (token_hash)` |
| `ix_password_reset_tokens_user` | indice | `password_reset_tokens (user_id)` |
| `ix_audit_logs_occurred_at` | indice (V2) | `audit_logs (occurred_at DESC)` |
| `ix_audit_logs_target` | indice (V2) | `audit_logs (target_user_id)` |
| `ix_audit_logs_actor` | indice (V2) | `audit_logs (actor_user_id)` |
| `ix_anomalies_status_severity` | indice (V6) | `anomalies (status, severity, detected_at DESC)` |
| `ix_anomalies_type_detected_at` | indice (V6) | `anomalies (type, detected_at DESC)` |
| `ix_anomalies_detected_at` | indice (V6) | `anomalies (detected_at DESC)` |
| `ix_login_attempts_locked_until` | indice (V7) | `login_attempts (locked_until)` |
| `ux_mfa_recovery_codes_hash` | UNIQUE (V8) | `mfa_recovery_codes (code_hash)` |
| `ix_mfa_recovery_codes_user` | indice (V8) | `mfa_recovery_codes (user_id)` |

Checks (todos `CHECK`): `chk_users_role` reemplazado en `V10` por los codigos
ingleses `TOURIST`, `GUIDE`, `SERVICE_PROVIDER`, `DESTINATION_MANAGER`,
`ADMIN`; `chk_users_status` (`ACTIVO`, `BLOQUEADO`, `SUSPENDIDO`,
`ELIMINADO`); `chk_provider_registration_status`
(`PENDIENTE`/`APROBADO`/`RECHAZADO`); `chk_provider_coordinates` (ambas
coordenadas null o ambas no null); `chk_password_reset_tokens_purpose`
(`PASSWORD_RESET`, `ACCOUNT_INVITATION`, V4); en `anomalies`
`chk_anomalies_type`, `chk_anomalies_severity`, `chk_anomalies_status`,
`chk_anomalies_resolution`; en `anomaly_thresholds` validacion de type,
severity, value (1..1000000) y window (10..3600) + 3 filas semilla.

Todas las FK de los perfiles usan `ON DELETE CASCADE`. `V10` migra datos: pasa
`users.role` y `audit_logs.actor_role` de espanol a los codigos ingleses.

### 1.2 ms-03 - base `activities_events`

Migraciones: `V1` a `V8`.

Tablas finales: `evento`, `actividad`, `reservations`, `outbox_events` (mas las
tablas de archivo `reservations_legacy`, `reservation_items_legacy`,
`reservations_quarantine`; `reservation_items` se elimina en `V2`).

Indices finales:

| Objeto | Definicion |
| --- | --- |
| `evento_sitio_idx` | `evento (sitio_id)` |
| `evento_manager_idx` | `evento (manager_id)` |
| `evento_name_idx` | `evento (name)` |
| `evento_status_starts_idx` (V3) | `evento (status, starts_at)` |
| `evento_updated_at_idx` (V8) | `evento (updated_at, id)` |
| `actividad_sitio_idx` | `actividad (sitio_id)` |
| `actividad_category_idx` | `actividad (category)` |
| `actividad_status_starts_idx` (V3) | `actividad (status, starts_at)` |
| `actividad_updated_at_idx` (V8) | `actividad (updated_at, id)` |
| `reservations_status_idx` | `reservations (status)` (recreado en V2) |
| `reservations_guide_idx` | `reservations (guide_id)` |
| `reservations_provider_idx` (V2) | `reservations (provider_id)` |
| `reservations_item_idx` (V2) | `reservations (item_type, item_id, status)` |
| `reservations_tourist_status_idx` (V2) | `reservations (tourist_id, status)` |
| `reservations_status_starts_idx` (V2) | `reservations (status, item_starts_at)` |
| `reservations_tourist_status_starts_idx` (V3) | `reservations (tourist_id, status, item_starts_at)` |
| `reservations_tourist_idempotency_idx` (V2) | UNIQUE parcial `reservations (tourist_id, idempotency_key) WHERE idempotency_key IS NOT NULL` |
| `outbox_events_claim_idx` (V7) | parcial `outbox_events (occurred_at, id) WHERE status = 'PENDING'` |
| `outbox_events_lease_idx` (V7) | parcial `outbox_events (locked_until) WHERE status = 'PROCESSING'` |
| `outbox_events_retention_idx` (V7) | parcial `outbox_events (created_at) WHERE status IN ('PUBLISHED','FAILED')` |

Secuencia: `reservation_code_seq` (genera `reservation_code`).

Checks finales: en `reservations` el estado valido es
`CONFIRMED`/`CANCELLED`/`NO_SHOW`/`COMPLETED` (V2), `unit_price >= 0`,
`item_type` en `EVENT`/`ACTIVITY`, `total = unit_price * number_of_people` y
`booked_for = item_starts_at`; en `evento` (V3, `NOT VALID`) precio y
`expected_attendance >= 0`, `capacity > 0`, `ends_at > starts_at` y status
`DRAFT`/`PUBLISHED`/`POSTPONED`/`CANCELLED`; en `actividad` (V3, `NOT VALID`)
precio y `capacity >= 0`, `duration_mins > 0`, `ends_at > starts_at` y status
`DRAFT`/`PUBLISHED`/`PAUSED`/`CANCELLED`.

Notas de evolucion: `V1` creaba `reservation_items`, `V2` lo reemplaza por el
modelo simbolico (`item_type`/`item_id`); `V4` creaba
`outbox_events (occurred_at) WHERE published_at IS NULL`, que `V7` sustituye por
el esquema de lease (`status`, `locked_by`, `locked_until`, `next_attempt_at`);
`V5` agrega `evento.sitio_actualizado_en`; `V6` agrega
`reservations.item_name`.

### 1.3 ms-04 - base `smxplore`

Migraciones: `V1`, `V2` (solo datos).

Tablas: `zones`, `reservations`, `reservation_items`, `reservation_outbox`.

| Objeto | Definicion |
| --- | --- |
| `zones_manager_idx` | `zones (manager_id)` |
| `zones_status_idx` | `zones (status)` |
| `zones_name_idx` | `zones (name)` |
| `reservations_tourist_idx` | `reservations (tourist_id)` |
| `reservations_status_idx` | `reservations (status)` |
| `reservations_booked_for_idx` | `reservations (booked_for)` |
| `reservations_capacity_idx` | `reservations (booked_for, status)` |
| `reservations_idempotency_idx` | UNIQUE parcial `reservations (tourist_id, idempotency_key) WHERE idempotency_key IS NOT NULL` |
| `reservation_items_reservation_idx` | `reservation_items (reservation_id)` |
| `reservation_items_service_idx` | `reservation_items (service_id)` |
| `reservation_items_provider_idx` | `reservation_items (provider_id)` |
| `reservation_items_service_unique` | UNIQUE `reservation_items (reservation_id, service_id)` |
| `reservation_items_position_unique` | UNIQUE `reservation_items (reservation_id, position)` |
| `reservation_outbox_pending_idx` | `reservation_outbox (status, next_attempt_at)` |
| `reservation_outbox_lease_idx` | `reservation_outbox (status, locked_until)` |

Checks: `reservations` status `PENDING`/`CONFIRMED`/`CANCELLED`/`COMPLETED`,
`total >= 0`, `number_of_people > 0`; `reservation_items` `number_of_people > 0`,
`unit_price >= 0`, `total >= 0`, `position >= 0`. En `reservation_items` las FK
a `reservations` usan `ON DELETE CASCADE`.

`reservation_outbox` es el **outbox PG de todo ms-04** (reservas y zonas); el
relay (`ReservationOutboxRelay`) publica cada fila al topic indicado por
`event_type`, por eso no hay una tabla separada para `catalog.zone.changed`.
`V2` solo normaliza datos historicos (`PENDING` -> `CONFIRMED` y encola el
evento de cambio en el outbox).

---

## 2. MongoDB

### 2.1 Como se crean los indices

| Servicio | Mecanismo | Flag relevante |
| --- | --- | --- |
| ms-01 | `ReviewIndexInitializer` (`ApplicationRunner`) | `app.reviews.index-initialization.enabled` (dev `true`; prod `${REVIEW_INDEX_INIT:false}` y el compose de produccion define `REVIEW_INDEX_INIT="true"`) |
| ms-02 | anotaciones `@CompoundIndex` + `@GeoSpatialIndexed`, creacion automatica de Spring Data | `spring.data.mongodb.auto-index-creation` (`MONGO_AUTO_INDEX_CREATION`, dev `false`, prod `true`) |
| ms-03 | anotaciones `@CompoundIndex`, creacion automatica | `MONGO_AUTO_INDEX_CREATION` (dev `true`, prod `true`) |
| ms-04 | `MongoIndexInitializer` (`@PostConstruct`) | no depende de auto-index-creation (dev `false`); crea los indices explicitamente |
| Neo4j (ms-05) | `Neo4jSchemaInitializer` lee `cypher/schema.cypher` | `app.neo4j.initialize-schema` / `NEO4J_INITIALIZE_SCHEMA` (prod `true`) |

Implicaciones: en produccion el primer arranque crea los indices
(`REVIEW_INDEX_INIT=true`, `MONGO_AUTO_INDEX_CREATION=true`); una vez creados se
pueden volver a `false` para no recrearlos en cada arranque. Si se restaura un
backup sobre un stack ya inicializado, los indices se recrean solos con los
mismos flags; verifica con `getIndexes()`.

### 2.2 ms-01 - base `smxplore_reviews`, coleccion `reviews`

Indices con nombre explicito (`@CompoundIndexes` en `ReviewDocument`):

| Nombre | Keys |
| --- | --- |
| `ux_reviews_tourist_target` | UNIQUE `{ touristId: 1, targetType: 1, targetId: 1 }` |
| `ix_reviews_target` | `{ targetType: 1, targetId: 1, moderationStatus: 1, createdAt: -1 }` |
| `ix_reviews_reported` | `{ reported: 1, updatedAt: -1 }` |
| `ix_reviews_ai_flagged` | `{ aiFlagged: 1, moderationStatus: 1, createdAt: -1 }` |

Garantia de negocio: un turista solo puede resenar una vez cada `targetType` +
`targetId` (indice unico). Esperado en `getIndexes()`: 5 documentos incluyendo
`_id_`.

### 2.3 ms-02 - base `security_reports`

| Coleccion | Indice | Keys |
| --- | --- | --- |
| `security_reports` | `idx_security_reports_status_created_at_v2` | `{ status: 1, createdAt: -1, _id: -1 }` |
| `security_reports` | `idx_security_reports_user_created_at_v2` | `{ "user.userId": 1, createdAt: -1, _id: -1 }` |
| `security_reports` | `idx_security_reports_position` | `2dsphere` sobre `position` |
| `security_report_comments` | `idx_security_report_comments_report_created_at_v2` | `{ reportId: 1, createdAt: 1, _id: 1 }` |
| `security_report_outbox` | (sin indices propios) | — |

En dev `auto-index-creation=false`, asi que tras arrancar en limpio hay que
verificar que existan; en produccion el compose lo pone en `true`.

Desde la fase INFORMATIVA (MS02-17) el servicio arranca ademas un
`MongoIndexGuard` que comprueba al iniciar que existan los cuatro indices de la
tabla (`idx_security_reports_status_created_at_v2`,
`idx_security_reports_user_created_at_v2`, `idx_security_reports_position` y
`idx_security_report_comments_report_created_at_v2`). Si faltan, el arranque
falla fuera de `local/dev/test` indicando el nombre ausente; con
`auto-index-creation=false` el guard no los crea: se aprovisionan con los
comandos de la seccion 4.2 o volviendo a `MONGO_AUTO_INDEX_CREATION=true` un
arranque. Decision vigente: produccion mantiene `MONGO_AUTO_INDEX_CREATION=true`
y el guard actua como red de seguridad.

### 2.4 ms-03 - base `reviews`

| Coleccion | Indice | Keys |
| --- | --- | --- |
| `event_reviews` | `event_review_item_user` | UNIQUE `{ itemId: 1, "user.id": 1 }` |
| `event_reviews` | `event_review_item_created` | `{ itemId: 1, createdAt: -1 }` |
| `event_reviews` | `event_review_status_updated` | `{ status: 1, updatedAt: -1 }` |
| `event_reviews` | `event_review_event_pending` | `{ eventPublishedAt: 1, updatedAt: 1 }` |
| `activity_reviews` | `activity_review_item_user` | UNIQUE `{ itemId: 1, "user.id": 1 }` |
| `activity_reviews` | `activity_review_item_created` | `{ itemId: 1, createdAt: -1 }` |
| `activity_reviews` | `activity_review_status_updated` | `{ status: 1, updatedAt: -1 }` |
| `activity_reviews` | `activity_review_event_pending` | `{ eventPublishedAt: 1, updatedAt: 1 }` |
| `review_reports` | `review_report_review_reporter` | UNIQUE `{ reviewId: 1, reporterId: 1 }` |
| `review_reports` | `review_report_review` | `{ reviewId: 1 }` |
| `review_reports` | `review_report_status_created` | `{ status: 1, createdAt: 1 }` |

Garantias: una resena por item y usuario (indices unicos), y un reporte por
resena y reportante.

### 2.5 ms-04 - base `smxplore`

Los indices los crea `MongoIndexInitializer` al arrancar. Nombres generados por
Mongo salvo que el inicializador fije uno; compara por keys.

| Coleccion | Keys | Notas |
| --- | --- | --- |
| `categories` | `{ scope: 1 }`, `{ parentId: 1 }` | arbol de categorias |
| `sites` | `{ status: 1 }`, `{ categoryId: 1 }`, `{ "zone.id": 1 }`, `{ managerId: 1 }`, `{ updatedAt: -1 }` | filtros de catalogo |
| `services` | `{ status: 1 }`, `{ categoryId: 1 }`, `{ "zone.id": 1 }`, `{ "site.id": 1 }`, `{ "provider.id": 1 }`, `{ updatedAt: -1 }` | filtros de catalogo |
| `reviews` | UNIQUE `{ touristId: 1, itemType: 1, itemId: 1 }`, `{ itemType: 1, itemId: 1, status: 1, createdAt: -1 }`, `{ aiRetained: 1, status: 1, updatedAt: -1 }` | una resena por turista e item |
| `review_reports` | UNIQUE `{ reviewId: 1, reportedBy: 1 }`, `{ status: 1, createdAt: -1 }`, `{ reviewId: 1, status: 1 }` | un reporte por resena y usuario |
| `outbox_event` | `{ status: 1, nextAttemptAt: 1, occurredAt: 1 }` | outbox Mongo (catalogo/resenas) |
| `notifications` | `{ recipientId: 1, createdAt: -1 }`, `{ recipientId: 1, readAt: 1 }`, `{ reservationId: 1 }` | bandeja de notificaciones |
| `notification_contacts` | (solo `_id_`) | contactos de notificacion |

Esperado tras el primer arranque (incluyendo `_id_`): `categories` 3, `sites` 6,
`services` 7, `reviews` 5, `review_reports` 5, `outbox_event` 2,
`notifications` 4, `notification_contacts` 1.

---

## 3. Neo4j (ms-05)

Fuente: `microservices/ms-05-*/src/main/resources/cypher/schema.cypher`, que
`Neo4jSchemaInitializer` ejecuta al arrancar cuando
`app.neo4j.initialize-schema=true` (prod `NEO4J_INITIALIZE_SCHEMA=true`).
Son sentencias `CREATE CONSTRAINT ... IF NOT EXISTS` e
`CREATE INDEX ... IF NOT EXISTS`, por lo que el arranque es idempotente.

### Constraints UNIQUE

| Nombre | Label.propiedad |
| --- | --- |
| `turista_id` | `TuristaProfile.touristId` |
| `categoria_id` | `Categoria.categoriaId` |
| `catalog_item_id` | `CatalogItem.itemId` |
| `sitio_id` | `Sitio.siteId` |
| `actividad_id` | `Actividad.activityId` |
| `evento_id` | `Evento.eventId` |
| `servicio_id` | `Servicio.serviceId` |
| `processed_event_id` | `ProcessedEvent.eventId` |
| `zona_id` | `Zona.zoneId` |

### Indices secundarios

| Nombre | Propiedad |
| --- | --- |
| `catalog_item_status` | `CatalogItem.status` |
| `catalog_item_type` | `CatalogItem.type` |
| `catalog_item_site` | `CatalogItem.siteId` |
| `catalog_item_category` | `CatalogItem.categoryId` |
| `catalog_item_updated_at` | `CatalogItem.updatedAt` |
| `evento_starts_at` | `Evento.startsAt` |
| `evento_ends_at` | `Evento.endsAt` |
| `categoria_parent` | `Categoria.parentId` |
| `categoria_scope` | `Categoria.scope` |
| `processed_event_processed_at` | `ProcessedEvent.processedAt` |

`SHOW INDEXES` tambien lista los indices de soporte de cada constraint UNIQUE;
por eso el total sale mayor que 10. Compara por nombre, no solo por cantidad.

---

## 4. Verificacion

### 4.1 PostgreSQL

Listar indices de una base:

```bash
docker compose -p smxplore-prod -f deploy/prod/compose.yaml exec -T postgres \
  psql -U smxplore -d smxplore -c \
  "SELECT indexname, indexdef FROM pg_indexes WHERE schemaname = 'public' ORDER BY tablename, indexname;"
```

Constraints (checks, uniques, FKs) de una tabla:

```bash
docker compose -p smxplore-prod -f deploy/prod/compose.yaml exec -T postgres \
  psql -U smxplore -d smxplore -c \
  "SELECT conname, contype, pg_get_constraintdef(oid) FROM pg_constraint
   WHERE conrelid = 'reservations'::regclass ORDER BY conname;"
```

Tablas por base:

```bash
docker compose -p smxplore-prod -f deploy/prod/compose.yaml exec -T postgres \
  psql -U smxplore -d smxplore_users -c '\dt'
```

Mismo patron para `activities_events` (ms-03) y `smxplore` (ms-04). Compara
ademas `SELECT version, description, success FROM flyway_schema_history ORDER BY installed_rank;`
para confirmar que las migraciones aplicadas son las esperadas.

### 4.2 MongoDB

En produccion, autenticado y con TLS (la CA esta en `/tmp/mongo-ca.pem` dentro
del nodo). En dev se puede omitir `--tls` y las credenciales:

```bash
# PLAT-11: no teclees la password en la linea de comandos (queda en el historial
# del shell y en `ps`). Exportala y deja que mongosh la lea del entorno.
read -rsp 'Password de ms01_app: ' MONGO_PASSWORD; export MONGO_PASSWORD
docker compose -p smxplore-prod -f deploy/prod/compose.yaml exec -T \
  -e MONGO_PASSWORD -e MONGO_USER=ms01_app -e MONGO_AUTH_DB=smxplore_reviews mongo1 \
  mongosh --quiet --tls --tlsCAFile /tmp/mongo-ca.pem \
    "mongodb://mongo1:27017,mongo2:27017,mongo3:27017/smxplore_reviews?replicaSet=rs0" \
    --eval 'db.getSiblingDB(process.env.MONGO_AUTH_DB).auth(process.env.MONGO_USER, process.env.MONGO_PASSWORD);
            db.getCollectionNames().forEach(c => print(c + ": " + db.getCollection(c).getIndexes().map(i => i.name).join(", ")))'
```

Para una coleccion concreta:

```bash
... --eval 'db.reviews.getIndexes().map(i => ({ name: i.name, key: i.key, unique: !!i.unique }))'
```

Espera exactamente los nombres/keys de la seccion 2; si falta alguno, vuelve a
arrancar el servicio con su flag de inicializacion activo (seccion 2.1).

### 4.3 Neo4j

```bash
docker compose -p smxplore-prod -f deploy/prod/compose.yaml exec -T neo4j \
  cypher-shell -u neo4j -p "$NEO4J_PASSWORD" "SHOW CONSTRAINTS;"

docker compose -p smxplore-prod -f deploy/prod/compose.yaml exec -T neo4j \
  cypher-shell -u neo4j -p "$NEO4J_PASSWORD" "SHOW INDEXES;"
```

Comprueba los 9 constraints y los 10 indices secundarios por nombre.

### 4.4 Tras un backup / restore

1. `deploy/backup/verify-backup.sh --backup DIR` (integridad de los archivos).
2. `deploy/backup/restore.sh --backup DIR` (con `RESTORE=yes` y servicios de
   aplicacion detenidos).
3. Repite las consultas de las secciones 4.1-4.3. En MongoDB y Neo4j los
   indices se recrean al arrancar los servicios si los flags de inicializacion
   estan activos; en PostgreSQL vienen dentro del dump y de
   `flyway_schema_history`.
