# ms-04 — Sitios, Servicios, Reservas y Notificaciones

Microservicio de catálogo turístico de SM-Xplore. Gestiona:

- **Zonas** turísticas en PostgreSQL.
- **Sitios** turísticos y **servicios** de prestadoras en MongoDB.
- **Categorías** del catálogo en MongoDB.
- **Reseñas** de sitios y servicios en MongoDB.
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
- El consumidor de notificaciones lee `bookings.reservation.changed` y guarda una notificación
  idempotente por destinatario.

## Bases de datos

### MongoDB

Base `smxplore` (configurable con `MONGO_DATABASE`). Colecciones:

- `categories`
- `sites`
- `services`
- `reviews`
- `notifications`
- `outbox_event`

MongoDB debe ejecutarse como **replica set** para soportar transacciones. El `docker-compose.yml`
levanta un nodo con replica set `rs0` y un contenedor de inicialización.

### PostgreSQL

Base `smxplore` (configurable con `POSTGRES_DB`). Tablas:

- `zones`
- `reservations`
- `reservation_items`
- `reservation_outbox`

El esquema se crea desde `src/main/resources/schema.sql` con `spring.sql.init.mode=always`.
`reservations` y `reservation_items` implementan las tablas `Reserva` y `ReservaItem` del modelo
relacional físico.

Las reservas son **simbólicas**: no hay pasarela de pagos ni campos de pago. Una reserva aparta
`people` cupos por cada ítem de servicio y conserva una foto del nombre, prestadora, tipo y precio
del servicio. El pago lo gestiona cada prestadora por fuera de SM-Xplore.

Reglas:

- Una reserva puede tener **máximo 10 ítems** y todos deben pertenecer a **una sola prestadora**.
  La regla se aplica al crear; las reservas históricas multi-prestadora se mantienen legibles.
- Toda reserva se crea directamente en `CONFIRMED`: no requiere aceptación manual. `PENDING` se
  conserva solo por compatibilidad con datos heredados y el contrato compartido; `schema.sql`
  normaliza las filas `PENDING` existentes a `CONFIRMED`. Esa normalización es una migración de
  datos y no emite un evento nuevo: si existen proyecciones de consumidores, deben reconciliarse.
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
- La fecha/hora reservada debe caer dentro de alguna franja de `Service.availability`. Si el
  servicio no tiene franjas configuradas, se permite cualquier horario.
- El servicio debe estar `PUBLISHED` y su zona no puede estar `INACTIVE`.
- `POST /api/v1/reservations` requiere `Idempotency-Key`: el mismo key con el mismo payload
  devuelve la reserva existente; el mismo key con otro payload responde `409`.

## API

Todas las rutas requieren JWT salvo `health`, `info`, `/actuator/metrics`,
`/actuator/prometheus` y Swagger. En producción, los endpoints de management deben aislarse en
una red o puerto privado.

