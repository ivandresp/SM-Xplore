# ms-04 — Sitios, Servicios, Reservas y Notificaciones

Microservicio de catálogo turístico de SM-Xplore. Gestiona:

- **Zonas** turísticas en PostgreSQL.
- **Sitios** turísticos y **servicios** de prestadoras en MongoDB.
- **Categorías** del catálogo en MongoDB.
- **Reseñas** de sitios y servicios en MongoDB.
- **Moderación automática de reseñas** con un LLM local compatible con OpenAI (filtro de
  review bombing) y cola administrativa de reseñas retenidas.
- **Indicadores de demanda** por servicio, sitio y zona para prestadoras y gestoras de
  destinos, con comparación contra el periodo anterior y exportación CSV.
- **Reservas simbólicas de servicios** en PostgreSQL: apartan cupos para uno o varios servicios
  de una sola prestadora, se confirman automáticamente y no procesan pagos.
- **Bandeja de notificaciones** en MongoDB alimentada por el evento de reservas.
- Publicación de eventos de catálogo, reseñas y reservas por **Kafka** con patrón
  **transactional outbox**.
- Retención y reproceso operativo de los outboxes.

## Estado

Primera entrega implementada:

- API reactiva con WebFlux.
- Arquitectura hexagonal.
- MongoDB reactivo con transacciones y outbox.
- PostgreSQL reactivo con R2DBC para zonas y reservas.
- Reservas simbólicas con control transaccional de cupos y bloqueo por servicio.
- Límites por turista, rate limit de creación y ventana máxima de anticipación.
- Auto-completado de reservas pasadas y bandeja de notificaciones por usuario.
- Seguridad RS256/JWKS o llave pública PEM.
- CRUD y consultas de categorías, zonas, sitios, servicios y reseñas.
- Filtros por zona, categoría, sitio, prestadora, estado, tipo y texto.
- Reseñas con respuesta del propietario y moderación administrativa.
- Filtro de IA para review bombing con cola `PENDING_REVIEW` y política fail-open/fail-closed.
- Indicadores de demanda, ocupación y estacionalidad para prestadoras y gestoras.
- Reportes de reseñas con resolución administrativa y métricas de reputación por sitio o servicio.
- Eventos Kafka versionados según `contracts/events/`.
- Outbox con relay, reintentos y métricas, tanto en MongoDB como en PostgreSQL.
- Tests de contrato y tests de integración con Testcontainers.

## Stack

- Java 25
- Spring Boot 4.1.2
- Gradle 9.8.2
- WebFlux + Reactor
- Spring Security + OAuth2 Resource Server
- Spring Data MongoDB reactivo
- Spring Data R2DBC + PostgreSQL
- Spring Kafka
- Lombok
- Testcontainers 2.x

## Arquitectura

```text
src/main/java/com/smxplore/sitesandservices
├── domain
│   ├── model
│   │   ├── catalog
│   │   ├── common
│   │   ├── event
│   │   ├── indicator
│   │   ├── notification
│   │   ├── reservation
│   │   ├── review
│   │   ├── service
│   │   ├── site
│   │   └── zone
│   ├── exception
│   └── ports
│       ├── in
│       └── out
├── application
│   ├── catalog
│   ├── event
│   ├── indicator
│   ├── notification
│   ├── reservation
│   ├── review
│   ├── service
│   ├── site
│   └── zone
└── infrastructure
    ├── adapters/in/kafka
    ├── adapters/in/scheduling
    ├── adapters/in/web
    ├── adapters/out/kafka
    ├── adapters/out/mongo
    ├── adapters/out/outbox
    ├── adapters/out/postgres
    └── config
```

- El dominio no depende de Spring, Mongo, R2DBC ni Kafka.
- Las entidades usan `@Builder` y `@Builder.Default` para estados/listas por defecto.
- Cada puerto de entrada es un caso de uso con un único `handle` y una única implementación.
- Los controllers mapean los DTO de request a entidades de dominio; los casos de uso las
  enriquecen, validan y persisten.
- Las validaciones viven en los modelos y delegan en métodos privados.
- Los DTO están agrupados por recurso en `dto/category`, `dto/zone`, `dto/site`, `dto/service`,
  `dto/review`, `dto/reservation`, `dto/notification` y `dto/common`.
- Los casos de uso se cablean como beans en `BeanConfiguration`.
- Los adaptadores de persistencia son explícitos: `ReactiveMongoTemplate` y `DatabaseClient`.
- Las escrituras de sitios, servicios, categorías y reseñas guardan entidad + evento de outbox
  dentro de una transacción de MongoDB.
- Las reservas guardan reserva + ítems + evento de outbox dentro de una transacción PostgreSQL,
  con `pg_advisory_xact_lock` por servicio y por turista para serializar cupos y cuotas.
- El consumidor de notificaciones lee `bookings.reservation.changed` v1/v2 y guarda una
  notificación idempotente por destinatario (turista, guía, gestora o prestadora).

## Bases de datos

### MongoDB

Base `smxplore` (configurable con `MONGO_DATABASE`). Colecciones:

- `categories`
- `sites`
- `services`
- `reviews`
- `review_reports`
- `notifications`
- `notification_contacts`
- `outbox_event`

MongoDB debe ejecutarse como **replica set** para soportar transacciones. El `docker-compose.yml`
levanta un nodo con replica set `rs0` y un contenedor de inicialización.

### PostgreSQL

Base `smxplore` (configurable con `POSTGRES_DB`). Tablas:

- `zones`
- `reservations`
- `reservation_items`
- `reservation_outbox`

El esquema se versiona con **Flyway** en `src/main/resources/db/migration`: `V1` crea el esquema
base y `V2` normaliza las reservas heredadas y publica sus eventos. Flyway abre su propia conexión
JDBC (`spring.flyway.url`, variable `POSTGRES_JDBC_URL`) porque la aplicación trabaja con R2DBC; en
bases creadas con el antiguo `schema.sql` la primera migración se baselinea
(`FLYWAY_BASELINE_ON_MIGRATE`) y todas las sentencias son idempotentes (`IF NOT EXISTS`).
`reservations` y `reservation_items` implementan las tablas `Reserva` y `ReservaItem` del modelo
relacional físico.

