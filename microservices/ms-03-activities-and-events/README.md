# Microservicio #3: Actividades y Eventos

Servicio encargado de la gestión de **Actividades** y **Eventos** de SM-Xplore.
Incluye las **Reservas** y las **Reseñas** de ambos.

## Stack

- Java 25 + Spring Boot 4.1.2
- Spring WebFlux (reactivo)
- PostgreSQL (R2DBC) para eventos, actividades y reservas
- MongoDB (reactivo) para reseñas
- JWT RS256 (JWKS de ms-01 o llave pública PEM)

## Requisitos

- Java 25
- Docker y Docker Compose

## Ejecución con Docker

```bash
cp .env.example .env
# Configura JWK_SET_URI (JWKS de ms-01) o JWT_PUBLIC_KEY_LOCATION (PEM)
docker compose up --build
```

Servicios:

- API: `http://localhost:8082`
- PostgreSQL: `localhost:5432`
- MongoDB: `localhost:27017`
- Swagger UI: `http://localhost:8082/swagger-ui.html`
- Health: `http://localhost:8082/actuator/health`

Swagger está desactivado por defecto; Docker Compose y `.env.example` lo activan para desarrollo. El detalle público solo devuelve ítems `PUBLISHED`; el staff autenticado puede ver cualquier estado.

Las migraciones viven en `src/main/resources/db/migration` y las aplica Flyway al arrancar (`V1` baseline, `V2` reservas simbólicas, `V3` índices y checks de integridad). `V2` archiva las filas originales en `reservations_legacy` y `reservation_items_legacy`, y mueve a `reservations_quarantine` las reservas que no se pueden representar: sin ítem, multi-ítem, ítem inexistente, inicio anterior a la reserva, total no divisible o `PENDING` sin cupo. No reescribe el `total`; solo elimina de la tabla viva los IDs capturados en esa ejecución. Hacer backup y revisar la cuarentena después de migrar.

Para reiniciar las bases desde cero:

```bash
docker compose down -v
```

## Ejecución local

```bash
export JWK_SET_URI="http://localhost:8081/.well-known/jwks.json"
export JWT_ISSUER="sm-xplore-ms-01"
export JWT_AUDIENCE="sm-xplore-services"
export R2DBC_URL="r2dbc:postgresql://localhost:5432/activities_events"
export MONGODB_URI="mongodb://localhost:27017/reviews"
./gradlew bootRun
```

`JWK_SET_URI` o `JWT_PUBLIC_KEY_LOCATION` es obligatorio: el servicio no arranca sin una fuente de llaves RS256. No se admite HS256.

Si no defines `R2DBC_URL` ni `FLYWAY_URL`, ambas conexiones se derivan de `POSTGRES_HOST`, `POSTGRES_PORT` y `POSTGRES_DB`; si las defines, deben apuntar a la misma base.

## Variables de entorno