| Método | Ruta | Roles | Descripción |
|---|---|---|---|
| POST | `/api/v1/categories` | `GESTORA_DESTINOS`, `ADMIN` | Crear categoría |
| PUT | `/api/v1/categories/{id}` | `GESTORA_DESTINOS`, `ADMIN` | Actualizar categoría |
| GET | `/api/v1/categories` | autenticado | Listar/filtrar categorías |
| POST | `/api/v1/zones` | `GESTORA_DESTINOS`, `ADMIN` | Crear zona |
| PUT | `/api/v1/zones/{id}` | `GESTORA_DESTINOS`, `ADMIN` | Actualizar zona |
| PATCH | `/api/v1/zones/{id}/status` | `GESTORA_DESTINOS`, `ADMIN` | Cambiar estado/congestión |
| GET | `/api/v1/zones` | autenticado | Listar/filtrar zonas |
| POST | `/api/v1/sites` | `GESTORA_DESTINOS`, `ADMIN` | Crear sitio |
| PUT | `/api/v1/sites/{id}` | `GESTORA_DESTINOS`, `ADMIN` | Actualizar sitio |
| PATCH | `/api/v1/sites/{id}/status` | `GESTORA_DESTINOS`, `ADMIN` | Cambiar estado |
| PATCH | `/api/v1/sites/{id}/zone` | `GESTORA_DESTINOS`, `ADMIN` | Asignar zona |
| GET | `/api/v1/sites` | autenticado | Listar/filtrar sitios |
| POST | `/api/v1/services` | `PRESTADORA_SERVICIOS`, `ADMIN` | Crear servicio |
| PUT | `/api/v1/services/{id}` | `PRESTADORA_SERVICIOS`, `ADMIN` | Actualizar servicio |
| PATCH | `/api/v1/services/{id}/status` | `PRESTADORA_SERVICIOS`, `ADMIN` | Cambiar estado |
| GET | `/api/v1/services` | autenticado | Listar/filtrar servicios |
| POST | `/api/v1/reviews` | `TURISTA` | Crear reseña de sitio o servicio |
| POST | `/api/v1/reviews/{id}/response` | `PRESTADORA_SERVICIOS`, `GESTORA_DESTINOS`, `ADMIN` | Responder reseña |
| PATCH | `/api/v1/reviews/{id}/status` | `ADMIN` | Moderar reseña |
| GET | `/api/v1/reviews/summary?itemType=&itemId=` | autenticado | Promedio y número de reseñas |
| GET | `/api/v1/reviews` | autenticado | Listar reseñas |
| POST | `/api/v1/reservations` | `TURISTA` | Crear reserva confirmada de hasta 10 servicios de una prestadora |
| GET | `/api/v1/reservations/availability?serviceId=&bookedFor=&people=` | autenticado | Consultar cupos disponibles |
| GET | `/api/v1/reservations/{id}` | turista dueño, prestadora o `ADMIN` | Ver reserva |
| GET | `/api/v1/reservations` | `TURISTA`, `PRESTADORA_SERVICIOS`, `ADMIN` | Listar reservas según el actor |
| PATCH | `/api/v1/reservations/{id}/status` | turista dueño (cancelar), prestadora o `ADMIN` | Cancelar o completar |
| GET | `/api/v1/notifications?unreadOnly=&page=&size=` | autenticado | Bandeja del usuario actual |
| PATCH | `/api/v1/notifications/{id}/read` | destinatario o `ADMIN` | Marcar notificación como leída |
| POST | `/api/v1/admin/outbox/reprocess` | `ADMIN` | Resetear eventos `FAILED` a `PENDING` |

Filtros disponibles:

- Sitios: `categoryId`, `zoneId`, `status`, `search`, `page`, `size`.
- Servicios: `categoryId`, `zoneId`, `siteId`, `providerId`, `status`, `type`, `search`, `page`, `size`.
- Zonas: `status`, `type`, `search`, `page`, `size`.
- Reseñas: `itemType`, `itemId`, `touristId`, `status`, `page`, `size`.
- Reservas: `touristId`, `providerId`, `serviceId`, `status`, `from`, `to`, `page`, `size`.
  El turista solo ve las suyas y la prestadora solo las que incluyen sus servicios.
- Notificaciones: `unreadOnly`, `page`, `size`; siempre se limita al usuario autenticado.

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

- Solo RS256. No se acepta HS256.
- Producción / ms-01: `JWK_SET_URI=https://ms-01/.../.well-known/jwks.json`.
- Local/tests: `JWT_PUBLIC_KEY_LOCATION=file:/ruta/public.pem`.
- Identidad desde `sub` o, si no viene, del claim `userId` (`JWT_USER_ID_CLAIM`).
- Roles: `GESTORA_DESTINOS`, `PRESTADORA_SERVICIOS`, `TURISTA`, `ADMIN`.
- Las reglas de propiedad se validan en la capa de aplicación: una prestadora solo modifica sus
  servicios; una gestora solo modifica sus zonas/sitios; el `ADMIN` puede moderar.
- En reservas, un turista solo crea, consulta y cancela las suyas. Como todos los ítems de una
  reserva pertenecen a una sola prestadora, esta solo ve y gestiona sus propias reservas. El
  `ADMIN` puede gestionar cualquiera. Las reservas se confirman automáticamente; no existe una
  acción de aceptación manual.
- Cada usuario solo ve y marca sus propias notificaciones; el `ADMIN` puede marcar cualquiera.
- El reproceso de outbox está restringido a `ADMIN`.
- Swagger declara el esquema `bearerAuth` para enviar el JWT desde la UI.

## Kafka y contrato

Topics publicados:

| Topic | Evento | Contrato |
|---|---|---|
| `catalog.category.changed` | Categoría creada/actualizada | `contracts/events/catalog.category.changed.v1.json` |
| `catalog.site.changed` | Sitio creado/actualizado | `contracts/events/catalog.site.changed.v1.json` |
| `catalog.service.changed` | Servicio creado/actualizado | `contracts/events/catalog.service.changed.v1.json` |
| `reviews.review.changed` | Reseña creada/moderada | `contracts/events/reviews.review.changed.v1.json` |
| `bookings.reservation.changed` | Reserva creada/cancelada/completada | `contracts/events/bookings.reservation.changed.v1.json` |