Las reservas son **simbólicas**: no hay pasarela de pagos ni campos de pago. Una reserva aparta
`people` cupos por cada ítem de servicio y conserva una foto del nombre, prestadora, tipo y precio
del servicio. El pago lo gestiona cada prestadora por fuera de SM-Xplore.

Reglas:

- Una reserva puede tener **máximo 10 ítems** y todos deben pertenecer a **una sola prestadora**.
  La regla se aplica al crear; las reservas históricas multi-prestadora se mantienen legibles.
- Toda reserva se crea directamente en `CONFIRMED`: no requiere aceptación manual. `PENDING` se
  conserva solo por compatibilidad con el contrato compartido; la migración `V2` normaliza las filas
  `PENDING` heredadas y emite un `bookings.reservation.changed` por cada una a través del outbox, de
  modo que ms-05 y la bandeja de notificaciones se reconcilian sin intervención manual.
- No hay expiración automática de reservas `CONFIRMED`: el cupo se libera cuando el turista o la
  prestadora la cancelan, o cuando el job de auto-completado la pasa a `COMPLETED` después de la
  fecha reservada.
- `CONFIRMED` ocupa capacidad; `CANCELLED` y `COMPLETED` la liberan.
- Un turista puede mantener como máximo `max-active-per-tourist` reservas confirmadas futuras
  (5 por defecto) y crear como máximo `max-creations-per-minute` reservas por minuto (10 por
  defecto). Superar cualquiera devuelve `429`.
- Una reserva no puede crearse con más de `max-advance-days` de anticipación (365 por defecto).
- La capacidad máxima del servicio (`Service.maxCapacity`) se evalúa **por día local**, usando
  `app.bookings.zone-id` (por defecto `America/Bogota`).
- Cada franja de `Service.availability` puede declarar `capacity` (cupo del turno). Si la reserva cae
  en una franja con cupo, se aplican los dos topes —día y turno— y gana el más restrictivo; la
  respuesta `409` indica `in the selected time slot` cuando el turno es el que limita.
- La fecha/hora reservada debe caer dentro de alguna franja de `Service.availability`. Si el
  servicio no tiene franjas configuradas, se permite cualquier horario.
- El servicio debe estar `PUBLISHED` y su zona no puede estar `INACTIVE`.
- El **sitio** del servicio se consulta en el momento de reservar: si ya no existe la reserva falla
  con `SITE_NOT_AVAILABLE` y si está despublicado con `SITE_NOT_PUBLISHED`, aunque el servicio siga
  `PUBLISHED`. Al renombrar un sitio se refresca el snapshot `site.name` de todos sus servicios.
- `POST /api/v1/reservations` requiere `Idempotency-Key`: el mismo key con el mismo payload
  devuelve la reserva existente; el mismo key con otro payload responde `409`.
- Los textos que viajan a PostgreSQL respetan el ancho de su columna: nombre de zona ≤100, tipo de
  zona ≤50, nombre de servicio y de prestadora ≤150, tipo de servicio ≤50, nombre del turista ≤150,
  notas ≤1000 y motivo de cancelación ≤500. Los DTO (`@Size`) y el dominio aplican el mismo límite,
  así que una reserva no puede fallar después con `value too long for type character varying`.
- Cualquier usuario autenticado puede reportar una reseña ajena (nunca la propia ni una borrada) una
  sola vez; el reporte nace `OPEN` con un motivo (`SPAM`, `OFFENSIVE`, `FALSE_INFORMATION`,
  `PERSONAL_DATA`, `OTHER`). Al `ACCEPTED` la reseña pasa a `HIDDEN` y se publica
  `reviews.review.changed`; `DISMISSED` la deja intacta. Un reporte resuelto no se puede volver a
  resolver.
- `GET /api/v1/reviews/reputation` agrega solo reseñas `PUBLISHED`: promedio, conteo, respuestas,
  tasa de respuesta, ocultas, distribución 1–5 y fecha de la última reseña.

## API

Todas las rutas requieren JWT salvo `health`, `info`, `/actuator/metrics`,
`/actuator/prometheus` y Swagger. En producción, los endpoints de management deben aislarse en
una red o puerto privado.