| Variable | Default | Descripción |
|---|---|---|
| `SERVER_PORT` | `8082` | Puerto HTTP del servicio. |
| `APP_PORT` | `8082` | Puerto publicado por Docker Compose. |
| `R2DBC_URL` | `r2dbc:postgresql://localhost:5432/activities_events` | Conexión reactiva a PostgreSQL. |
| `POSTGRES_ROOT_USER` / `POSTGRES_ROOT_PASSWORD` | `postgres` / vacío | Superusuario; solo lo usa el init de Docker. |
| `POSTGRES_USER` / `POSTGRES_PASSWORD` | `ms03_app` / vacío | Usuario DML de la aplicación (sin DDL). |
| `FLYWAY_USER` / `FLYWAY_PASSWORD` | `ms03_flyway` / vacío | Usuario DDL de Flyway. |
| `MONGODB_URI` | `mongodb://localhost:27017/reviews` | Conexión a MongoDB. En Compose usa `reviews_app`. |
| `MONGO_ROOT_USER` / `MONGO_ROOT_PASSWORD` | `root` / vacío | Superusuario; solo init de Mongo. |
| `MONGO_APP_USER` / `MONGO_APP_PASSWORD` | `reviews_app` / vacío | Usuario `readWrite` solo sobre `reviews`. |
| `JWK_SET_URI` | vacío | URL del JWKS de ms-01. Obligatoria si no hay PEM; fuera de loopback debe ser `https://`. |
| `JWT_ALLOW_INSECURE_JWKS` | `false` | Solo desarrollo: acepta el JWKS por HTTP cuando el host no es loopback (por ejemplo `http://ms-01:8081` dentro del stack de Compose). En producción debe quedar en `false`. |
| `JWT_PUBLIC_KEY_LOCATION` | vacío | Ruta o URL de la llave pública PEM (local/tests). |
| `JWT_ISSUER` | vacío (obligatoria) | Issuer esperado en el token. |
| `JWT_AUDIENCE` | vacío (obligatoria) | Audiencia esperada en el token. |
| `JWT_ROLE_CLAIM` | `role` | Claim que contiene el rol. |
| `JWT_NAME_CLAIM` | `name` | Claim que contiene el nombre. |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:3000,http://localhost:4200` | Orígenes permitidos. |
| `MONGO_AUTO_INDEX_CREATION` | `true` | Crea índices al arrancar. |
| `FLYWAY_URL` | `jdbc:postgresql://localhost:5432/activities_events` | URL JDBC que usa Flyway para migrar el esquema. |
| `FLYWAY_ENABLED` | `true` | Activa o desactiva las migraciones al arrancar. |
| `MS04_BASE_URL` | `http://localhost:8084` | Base de ms-04 para validar el sitio al crear eventos. En Compose: `http://host.docker.internal:8084`. |
| `MS04_TIMEOUT` | `3s` | Timeout de la llamada a ms-04. |
| `KAFKA_BOOTSTRAP_SERVERS` | `localhost:9092` | Brokers para el relay del outbox. |
| `OUTBOX_ENABLED` | `false` | Activa el relay del outbox a Kafka. |
| `OUTBOX_POLL_INTERVAL_MS` | `5000` | Frecuencia del relay. |
| `RESERVATION_AUTO_CLOSE_CRON` | `0 15 3 * * *` | Cron del job que cierra reservas vencidas. |
| `RESERVATION_AUTO_CLOSE_GRACE` | `P1D` | Tiempo de gracia tras el fin del ítem antes de cerrar la reserva. |
| `DEMAND_ZONE_ID` | `America/Bogota` | Zona horaria usada para agrupar la serie temporal y los días de la semana. |
| `REVIEW_AI_ENABLED` | `false` | Activa el filtro automático de reseñas (mismo contrato que ms-04). |
| `LITTLELLM_URL` | vacío | Base URL del servidor littleLLM compatible con OpenAI; obligatoria si el filtro se activa. |
| `LITTLELLM_API_KEY` | vacío | Llave enviada al LLM. |
| `LITTLELLM_API_KEY_HEADER` | `Authorization` | Cabecera de la llave. |
| `LITTLELLM_API_KEY_PREFIX` | `Bearer` | Prefijo de la llave. |
| `LITTLELLM_MODEL` | `littlellm` | Modelo solicitado. |
| `LITTLELLM_CHAT_PATH` | `/v1/chat/completions` | Ruta de chat completions. |
| `REVIEW_AI_TIMEOUT` | `PT5S` | Timeout de la llamada al filtro. |
| `REVIEW_AI_THRESHOLD` | `0.7` | Score mínimo para retener una reseña marcada. |
| `REVIEW_AI_TEMPERATURE` | `0.0` | Temperatura del modelo (0–2). |
| `REVIEW_AI_ACTION` | `PENDING_REVIEW` | `PENDING_REVIEW` deja la cola; `HIDE` oculta de inmediato. |
| `REVIEW_AI_FAIL_OPEN` | `true` | Si el filtro cae, publica la reseña; con `false` la creación responde 503. |
| `REVIEW_AI_JSON_RESPONSE_FORMAT` | `false` | Pide `response_format=json_object` al modelo. |

Docker Compose **no arranca** si faltan `POSTGRES_ROOT_PASSWORD`, `POSTGRES_PASSWORD`, `FLYWAY_PASSWORD`, `MONGO_ROOT_PASSWORD`, `MONGO_APP_PASSWORD`, `JWT_ISSUER` o `JWT_AUDIENCE`. Copia `.env.example`, completa los valores y usa un volumen limpio (`docker compose down -v`) la primera vez, porque los scripts de init solo corren en una base nueva.