- La key de Kafka es el `aggregateId`.
- El sobre incluye `eventId`, `eventVersion=1`, `occurredAt`, `correlationId` y `producer=ms-04`.
- `reviews.review.changed` es compartido: ms-03 publica actividades/eventos y ms-04 sitios/servicios.
- `bookings.reservation.changed` se publica para reservas de servicios (`itemType=SERVICE`).
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
| `bookings.reservation.changed` | ms-04 (`ms-04-notifications`) | Crear notificaciones de bandeja para turista y prestadora |

- El consumidor procesa eventos con `itemType=SERVICE`, usa `eventId` para IDs determinísticos
  de notificación y evita duplicados. Un grupo nuevo arranca en `latest`; los tests usan
  `earliest` para no depender del orden de arranque.
- El evento incluye `bookedFor`, `providerId` y `serviceName` como campos opcionales del contrato
  para permitir notificaciones sin consultar otros servicios.
- La entrega de notificaciones es una **bandeja interna**; el envío real por correo/push queda
  pendiente como adaptador de entrega.

### Retención y reproceso

- `OutboxRetentionJob` elimina eventos `PUBLISHED`/`FAILED` más antiguos que
  `app.outbox.retention-days` en PostgreSQL y MongoDB.
- `POST /api/v1/admin/outbox/reprocess` resetea eventos `FAILED` a `PENDING`, con `attempts=0`,
  en ambos outboxes. El relay los vuelve a intentar.

### Métricas

En `/actuator/prometheus` (público; aislar por red en producción):

- `catalog_outbox_published_total{event_type}`.
- `catalog_outbox_failed_total{event_type}`.
- `booking_outbox_published_total{event_type}`.
- `booking_outbox_failed_total{event_type}`.
- Métricas estándar de Actuator, JVM, HTTP y Kafka.

## Configuración

| Variable | Default | Descripción |
|---|---|---|
| `SERVER_PORT` | `8080` | Puerto interno |
| `MONGODB_URI` | `mongodb://localhost:27017/smxplore?replicaSet=rs0` | Mongo con replica set |
| `POSTGRES_R2DBC_URL` | `r2dbc:postgresql://localhost:5432/smxplore` | PostgreSQL R2DBC |
| `POSTGRES_USER` | `smxplore` | Usuario PostgreSQL |
| `POSTGRES_PASSWORD` | `change-me` | Contraseña PostgreSQL |
| `KAFKA_BOOTSTRAP_SERVERS` | `localhost:29092` | Broker Kafka |
| `KAFKA_CONSUMER_GROUP_ID` | `ms-04-notifications` | Grupo del consumidor de notificaciones |
| `KAFKA_AUTO_OFFSET_RESET` | `latest` | Offset inicial del consumidor |
| `NOTIFICATIONS_ENABLED` | `true` | Activa el consumidor y la bandeja |
| `JWK_SET_URI` | vacío | JWKS de ms-01 |
| `JWT_PUBLIC_KEY_LOCATION` | vacío | PEM local/test |
| `JWT_ISSUER` | vacío | Issuer esperado |
| `JWT_AUDIENCE` | vacío | Audiencia esperada |
| `JWT_USER_ID_CLAIM` | `userId` | Claim de identidad si no viene `sub` |
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
   ```

## Pruebas

```bash
./gradlew clean test bootJar
```

Resultado actual: **44 tests, 0 fallos; bootJar BUILD SUCCESSFUL**.

La suite usa Testcontainers con:

- `mongo:7.0` en replica set.
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
- listado paginado de reservas con carga de ítems en una sola consulta por página;
- persistencia de zonas y reservas en PostgreSQL, y catálogo/notificaciones en MongoDB;
- eventos de outbox y su serialización contra los JSON Schema;
- publicación real del outbox de catálogo y de reservas a Kafka;
- dos relays concurrentes publican cada evento una sola vez;
- retención de outbox y reproceso administrativo de eventos `FAILED`;
- consumo Kafka de reservas, bandeja de notificaciones, idempotencia y marcado como leída;
- `/actuator/prometheus` accesible sin JWT y OpenAPI con `bearerAuth`.

## Pendiente

- Migraciones versionadas (Flyway/Liquibase) y migración con eventos de datos heredados.
- Alinear límites de columnas/tipos con validaciones de DTOs y datos de catálogo.
- Consistencia de snapshots de zona/sitio y chequeo del estado del sitio al reservar.
- Capacidad por turno además de la capacidad diaria.
- Entrega real de notificaciones por correo/push (hoy existe bandeja interna).
- Reindex/lectura paginada para ms-05.
- Moderación/reportes de reseñas y métricas de reputación.
- Pruebas de carga y runbook operativo.