| Método | Ruta | Roles | Descripción |
|---|---|---|---|
| POST | `/api/v1/categories` | `ADMIN` | Crear categoría de la taxonomía global |
| PUT | `/api/v1/categories/{id}` | `ADMIN` | Actualizar categoría de la taxonomía global |
| GET | `/api/v1/categories` | autenticado | Listar/filtrar categorías |
| POST | `/api/v1/zones` | `DESTINATION_MANAGER`, `ADMIN` | Crear zona |
| PUT | `/api/v1/zones/{id}` | `DESTINATION_MANAGER`, `ADMIN` | Actualizar zona |
| PATCH | `/api/v1/zones/{id}/status` | `DESTINATION_MANAGER`, `ADMIN` | Cambiar estado/congestión |
| GET | `/api/v1/zones` | autenticado | Listar/filtrar zonas |
| POST | `/api/v1/sites` | `DESTINATION_MANAGER`, `ADMIN` | Crear sitio |
| PUT | `/api/v1/sites/{id}` | `DESTINATION_MANAGER`, `ADMIN` | Actualizar sitio |
| PATCH | `/api/v1/sites/{id}/status` | `DESTINATION_MANAGER`, `ADMIN` | Cambiar estado |
| PATCH | `/api/v1/sites/{id}/zone` | `DESTINATION_MANAGER`, `ADMIN` | Asignar zona |
| GET | `/api/v1/sites` | público / autenticado | Listar sitios; el anónimo solo ve `PUBLISHED` |
| POST | `/api/v1/services` | `SERVICE_PROVIDER`, `ADMIN` | Crear servicio |
| PUT | `/api/v1/services/{id}` | `SERVICE_PROVIDER`, `ADMIN` | Actualizar servicio |
| PATCH | `/api/v1/services/{id}/status` | `SERVICE_PROVIDER`, `ADMIN` | Cambiar estado |
| GET | `/api/v1/services` | público / autenticado | Listar servicios; el anónimo solo ve `PUBLISHED` |
| POST | `/api/v1/reviews` | `TOURIST` | Crear reseña de sitio o servicio |
| POST | `/api/v1/reviews/{id}/response` | `SERVICE_PROVIDER`, `DESTINATION_MANAGER`, `ADMIN` | Responder reseña |
| PATCH | `/api/v1/reviews/{id}/status` | `ADMIN` | Moderar reseña |
| GET | `/api/v1/admin/reviews/ai-flagged?page=&size=` | `ADMIN` | Reseñas retenidas por el filtro de IA |
| GET | `/api/v1/reviews/summary?itemType=&itemId=` | autenticado | Promedio y número de reseñas |
| GET | `/api/v1/reviews/reputation?itemType=&itemId=` | autenticado | Métricas de reputación (distribución, respuestas, ocultas) |
| POST | `/api/v1/reviews/{id}/reports` | autenticado (no el autor) | Reportar una reseña |
| GET | `/api/v1/reviews/reports?status=&reviewId=&page=&size=` | `ADMIN` | Listar reportes de reseñas |
| PATCH | `/api/v1/reviews/reports/{id}` | `ADMIN` | Aceptar o descartar un reporte |
| GET | `/api/v1/reviews` | autenticado | Listar reseñas |
| POST | `/api/v1/reservations` | `TOURIST` | Crear reserva confirmada de hasta 10 servicios de una prestadora |
| GET | `/api/v1/reservations/availability?serviceId=&bookedFor=&people=` | autenticado | Consultar cupos disponibles |
| GET | `/api/v1/reservations/{id}` | turista dueño, prestadora o `ADMIN` | Ver reserva |
| GET | `/api/v1/reservations` | `TOURIST`, `SERVICE_PROVIDER`, `ADMIN` | Listar reservas según el actor |
| PATCH | `/api/v1/reservations/{id}/status` | turista dueño (cancelar), prestadora o `ADMIN` | Cancelar o completar |
| GET | `/api/v1/demand-indicators?from=&to=&zoneId=&siteId=&granularity=` | `SERVICE_PROVIDER`, `DESTINATION_MANAGER`, `ADMIN` | Indicadores de demanda según el alcance del actor |
| GET | `/api/v1/demand-indicators/report?from=&to=&zoneId=&siteId=&granularity=` | `SERVICE_PROVIDER`, `DESTINATION_MANAGER`, `ADMIN` | Reporte CSV de indicadores |
| GET | `/api/v1/notifications?unreadOnly=&page=&size=` | autenticado | Bandeja del usuario actual |
| PATCH | `/api/v1/notifications/{id}/read` | destinatario o `ADMIN` | Marcar notificación como leída |
| PUT | `/api/v1/admin/notification-contacts/{userId}` | `ADMIN` | Alta o cambio del correo de un destinatario |
| DELETE | `/api/v1/admin/notification-contacts/{userId}` | `ADMIN` | Baja del correo de un destinatario |
| POST | `/api/v1/admin/outbox/reprocess` | `ADMIN` | Resetear eventos `FAILED` a `PENDING` |
| GET | `/api/v1/admin/catalog/export?entity=&updatedSince=&page=&size=` | `ADMIN` | Feed de reindexado con el payload de los eventos de catálogo |

Filtros disponibles:

- Sitios: `categoryId`, `zoneId`, `status`, `search`, `managerId`, `page`, `size`.
  `managerId` exige `ADMIN` o la propia `DESTINATION_MANAGER`; cualquier otro valor devuelve `403`.
  Las peticiones anónimas solo ven sitios `PUBLISHED`; la gestora dueña o el `ADMIN` pueden filtrar
  por otros estados.
- Servicios: `categoryId`, `zoneId`, `siteId`, `providerId`, `status`, `type`, `search`, `page`, `size`.
  Las peticiones anónimas solo ven servicios `PUBLISHED`; la prestadora dueña o el `ADMIN` pueden
  filtrar por otros estados.
- Zonas: `status`, `type`, `search`, `page`, `size`.
- Reseñas: `itemType`, `itemId`, `touristId`, `status`, `page`, `size`.
- Reportes: `status`, `reviewId`, `page`, `size`.
- Reservas: `touristId`, `providerId`, `serviceId`, `status`, `from`, `to`, `page`, `size`.
  El turista solo ve las suyas y la prestadora solo las que incluyen sus servicios.
- Notificaciones: `unreadOnly`, `page`, `size`; siempre se limita al usuario autenticado.
- Exportación de catálogo: `entity` (`CATEGORIES`, `SITES`, `SERVICES`, obligatorio), `updatedSince` (ISO-8601, opcional), `page`, `size` (máximo 200).

El feed `GET /api/v1/admin/catalog/export` existe para que ms-05 reconstruya su proyección desde cero:
devuelve páginas del catálogo con **el mismo `eventType`, `eventVersion`, `aggregateId` y `payload`**
que publican los eventos de Kafka, así que el consumidor reutiliza el mismo código de proyección y la
misma validación de versión. `eventVersion` sale de `EventVersions` (`catalog.site.changed` es v2 por el
`managerId`; el resto son v1). Cuando `hasNext` es `false`, ms-05 pasa a consumir los eventos
incrementales.

### Moderación automática de reseñas

Cada reseña con comentario pasa por un filtro de IA antes de guardarse. El filtro llama a un servidor
littleLLM compatible con `POST /v1/chat/completions` y espera un veredicto JSON con `flagged`,
`score` (0–1), `categories` y `reason`. El texto analizado es solo el tipo de destino, la calificación
y el comentario público; no se envían datos del turista. El resultado se conserva en el documento
`reviews` (`ai` + `aiRetained`) para que el administrador pueda auditarlo.

- Si `score >= REVIEW_AI_THRESHOLD` y el modelo marca la reseña, se aplica
  `REVIEW_AI_ACTION=PENDING_REVIEW` (estado `PENDING_REVIEW`, visible solo para administración)
  o `HIDE` (estado `HIDDEN`).
- `REVIEW_AI_FAIL_OPEN=true` (default) guarda la reseña visible cuando el LLM no responde; con
  `false` la creación falla con `503 Review filter unavailable`.