## Alcance

En `prod`, este servicio valida el claim `ver` contra ms-01 (`MS01_BASE_URL`) y exige
`mfa=true` a los administradores. La validación es fail-closed: si ms-01 no responde, las
peticiones se rechazan. En local/tests queda desactivada con `AUTH_VERSION_ENABLED=false`.


- Actividades y eventos (registro, consulta, estado, cupos).
- Reservas de actividades y eventos.
- Reseñas de actividades y eventos.

Las reseñas viven en MongoDB; el resto en PostgreSQL. Neo4j para preferencias queda diferido.

Los listados (`/events`, `/events/search`, `/activities`, `/activities/search`, `/activities/site/{id}` y `GET /reservations?status=`) aceptan `page` (>= 0) y `size` (1..100) y devuelven `PageResponse`: `content`, `page`, `size`, `total`, `totalPages`. Los errores usan RFC 9457 (`application/problem+json`).

Todos esos listados paginan **en la base de datos**: aplican `LIMIT`/`OFFSET` y piden el `COUNT` del filtro, así que la página 2 y el `total` son reales y no dependen de cuántas filas quepan en memoria. El adaptador arma el `PageResult` del dominio con las dos consultas en paralelo.

Los roles del JWT son los de ms-01, en inglés: `TOURIST`, `GUIDE`, `SERVICE_PROVIDER`, `DESTINATION_MANAGER` y `ADMIN`. Los códigos antiguos en español (`TURISTA`, `GUIA`, `PRESTADORA_SERVICIOS`, `GESTORA_DESTINOS`) se aceptan solo como alias de lectura para tokens ya emitidos; ms-03 nunca los emite.

## Endpoints

| Método | Ruta | Acceso |
|---|---|---|
| GET | `/api/v1/events` | Público |
| GET | `/api/v1/events/{eventId}` | Público |
| GET | `/api/v1/events/{eventId}/exists` | Público (200/404) |
| GET | `/api/v1/events/{eventId}/availability` | Público |
| GET | `/api/v1/events/search?name=` | Público |
| POST | `/api/v1/events` | `ADMIN`, `GESTORA_DESTINOS` |

Al crear un evento, ms-03 valida el sitio con `GET /api/v1/sites/{siteId}` de ms-04. `siteName` y `managerId` se copian del sitio; el `managerId` del body se ignora y una `GESTORA_DESTINOS` solo puede crear eventos en sus propios sitios. Si ms-04 no responde, la creación falla.
| PATCH | `/api/v1/events/{eventId}/status` | `ADMIN`, `GESTORA_DESTINOS` |
| PATCH | `/api/v1/events/{eventId}/attendance` | `ADMIN`, `GESTORA_DESTINOS` |
| GET | `/api/v1/activities` | Público |
| GET | `/api/v1/activities/{activityId}` | Público |
| GET | `/api/v1/activities/{activityId}/availability` | Público |
| GET | `/api/v1/activities/search?category=` | Público |
| GET | `/api/v1/activities/site/{siteId}` | Público |
| POST | `/api/v1/activities` | `ADMIN`, `GESTORA_DESTINOS`, `GUIA` |
| PATCH | `/api/v1/activities/{activityId}` | `ADMIN`, `GESTORA_DESTINOS`, `GUIA` |
| PATCH | `/api/v1/activities/{activityId}/availability` | `ADMIN`, `GESTORA_DESTINOS`, `GUIA` |
| PATCH | `/api/v1/activities/{activityId}/slots` | `ADMIN`, `GESTORA_DESTINOS`, `GUIA` |
| PATCH | `/api/v1/activities/{activityId}/status` | `ADMIN`, `GESTORA_DESTINOS`, `GUIA` |
| POST | `/api/v1/activities/{activityId}/cancel` | `ADMIN`, `GESTORA_DESTINOS`, `GUIA` |

Los errores usan RFC 9457 (`application/problem+json`): `type`, `title`, `status`, `detail` e
`instance`.

## Limitaciones conocidas