- `GET /api/v1/admin/reviews/ai-flagged` lista las reseñas retenidas y `PATCH
  /api/v1/reviews/{id}/status` permite publicarlas o mantenerlas ocultas. Ambas respuestas usan
  `AdminReviewResponse` y son las únicas que devuelven el veredicto de la IA: `ReviewResponse`
  (catálogo público) ya no lleva `ai`, para que un turista no pueda calibrar el filtro.
- El evento `reviews.review.changed` incluye el nuevo estado `PENDING_REVIEW`; los consumidores que
  solo cuentan reseñas publicadas lo ignoran, igual que `HIDDEN`.

### Indicadores de demanda

`GET /api/v1/demand-indicators` calcula, a partir de las reservas de servicios, un resumen y un
desglose por zona, sitio y servicio. El alcance depende del rol:

- `SERVICE_PROVIDER`: solo sus propios servicios.
- `DESTINATION_MANAGER`: solo sus zonas y sitios; si envía `zoneId` o `siteId` de otro gestor
  recibe `403`.
- `ADMIN`: visión global; `zoneId`/`siteId` actúan como filtro.

Los guías **no** tienen servicios en ms-04: su oferta son actividades de ms-03 (`Guia ->
Actividad` en el modelo de dominio). La demanda de actividades de `Guía US8` vive en ms-03. La
vista consolidada de la gestora también está en `GET /api/v1/demand-indicators` de ms-03, que
consulta este endpoint y suma actividades/eventos + servicios en una sola respuesta.

Parámetros: `from` y `to` son instantes ISO-8601 (`from` inclusivo, `to` exclusivo; por defecto los
últimos 30 días, máximo 366 días), `granularity` es `DAY` o `MONTH`. La respuesta incluye:

- totales de reservas, personas, ingresos, cancelaciones, completadas, turistas únicos, tasa de
  cancelación y promedio de personas por reserva;
- comparación porcentual contra el periodo inmediatamente anterior de igual duración;
- serie temporal (`timeline`), distribución por día de la semana y por hora;
- desglose por zona, sitio y servicio con participación y ocupación estimada.

La ocupación es una estimación: para servicios se usa `maxCapacity` por día, ajustada por la suma
de cupos de las franjas de disponibilidad del día cuando existen; para sitios se usa
`maxCapacity × días`; para zonas se suman las capacidades de los sitios distintos de la zona. La
tasa se limita a `1.0` para no presentar sobreocupación como un porcentaje mayor a 100 %. Los
ingresos son la suma de `reservation_items.total` de reservas no canceladas y **no** son un
recaudo real: las reservas de ms-04 son simbólicas.

`GET /api/v1/demand-indicators/report` devuelve el resumen, la comparación y los desgloses por
zona, sitio y servicio en CSV para descargar como soporte de planificación.

Ejemplo de creación (requiere header `Idempotency-Key`):

```http
POST /api/v1/reservations
Idempotency-Key: 7d10a1b2-c3d4-4e5f-8a9b-0c1d2e3f4a5b
Content-Type: application/json
```

```json
{
  "bookedFor": "2026-11-09T17:00:00Z",
  "notes": "Mesa junto a la ventana",
  "items": [
    {"serviceId": "55555555-5555-5555-5555-555555555501", "people": 2},
    {"serviceId": "55555555-5555-5555-5555-555555555502", "people": 1}
  ]
}
```

Estados: `CONFIRMED` (creación automática al apartar cupo), `CANCELLED` (libera cupo) y
`COMPLETED` (solo después de la fecha reservada). `PENDING` no se genera en ms-04.

Las escrituras aceptan `X-Correlation-Id`; si no se envía, se genera uno.

## Seguridad

En `prod`, este servicio valida el claim `ver` contra ms-01 (`MS01_BASE_URL`) y exige
`mfa=true` a los administradores. La validación es fail-closed: si ms-01 no responde, las
peticiones se rechazan. En local/tests queda desactivada con `AUTH_VERSION_ENABLED=false`.

- Solo RS256. No se acepta HS256.
- Producción / ms-01: `JWK_SET_URI=https://ms-01/.../.well-known/jwks.json`.
- Local/tests: `JWT_PUBLIC_KEY_LOCATION=file:/ruta/public.pem`.
- `JWT_ISSUER=sm-xplore-ms-01` y `JWT_AUDIENCE=sm-xplore-services`.
- Identidad desde `sub` o, si no viene, del claim `userId` (`JWT_USER_ID_CLAIM`).
- Roles: `DESTINATION_MANAGER`, `SERVICE_PROVIDER`, `GUIDE`, `TOURIST`, `ADMIN`.
- Los tokens con `scope=mfa` se rechazan: son desafíos de segundo factor, no tokens de acceso.
- Las reglas de propiedad se validan en la capa de aplicación: una prestadora solo modifica sus
  servicios; una gestora solo modifica sus zonas/sitios; el `ADMIN` puede moderar.
- La taxonomía de categorías es global: solo `ADMIN` crea y actualiza categorías (regla HTTP y
  comprobación `requireTaxonomyAdmin` en la capa de aplicación). Antes de guardar se valida el
  árbol completo: el padre debe existir, no puede haber ciclos y la profundidad máxima es 50.
- El catálogo público (`GET`/`HEAD` de sitios y servicios) solo expone contenido `PUBLISHED`.
  El detalle de un borrador o inactivo responde `404` salvo para su gestora/proveedor dueño o un
  `ADMIN`, y los listados anónimos no pueden pedir otro estado.
- Una prestadora solo puede vincular servicios a sitios `PUBLISHED`, de la zona indicada en el
  servicio y administrados por el gestor de esa zona; si no, la operación responde `400`.
- Una gestora solo puede crear o mover sus sitios a zonas que ella misma administra; el `ADMIN`
  puede asignar cualquier zona y el resto recibe `403`.
- Los indicadores de demanda se limitan por rol: prestadora a sus servicios y gestora a sus
  zonas/sitios.
- En reservas, un turista solo crea, consulta y cancela las suyas. Como todos los ítems de una
  reserva pertenecen a una sola prestadora, esta solo ve y gestiona sus propias reservas. El
  `ADMIN` puede gestionar cualquiera. Las reservas se confirman automáticamente; no existe una
  acción de aceptación manual.