- No hay bloqueo optimista en las escrituras (ver `shortcut:` en los adaptadores).
- La búsqueda por nombre usa `ILIKE` sin índice `pg_trgm`.
- La cobertura de integración es parcial: cierre automático de reservas y migraciones Flyway.
- Neo4j (preferencias) queda diferido.

## Reservas

| Método | Ruta | Acceso |
|---|---|---|
| POST | `/api/v1/reservations` | Autenticado (turista crea la suya; admin puede crear para otro) |
| GET | `/api/v1/reservations/{id}` | Dueño, guía de la reserva o staff |
| GET | `/api/v1/reservations?status=` | `ADMIN` (todas) o `GESTORA_DESTINOS` (solo sus sitios) |
| GET | `/api/v1/reservations/tourists/{touristId}` | El propio turista o staff |
| GET | `/api/v1/reservations/guides/{guideId}` | El propio guía o staff |
| GET | `/api/v1/reservations/by-item?itemType=&itemId=` | Guía dueño del ítem o staff (eventos: staff) |
| PATCH | `/api/v1/reservations/{id}/cancel` | Dueño (hasta 24 h antes) o staff (siempre) |
| PATCH | `/api/v1/reservations/{id}/attendance` | Guía de la reserva o staff |
| PATCH | `/api/v1/reservations/{id}/no-show` | Guía de la reserva o staff |

Reglas: la reserva es **simbólica** y nace `CONFIRMED`; aparta cupo y el pago se resuelve fuera de la plataforma. Una reserva tiene **un solo ítem** (`itemType` + `itemId`), el precio sale del catálogo y se guarda como `unitPrice`; el total es `unitPrice × numberOfPeople`. Solo se reservan ítems publicados, vigentes y con cupo. El servidor fija `bookedFor` con el inicio del ítem e ignora el valor del cliente. En filas migradas por V2, si el inicio del ítem es anterior a `booked_at`, se conserva el `bookedFor` legacy para no violar el CHECK; solo las reservas nuevas quedan alineadas con el catálogo. Máximo **5 reservas activas** por turista. Estados: `CONFIRMED`, `CANCELLED`, `NO_SHOW`, `COMPLETED`. No existe confirmación manual ni tabla de ítems.

**Idempotencia:** `POST /api/v1/reservations` acepta el header opcional `Idempotency-Key` (1–64 caracteres). Si la misma clave se repite para el mismo turista, la respuesta es la reserva creada en el primer intento y no se consume cupo otra vez. La clave se guarda en `reservations` y el índice único parcial `(tourist_id, idempotency_key)` cubre la carrera concurrente. Reutilizar una clave con otro ítem o con otro número de personas devuelve la reserva original: la clave identifica la operación completa, no el ítem.

**Privacidad:** los listados (`?status=`, `/tourists/{id}`, `/guides/{id}`, `/by-item`) usan un DTO sin `notes`; el texto libre solo se devuelve en `GET /api/v1/reservations/{id}`. Una `GESTORA_DESTINOS` ya no puede enumerar reservas ajenas por esos endpoints: solo `ADMIN` o el propio turista/guía.

**Outbox:** las transiciones de reserva (`CONFIRMED`, `CANCELLED`, `COMPLETED`, `NO_SHOW`) escriben `bookings.reservation.changed` v2 en `outbox_events` dentro de la misma transacción. El relay está desactivado por defecto; con `OUTBOX_ENABLED=true` publica en Kafka usando `KAFKA_BOOTSTRAP_SERVERS`. La v2 agrega `itemName`, `guideId`, `managerId` y `NO_SHOW`, y ms-04 la consume para notificar al turista, al guía y a la gestora. El nombre del ítem se copia a la reserva al crearla (`item_name`).

**Alcance por gestora:** `GET /api/v1/reservations?status=` resuelve los sitios de la gestora con `GET /api/v1/sites?managerId=...` de ms-04 (reenviando el `Authorization` del usuario), luego sus eventos/actividades y finalmente sus reservas. Si no tiene sitios, devuelve una lista vacía. `ADMIN` sigue viendo todas.