- Cada usuario solo ve y marca sus propias notificaciones; el `ADMIN` puede marcar cualquiera.
- El reproceso de outbox está restringido a `ADMIN`, exige `limit` entre 1 y 500, admite
  `dryRun` para contar sin reencolar, limita a 5 peticiones cada 10 minutos por administrador y
  deja auditoría con quién, cuándo, límite, modo y cuántos eventos se reencolaron.
- Las lecturas públicas de reseñas no requieren token: un anónimo solo ve `PUBLISHED` y el
  listado de reportes es solo de `ADMIN`. Responder una reseña exige que esté `PUBLISHED` y sin
  respuesta previa; si no, responde `409` y no la sobrescribe.
- La consulta de disponibilidad de reservas exige autenticación y solo la pueden hacer turistas
  y personal operativo. El cupo detallado (capacidad, plazas reservadas y proveedor) se muestra
  solo a `ADMIN`, gestoras y a la prestadora dueña; el turista recibe disponibilidad booleana.
  Además hay un límite de 120 consultas por minuto por cuenta (`429`).
- Cambiar el correo de contacto de notificaciones exige `confirm=true` en el cuerpo, es solo de
  `ADMIN` y deja un registro en la colección Mongo `security_audit` con el correo anterior y el
  nuevo. Sin verificación por correo del titular (mitigación proporcionada; riesgo residual).
- El consumidor de `bookings.reservation.changed` solo acepta `producer` en
  `{ms-03, ms-03-activities-and-events, ms-04}`, `eventVersion` 1 o 2 y como máximo 50 ítems; los
  mensajes inválidos se publican en `NOTIFICATIONS_DLQ_TOPIC` sin efectos laterales.
- Crear una reseña exige una reserva `COMPLETED` previa del servicio (o de algún servicio del
  sitio reseñado), con `REVIEW_REQUIRE_COMPLETED_BOOKING=true` por defecto, y está limitado a 10
  reseñas por hora y 20 reportes por hora por usuario (`429`). El filtro de IA es fail-closed:
  `REVIEW_AI_FAIL_OPEN=false` por defecto, de modo que una caída del LLM rechaza la reseña.
- El filtro de precio de servicios valida importes no negativos, `priceMin <= priceMax`, tope de
  100 000 000 y un offset máximo de 10 000 (`400` con mensaje). Hay índices `status+price` y
  `site.id+price` en `ServiceDocument`.
- Los `IllegalArgumentException` no controlados responden `400` con un detalle genérico
  ("The request contains an invalid value.") y el mensaje interno solo se registra en el log, para
  no exponer detalles de conversiones o de implementación (MS04-13).
- Swagger declara el esquema `bearerAuth` para enviar el JWT desde la UI.

### Consistencia de snapshots de catálogo y servicios (MS04-14)

Los servicios guardan snapshots de catálogo (`site.name`, `zone.name`, `zone.status`,
`categoryName`) para no depender de ms-04 en cada lectura. Esos snapshots se refrescan al renombrar
un sitio, al cambiar el nombre o el estado de una zona y al renombrar una categoría, mediante los
flujos de actualización ya existentes.

Riesgo aceptado (fase informativa): el cambio de estado de un sitio (`SiteChangeStatusService`) no
propaga el estado a los snapshots de sus servicios y el listado público de servicios solo filtra por
el estado del propio servicio (más la visibilidad por rol descrita arriba). Por tanto, un servicio
`PUBLISHED` puede seguir apareciendo en `GET /api/v1/services` aunque su sitio pase a
`DRAFT`/`INACTIVE` o su zona quede `INACTIVE`. El flujo de reservas sí vuelve a validar el sitio y la
zona en el momento de reservar (`SITE_NOT_PUBLISHED`) y ms-05 debe validar la visibilidad del sitio
antes de mostrar un servicio.

Impacto bajo (inconsistencia de visibilidad y de snapshots; sin explotación directa). Se documenta en
lugar de corregirse en esta fase porque la corrección requiere una decisión de producto (si los
servicios deben desaparecer cuando su sitio se despublica y qué hacer con los ya existentes) y un
backfill de MongoDB. Corrección recomendada para una fase posterior:

1. Añadir `site.status` al snapshot del servicio y refrescarlo al cambiar el estado del sitio.
2. Filtrar los listados públicos por `site.status = PUBLISHED` y `zone.status != INACTIVE`.
3. Tratar los documentos antiguos sin `site.status` como visibles durante la transición.

## Kafka y contrato

Topics publicados:

| Topic | Evento | Contrato |
|---|---|---|
| `catalog.category.changed` | Categoría creada/actualizada | `contracts/events/catalog.category.changed.v1.json` |
| `catalog.site.changed` | Sitio creado/actualizado | `contracts/events/catalog.site.changed.v1.json` |
| `catalog.service.changed` | Servicio creado/actualizado | `contracts/events/catalog.service.changed.v1.json` |
| `reviews.review.changed` | Reseña creada/moderada | `contracts/events/reviews.review.changed.v1.json` |
| `bookings.reservation.changed` | Reserva creada/cancelada/completada | `contracts/events/bookings.reservation.changed.v1.json` (ms-04) / `.v2.json` (ms-03) |

- La key de Kafka es el `aggregateId`.
- El sobre incluye `eventId`, `eventVersion`, `occurredAt`, `correlationId` y `producer=ms-04`. La versión
  sale de `EventVersions`: `catalog.site.changed` es v2 (agrega `managerId`), `bookings.reservation.changed`
  de ms-04 es v1 y los demás son v1.
- `reviews.review.changed` es compartido: ms-03 publica actividades/eventos y ms-04 sitios/servicios.
  El estado puede ser `PUBLISHED`, `PENDING_REVIEW`, `HIDDEN` o `DELETED`; los consumidores deben
  tratar `PENDING_REVIEW` igual que una reseña no visible.
- `bookings.reservation.changed` v1 se publica para reservas de servicios (`itemType=SERVICE`).
  ms-03 publica la v2 para actividades/eventos con `itemName`, `guideId`, `managerId` y `NO_SHOW`.
- El outbox de catálogo/reseñas se escribe en la misma transacción Mongo que la entidad. El outbox
  de reservas se escribe en `reservation_outbox`, en la misma transacción PostgreSQL que la reserva
  y sus ítems.
- Los relays reclaman filas de forma atómica (`FOR UPDATE SKIP LOCKED` en PostgreSQL y
  `findAndModify` en MongoDB), las pasan a `PROCESSING` con un lease y solo ese worker puede
  marcarlas como `PUBLISHED` o devolverlas a `PENDING`. Esto evita duplicados normales entre
  instancias.
- La entrega es **at-least-once**: si el proceso muere después de publicar y antes de marcar
  `PUBLISHED`, el evento se republica al vencer el lease. Los consumidores deben deduplicar por
  `eventId`.
- Si Kafka está caído, la API sigue escribiendo entidades y el outbox queda pendiente.

Topics consumidos:

| Topic | Consumidor | Uso |
|---|---|---|
| `bookings.reservation.changed` | ms-04 (`ms-04-notifications`) | Crear notificaciones de bandeja para turista, guía, gestora y prestadora |

- El consumidor acepta v1 y v2, procesa `SERVICE`, `ACTIVITY` y `EVENT`, usa `eventId` para IDs
  determinísticos de notificación (incluye rol e id del destinatario) y evita duplicados. Un grupo
  nuevo arranca en `latest`; los tests usan `earliest` para no depender del orden de arranque.
- Antes de procesar se valida la procedencia: `producer` permitido, `eventVersion` soportada y
  máximo 50 ítems. Un mensaje que no cumple se envía a `NOTIFICATIONS_DLQ_TOPIC`
  (`kafka.dlq.bookings.reservation.changed` por defecto) sin crear notificaciones, y un fallo al
  publicar en la DLQ se registra en el log sin reprocesar el mensaje.
- El evento incluye `bookedFor`, `itemName`, `providerId`/`guideId`/`managerId` y `serviceName`
  como campos opcionales para permitir notificaciones sin consultar otros servicios. La v2 añade
  `NO_SHOW`.
- La entrega de notificaciones es una **bandeja interna**; el envío real por correo/push queda
  pendiente como adaptador de entrega.

### Retención y reproceso

- `OutboxRetentionJob` elimina eventos `PUBLISHED`/`FAILED` más antiguos que
  `app.outbox.retention-days` en PostgreSQL y MongoDB.
- `POST /api/v1/admin/outbox/reprocess` resetea eventos `FAILED` a `PENDING`, con `attempts=0`,
  en ambos outboxes. Acepta `limit` (1..500, por defecto 100) y `dryRun=true` para contar sin
  modificar. Está limitado por cuenta, deja auditoría en `security_audit` y emite la métrica
  `outbox.reprocess.events{kind}`. El relay los vuelve a intentar.

### Métricas

En `/actuator/prometheus` (público; aislar por red en producción):

- `catalog_outbox_published_total{event_type}`.
- `catalog_outbox_failed_total{event_type}`.
- `booking_outbox_published_total{event_type}`.
- `booking_outbox_failed_total{event_type}`.
- `outbox.reprocess.events{kind=reservation|catalog}`.
- Métricas estándar de Actuator, JVM, HTTP y Kafka.

## Configuración

| Variable | Default | Descripción |
|---|---|---|
| `SERVER_PORT` | `8080` | Puerto interno |
| `MONGODB_URI` | `mongodb://localhost:27017/smxplore?replicaSet=rs0` | Mongo con replica set |
| `POSTGRES_R2DBC_URL` | `r2dbc:postgresql://localhost:5432/smxplore` | PostgreSQL R2DBC |
| `POSTGRES_JDBC_URL` | `jdbc:postgresql://localhost:5432/smxplore` | Conexión JDBC que usa Flyway |
| `FLYWAY_ENABLED` | `true` | Ejecuta las migraciones al arrancar |
| `FLYWAY_BASELINE_ON_MIGRATE` | `true` | Baselinea bases creadas con el antiguo `schema.sql` |
| `POSTGRES_USER` | `smxplore` | Usuario PostgreSQL |
| `POSTGRES_PASSWORD` | `change-me` | Contraseña PostgreSQL |
| `KAFKA_BOOTSTRAP_SERVERS` | `localhost:29092` | Broker Kafka |
| `KAFKA_CONSUMER_GROUP_ID` | `ms-04-notifications` | Grupo del consumidor de notificaciones |
| `KAFKA_AUTO_OFFSET_RESET` | `latest` | Offset inicial del consumidor |
| `NOTIFICATIONS_ENABLED` | `true` | Activa el consumidor y la bandeja |
| `NOTIFICATIONS_EMAIL_ENABLED` | `false` | Replica cada notificación nueva al correo del destinatario |
| `NOTIFICATIONS_EMAIL_FROM` | `no-reply@smxplore.local` | Remitente del correo |
| `NOTIFICATIONS_EMAIL_SUBJECT_PREFIX` | `[SM-Xplore] ` | Prefijo del asunto |
| `SMTP_HOST` | `localhost` (compose: `mailpit`) | Servidor SMTP |
| `SMTP_PORT` | `1025` | Puerto SMTP (Mailpit por defecto) |
| `SMTP_AUTH` | `false` | Autenticación SMTP |
| `SMTP_STARTTLS` | `false` | STARTTLS |
| `MAILPIT_UI_PORT` | `8025` | Bandeja web de Mailpit en Compose |
| `JWK_SET_URI` | vacío | JWKS de ms-01 |
| `JWT_PUBLIC_KEY_LOCATION` | vacío | PEM local/test |
| `JWT_ISSUER` | `sm-xplore-ms-01` | Issuer esperado |
| `JWT_AUDIENCE` | `sm-xplore-services` | Audiencia esperada |
| `JWT_USER_ID_CLAIM` | `userId` | Claim de identidad si no viene `sub` |
| `REVIEW_AI_ENABLED` | `false` | Activa el filtro de IA de reseñas |
| `LITTLELLM_URL` | vacío | Base URL del servidor littleLLM compatible con OpenAI |
| `LITTLELLM_API_KEY` | vacío | Llave enviada al LLM |
| `LITTLELLM_API_KEY_HEADER` | `Authorization` | Cabecera de la llave |
| `LITTLELLM_API_KEY_PREFIX` | `Bearer` | Prefijo de la llave |
| `LITTLELLM_MODEL` | `littlellm` | Modelo solicitado |
| `LITTLELLM_CHAT_PATH` | `/v1/chat/completions` | Ruta de chat completions |
| `REVIEW_AI_TIMEOUT` | `PT5S` | Timeout de la llamada al LLM |
| `REVIEW_AI_THRESHOLD` | `0.7` | Score mínimo para retener una reseña |
| `REVIEW_AI_TEMPERATURE` | `0.0` | Temperatura del modelo |
| `REVIEW_AI_ACTION` | `PENDING_REVIEW` | Acción al marcar: `PENDING_REVIEW` o `HIDE` |
| `REVIEW_AI_FAIL_OPEN` | `false` | Si el LLM falla, rechaza la reseña (fail-closed). |
| `REVIEW_REQUIRE_COMPLETED_BOOKING` | `true` | Exige una reserva `COMPLETED` del servicio para reseñar |
| `NOTIFICATIONS_DLQ_TOPIC` | `kafka.dlq.bookings.reservation.changed` | Topic DLQ para eventos de reserva inválidos |
| `REVIEW_AI_JSON_RESPONSE_FORMAT` | `false` | Envía `response_format=json_object` |
| `OUTBOX_ENABLED` | `true` | Activa el relay |
| `OUTBOX_PRODUCER` | `ms-04` | Campo `producer` del sobre |
| `OUTBOX_POLL_INTERVAL_MS` | `1000` | Intervalo del relay |
| `OUTBOX_MAX_ATTEMPTS` | `20` | Intentos antes de marcar `FAILED` |
| `OUTBOX_BATCH_SIZE` | `50` | Filas reclamadas por ciclo del relay |
| `OUTBOX_INITIAL_BACKOFF` | `1s` | Backoff inicial |
| `OUTBOX_MAX_BACKOFF` | `1m` | Backoff máximo |
| `OUTBOX_LEASE_DURATION` | `3m` | Lease para recuperar filas `PROCESSING` tras una caída |
| `OUTBOX_RETENTION_DAYS` | `30` | Días que se conservan eventos publicados/fallidos |
| `OUTBOX_RETENTION_INTERVAL_MS` | `3600000` | Intervalo del job de retención |
| `BOOKINGS_ZONE_ID` | `America/Bogota` | Zona para horarios y capacidad diaria de reservas |
| `BOOKINGS_MAX_ACTIVE_PER_TOURIST` | `5` | Máximo de reservas confirmadas futuras por turista |
| `BOOKINGS_MAX_CREATIONS_PER_MINUTE` | `10` | Máximo de creaciones por turista por minuto |
| `BOOKINGS_MAX_ADVANCE_DAYS` | `365` | Anticipación máxima para reservar |
| `BOOKINGS_AUTO_COMPLETE_ENABLED` | `true` | Activa el job de auto-completado |
| `BOOKINGS_COMPLETION_INTERVAL_MS` | `60000` | Intervalo del job de auto-completado |
| `BOOKINGS_COMPLETION_BATCH_SIZE` | `100` | Reservas procesadas por ciclo |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:3000,http://localhost:4200` | Orígenes permitidos |