**Outbox de catálogo:** crear o modificar una actividad o un evento escribe `activities.activity.changed` v2 o `activities.event.changed` v2 en el outbox. La v2 usa el nombre de la categoría que el usuario escribe (no un `categoryId`), no publica coordenadas —son del sitio en ms-04— y admite los estados reales (`PAUSED`/`CANCELLED` en actividades, `POSTPONED` en eventos). El envío es best effort: si el encolado falla, la escritura ya se guardó, se registra el error y la petición responde bien.

**Cierre automático:** un job diario (03:15, `America/Bogota`) marca `NO_SHOW` las reservas `CONFIRMED` cuyo ítem `PUBLISHED` terminó hace más de `RESERVATION_AUTO_CLOSE_GRACE` (24 h por defecto). Las reservas de ítems `CANCELLED` pasan a `CANCELLED`. Los ítems pausados o postergados no se tocan; cada cierre publica `bookings.reservation.changed` para notificar al turista y al guía/gestora. Un `NO_SHOW` se puede corregir a `COMPLETED` registrando la asistencia.

## Indicadores de demanda

| Método | Ruta | Acceso |
|---|---|---|
| GET | `/api/v1/demand-indicators?from=&to=&zoneId=&siteId=&granularity=` | `GUIDE`, `DESTINATION_MANAGER`, `ADMIN` |
| GET | `/api/v1/demand-indicators/report?...` | Igual que el anterior; descarga CSV |
| GET | `/api/v1/demand-indicators/report.pdf?...` | Igual que el anterior; descarga PDF |

- **Guía (US8):** ve solo sus actividades, con reservas, personas, ingresos, ocupación,
  actividades más solicitadas y comparación contra el periodo anterior. No se consultan servicios.
- **Gestora (US10/US11):** ve las actividades y eventos de sus sitios, agrupados por zona y sitio,
  con afluencia esperada vs. reservada de cada evento y el reporte CSV/PDF del periodo. Además, ms-03
  compone la demanda de **servicios** llamando a `GET /api/v1/demand-indicators` de ms-04 con el
  mismo filtro y el token de la gestora: la respuesta es una sola vista del destino. Si ms-04 no
  responde, se devuelven actividades/eventos con `partial=true` y `services=[]`.
- **Admin:** visión global o filtrada por `zoneId`/`siteId`.
- `from` es inclusivo y `to` exclusivo; por defecto, últimos 30 días y máximo 366. `granularity` es
  `DAY` o `MONTH`. Los importes son simbólicos (no representan recaudo real).
- La ocupación de una actividad/evento es `personas reservadas / maxCapacity` y se limita a `1.0`.
  La capacidad de un sitio o zona suma la capacidad de sus actividades/eventos en el alcance.
- Los nombres y zonas de los sitios se resuelven contra ms-04; si el catálogo no responde, se usa el
  snapshot del evento o el `siteId` como respaldo y la vista sigue siendo útil.

## Reseñas

| Método | Ruta | Acceso |
|---|---|---|
| POST | `/api/v1/reviews` | Autenticado; no exige reserva |
| GET | `/api/v1/reviews/events/{eventId}` | Público |
| GET | `/api/v1/reviews/activities/{activityId}` | Público |
| GET | `/api/v1/reviews/events/{eventId}/metrics` | Público |
| GET | `/api/v1/reviews/activities/{activityId}/metrics` | Público |
| POST | `/api/v1/reviews/{reviewId}/report` | Autenticado; un reporte por usuario y reseña |
| POST | `/api/v1/reviews/{reviewId}/response` | Entidad a cargo (guía de la actividad o gestor del evento) o ADMIN |
| GET | `/api/v1/admin/reviews?status=PENDING_REVIEW` | `ADMIN`; cola global de reseñas retenidas |
| PATCH | `/api/v1/reviews/{reviewId}/status` | `ADMIN`; publica, oculta, elimina o devuelve a la cola |
| GET | `/api/v1/review-reports?status=PENDING` | `ADMIN` ve todos; `DESTINATION_MANAGER` solo los de sus sitios |
| PATCH | `/api/v1/review-reports/{reportId}` | `ADMIN` o la entidad a cargo del ítem reseñado |