## Ejecución local

1. Levantar la infraestructura compartida:

   ```bash
   cd infrastructure
   cp .env.example .env
   docker compose up -d
   ```

2. Levantar Mongo, PostgreSQL y la API:

   ```bash
   cd microservices/ms-04-sites-and-services
   cp .env.example .env
   # Configura JWK_SET_URI o JWT_PUBLIC_KEY_LOCATION, JWT_ISSUER y contraseñas
   docker compose up --build
   ```

3. Verificar:

   ```text
   API:     http://localhost:8084
   Swagger: http://localhost:8084/swagger-ui.html
   Health:  http://localhost:8084/actuator/health
   Mongo:   localhost:27017
   Postgres localhost:5432
   Mailpit: http://localhost:8025
   ```

## Pruebas

```bash
./gradlew clean test bootJar
```

Resultado actual de la selección sin Docker (aplicación, dominio, contrato y arquitectura):
**125 pruebas en 28 clases, 0 fallos**. La suite completa con Testcontainers requiere Docker.

La suite incluye `ArchitectureTest` (reglas del hexágono y nombres de puertos),
`ReservationChangedEventDecoderContractTest` (destinatarios de notificación para reservas v1 y v2) y
las pruebas de la alerta de cupo bajo (umbral, deduplicación y no repetición) y del canal push.

Las regresiones de la fase BAJA viven en `application/security/`:
`ReviewVisibilityServiceTest` (MS04-05), `ServiceFilterValidationTest` (MS04-06),
`ReviewResponseConflictServiceTest` (MS04-07), `AvailabilityPrivacyServiceTest` (MS04-08),
`NotificationContactAuditServiceTest` (MS04-09), `OutboxReprocessGuardServiceTest` (MS04-10),
`ReservationEventProvenanceTest` (MS04-11) y `ReviewConsumptionProofServiceTest` (MS04-12).

La suite usa Testcontainers con:

- `mongo:8.2` en replica set.
- `postgres:16-alpine`.
- `apache/kafka:4.1.0` para los tests de outbox.

Cubre:

- flujo completo de categoría, zona, sitio, servicio y reseña;
- flujo completo de reserva: crear confirmada, consultar, cancelar y liberar cupo;
- reservas con varios servicios de una misma prestadora y cálculo del total;
- rechazo de reservas con ítems de varias prestadoras y de más de 10 ítems;
- idempotencia de creación por `Idempotency-Key`, replay aunque el servicio ya no esté disponible
  y rechazo de key reutilizada con otro payload;
- rechazo por capacidad diaria agotada, servicio no publicado y horario fuera de disponibilidad;
- límite de reservas activas por turista, rate limit de creación y ventana máxima de anticipación;
- auto-completado de reservas pasadas y emisión del evento `COMPLETED`;
- disponibilidad de fechas pasadas y validación de reglas de propiedad y roles;
- rechazo de reseñas duplicadas e ítems de reserva repetidos;
- filtro de IA: parseo del veredicto, timeout y caída del LLM, política fail-open/fail-closed,
  creación de reseña retenida en `PENDING_REVIEW`, cola administrativa y publicación posterior;
- indicadores de demanda: alcance de prestadora y gestora, ocupación, timeline, comparación
  contra el periodo anterior, `403` para guías/turistas y descarga CSV;
- reporte de reseña ajena, duplicado (`409`), autorreporte (`403`), resolución administrativa que
  oculta la reseña y métricas de reputación agregadas;
- listado paginado de reservas con carga de ítems en una sola consulta por página;
- persistencia de zonas y reservas en PostgreSQL, y catálogo/notificaciones en MongoDB;
- eventos de outbox y su serialización contra los JSON Schema;
- publicación real del outbox de catálogo y de reservas a Kafka;
- dos relays concurrentes publican cada evento una sola vez;
- retención de outbox y reproceso administrativo de eventos `FAILED`;
- consumo Kafka de reservas, bandeja de notificaciones, idempotencia y marcado como leída;
- entrega por correo de las notificaciones nuevas, alta y baja del contacto, y silencio cuando el
  canal está apagado o el destinatario no tiene correo;
- feed de reindexado del catálogo con el payload de los eventos, filtro incremental y solo `ADMIN`;
- `/actuator/prometheus` accesible sin JWT y OpenAPI con `bearerAuth`.

La operación diaria está descrita en `docs/runbook.md` (arranque, Flyway, outbox, capacidad,
notificaciones, incidencias, copias y restauración) y el escenario de carga en
`loadtest/booking-flow.js` (k6).

La integración continua está en `.github/workflows/ms-04-ci.yml` y corre en los push a `main`,
`microservicios` y la rama de ms-04, y en los pull request que toquen el servicio:

- job `verify`: JDK 25, `./gradlew test bootJar`, anotaciones `::error` con el primer fallo de cada
  prueba y artefactos con los reportes.
- job `docker`: construye la imagen, la escanea con Trivy (`CRITICAL` y `HIGH` con corrección
  disponible), levanta Kafka desde `infrastructure/compose.yaml` para tener la red `smxplore-net`,
  arranca PostgreSQL, MongoDB y Mailpit con `mongo-init`, y ejecuta el contenedor con la clave
  pública de pruebas hasta que `/actuator/health` responde `UP` y `/v3/api-docs` responde `200`.

## Notificaciones internas (P2-9)

- La bandeja de notificaciones en MongoDB sigue siendo la fuente de verdad. El canal de correo se
  eligió en el P1 y ahora convive con un canal de push de extensión: `PushNotificationDelivery`
  registra el intento (`notifications.push.attempts`) o la omisión
  (`notifications.push.skipped`) cuando `app.notifications.push.enabled=false`, sin proveedor
  externo (punto de extensión documentado en el javadoc).
- Al cruzar el umbral de cupo bajo de un servicio se emite una notificación `LOW_CAPACITY_ALERT`
  (RF66) al proveedor y a la gestora del sitio: umbral `ceil(capacidad * threshold-percent / 100)`
  configurable con `app.notifications.low-capacity.*`, id determinista (replay idempotente) y
  entrega best-effort que nunca tumba la reserva.
- El endpoint interno de contactos de ms-01 exige firma HMAC por servicio (MS01-11). La clave
  maestra con la que se derivan las claves por servicio se pasa en ms-01 con `INTERNAL_API_KEY`;
  hoy no hay ningún llamador dentro del repo, así que ms-04 no firma esas peticiones.
- ms-04 es el **hub de notificaciones** del sistema (P3-4 firmada): ningún otro servicio envía
  correo ni push. El hub decide el destinatario con los ids que trae el evento
  (`bookings.reservation.changed` v1/v2) y entrega por los canales configurados:

  | Evento | Destinatario | Rol | Canales |
  |---|---|---|---|
  | Reserva de servicio | Proveedor del servicio | `SERVICE_PROVIDER` | bandeja + email + push (si está encendido) |
  | Reserva de servicio | Gestora del sitio (si no es la misma) | `DESTINATION_MANAGER` | bandeja + email + push |
  | Reserva de actividad | Guía de la actividad | `GUIDE` | bandeja + email + push |
  | Reserva de evento | Gestora del evento | `DESTINATION_MANAGER` | bandeja + email + push |
  | Cruce de cupo bajo (RF66) | Proveedor y gestora del sitio | `SERVICE_PROVIDER` / `DESTINATION_MANAGER` | bandeja + email + push |
  | Reserva confirmada/cancelada | Turista que reservó | `TOURIST` | bandeja (+ email si está encendido) |

## Catálogo semilla y taxonomía (P3-10)

- `app.catalog.seed-enabled` (`CATALOG_SEED_ENABLED`, **apagado por defecto**) crea al arrancar las
  categorías base que falten: `AVENTURA`, `CULTURA`, `GASTRONOMIA`, `NATURALEZA`, `BIENESTAR`,
  `RECREACION`, todas `GENERAL`. Es idempotente (compara por nombre sin distinguir mayúsculas), no
  borra ni renombra nada y si Mongo no responde el servicio arranca igual (`CatalogSeedRunner`
  registra el aviso). En el stack local viene encendido.
- Solo el `ADMIN` puede crear o actualizar categorías. La semilla inicial la siembra el servicio
  al arrancar (`CATALOG_SEED_ENABLED`) y el resto de la taxonomía, incluidas las hijas (`parentId`),
  se gestiona por la API con rol `ADMIN`. La taxonomía es de ms-04 porque es el dueño del catálogo.
- Las zonas llevan `type` libre y estado `ACTIVE|CONGESTED|INACTIVE`; la congestión se marca
  **manualmente**, no hay fuente automática en esta entrega.

## Pendiente

- El filtro de IA es síncrono y fail-open por defecto. Si la latencia importa, mover la evaluación
  a una cola y mantener la reseña `PENDING_REVIEW` hasta que termine.
- El contrato real de littleLLM debe confirmarse contra el servidor desplegado; el adaptador asume
  el esquema de chat completions de OpenAI.
- La ocupación es estimada con capacidad diaria y franjas; no cruza reservas de actividades/eventos
  de ms-03 ni datos de afluencia externos.
- ms-05 ya consume el feed `GET /api/v1/admin/catalog/export`; queda extender el reindexado a
  reseñas y a los indicadores de demanda si el producto los necesita.