- Dos colecciones Mongo: `event_reviews` y `activity_reviews`.
- `user` se guarda anidado con solo `{id, name}`; sale del JWT, no del body.
- Una reseña por usuario e ítem. Sin “likes / ¿te resultó útil?”.
- La respuesta solo la puede escribir la entidad a cargo: guía dueño de la actividad; para eventos, la **gestora del sitio** (`managerId` guardado en el evento) o `ADMIN`.
- La reseña expone `status` (`PUBLISHED`, `PENDING_REVIEW`, `HIDDEN`, `DELETED`), `ai`, `aiRetained` y `updatedAt`. Los listados públicos y las métricas solo cuentan `PUBLISHED`; los documentos anteriores sin estado se leen como publicados.
- **Filtro de IA:** con `REVIEW_AI_ENABLED=true` cada reseña con comentario pasa por el mismo littleLLM de ms-04. Si el score supera `REVIEW_AI_THRESHOLD`, queda `PENDING_REVIEW` (o `HIDDEN` con `REVIEW_AI_ACTION=HIDE`) y aparece en la cola administrativa. La decisión se guarda en `ai` para auditoría. Sin filtro, la reseña nace `PUBLISHED`.
- **Moderación:** solo `ADMIN` cambia el estado. Cada cambio efectivo actualiza `updatedAt` y publica `reviews.review.changed`, para que ms-05 ajuste su agregado de reputación.
- **Outbox de reseñas:** crear, responder o moderar una reseña escribe `reviews.review.changed` v1 en el outbox con `reviewId`, `touristId`, `itemType`, `itemId`, `rating`, `status` y `updatedAt`. El comentario libre **no** viaja en el evento.
- La reseña vive en MongoDB y el outbox en PostgreSQL: no comparten transacción. La reseña se guarda primero; si el evento no se puede encolar se registra el error y la reseña se devuelve igual, porque la escritura del usuario ya ocurrió. Ese evento no se reintenta.
- **Reportes acotados:** `GET /api/v1/review-reports` resuelve los sitios de la gestora con `GET /api/v1/sites?managerId=` de ms-04, luego sus eventos, las reseñas de esos eventos y por último los reportes. Un reporte de una actividad no aparece en el listado de una gestora: las actividades son del guía. `ADMIN` conserva el listado global.
- **Resolver un reporte** exige ser `ADMIN` o la entidad a cargo del ítem reseñado (guía de la actividad, gestora del evento), con la misma regla que la respuesta a la reseña. Un reporte `RESOLVED` (aceptado) oculta la reseña y publica el evento; `DISMISSED` la deja publicada.

## Snapshot de sitio en eventos

`Evento` guarda `siteId`, `siteName` y `managerId`. Al crear un evento con rol `DESTINATION_MANAGER`, `managerId` se toma del `sub` del JWT (no del body).

Ese snapshot se usa como referencia, pero **responder una reseña lo revalida**: se consulta `GET /api/v1/sites/{siteId}` de ms-04 con el token del usuario y manda la gestora actual, así que una gestora reasignada pierde el derecho de inmediato. Si ms-04 no responde, o el sitio ya no existe, se usa el snapshot y se registra un `WARN`: la alternativa es rechazar todas las respuestas cada vez que el catálogo esté caído.

**Kafka:** ms-03 usa `spring-boot-starter-kafka`. Sin el starter no hay `KafkaTemplate` ni contenedor de listeners: el relay del outbox y el consumidor de sitios quedan inertes aunque `KAFKA_BOOTSTRAP_SERVERS` esté definido.

**Refresco por Kafka (opcional):** con `SITE_EVENTS_ENABLED=true`, un consumidor de `catalog.site.changed` v2 (ms-04) actualiza `sitio_nombre` y `manager_id` de todos los eventos de ese sitio. Solo aplica el evento si su `updatedAt` es más nuevo que el último aplicado (columna `sitio_actualizado_en`), así que un mensaje reordenado no devuelve el sitio a la gestora anterior. Los mensajes v1 se ignoran: no traen `managerId`.

## Pruebas

```bash
./gradlew test
./gradlew build
```

Las pruebas de integración usan Testcontainers (PostgreSQL y MongoDB) y requieren Docker. `ApplicationContextTest` levanta el contexto completo, ejecuta Flyway y valida el wiring RS256; los tests sin Docker se omiten.
