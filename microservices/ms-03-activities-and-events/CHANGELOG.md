# Changelog — ms-03 Actividades y Eventos

Cambios de este microservicio. La documentación del curso (`docs/`) no se modifica.

## [Outbox confiable y moderación acotada 2026-11-05]

### Corregido
- **Pérdida de eventos de actividades y eventos:** el encolado pasa a ser parte de la misma
  transacción PostgreSQL (`OutboxPublisher` + `TransactionalOperator`). Antes `BestEffortPublisher`
  se tragaba el error y la fila quedaba sin evento.
- **Relay del outbox:** claim con `FOR UPDATE SKIP LOCKED`, lease por fila, backoff exponencial,
  `OUTBOX_MAX_ATTEMPTS`, retención de filas terminales, métricas y conteo de los leases vencidos como
  intento (migración `V7`). Un fallo ya no puede dejar una fila en reintento infinito ni perderse.
- **Consumidor de `catalog.site.changed`:** reintentos y descarte a `catalog.site.changed.DLT`
  mediante una container factory propia; `auto-offset-reset=earliest` para poner al día un grupo nuevo.
- **DTO público de reseñas:** `ai` y `aiRetained` dejan de viajar en el catálogo; solo los expone
  `AdminReviewResponse` en los endpoints administrativos.
- **Cola de moderación:** el límite se empuja a Mongo con `Pageable` (reseñas y reportes) en lugar de
  leer la colección completa y recortarla en memoria.
- **Reportes de reseñas:** aceptar un reporte (`RESOLVED`, oculta la reseña) queda reservado a
  `ADMIN`/gestora; el guía solo puede descartarlo, para que no retire reseñas sobre su actividad.

### Agregado
- `GET /api/v1/review-reports/inbox`: bandeja del guía con los reportes de sus actividades.
- `/actuator/prometheus` y `/actuator/metrics` expuestos para el scraping en el stack local.
## [Notificaciones de reservas 2026-11-05]

### Agregado
- `bookings.reservation.changed` **v2** para reservas de actividades/eventos: incluye
  `itemName`, `guideId`, `managerId` y el estado `NO_SHOW`. El nombre del ítem se copia a la
  reserva al crearla (`item_name`, migración `V6`).
- ms-04 consume la v2 y genera notificaciones para turista, guía (actividad) y gestora (evento),
  además de las de servicios existentes.
- Plantillas nuevas para `NO_SHOW` y mensajes con el nombre real de la actividad/evento.

### Notas
- El cierre automático ahora busca las reservas vencidas una por una, aplica la transición de
  dominio y publica el evento; así también notifica los `NO_SHOW` y las cancelaciones masivas.

## [Indicadores de demanda 2026-11-05]

### Agregado
- **Guía US8:** `GET /api/v1/demand-indicators` con reservas, personas, ingresos, ocupación,
  actividades más solicitadas y comparación contra el periodo anterior, acotado a las actividades
  del guía autenticado.
- **Gestora US10/US11:** el mismo endpoint devuelve actividades y eventos de sus sitios, agrupados
  por zona y sitio, con afluencia esperada vs. reservada por evento. También consulta la demanda de
  servicios de ms-04 y la combina en una sola respuesta; `partial=true` si ms-04 no responde.
- `GET /api/v1/demand-indicators/report` descarga el reporte CSV del periodo (zonas, sitios,
  actividades, eventos y servicios); `GET /api/v1/demand-indicators/report.pdf` entrega el mismo
  reporte en PDF para planificación.
- `DEMAND_ZONE_ID` (default `America/Bogota`) controla la serie temporal, días de la semana y horas.
- Consulta SQL `PostgresDemandQueryAdapter` que une `reservations` con `actividad`/`evento` y
  proyecta capacidad, afluencia esperada, guía y gestora sin cruzar bases.

### Notas
- Los importes siguen siendo simbólicos; la ocupación se limita a `100 %`.
- El alcance de la gestora exige que el `zoneId`/`siteId` solicitado pertenezca a sus sitios; si no,
  responde `403`.

## [Moderación de reseñas 2026-11-05]

### Agregado
- **Filtro automático de IA en ms-03**, con el mismo contrato y variables que ms-04 (`REVIEW_AI_*`, `LITTLELLM_*`). Las reseñas marcadas quedan en `PENDING_REVIEW` o pasan a `HIDDEN` según `REVIEW_AI_ACTION`; la evaluación se guarda en el documento (`ai`, `aiRetained`) para auditoría.
- **Cola administrativa:** `GET /api/v1/admin/reviews?status=PENDING_REVIEW` lista reseñas de actividades y eventos; `PATCH /api/v1/reviews/{reviewId}/status` publica, oculta, elimina o devuelve una reseña a la cola. Solo `ADMIN`.
- **Eventos de cambio:** crear, responder o moderar una reseña publica `reviews.review.changed`; ms-05 recalcula el agregado de reputación al recibir estados distintos de `PUBLISHED`.
- **Reportes aceptados:** resolver un reporte como `RESOLVED` oculta la reseña y publica el evento; `DISMISSED` no la toca.

### Corregido
- Los listados públicos (`GET /reviews/events|activities/{id}`) y las métricas solo cuentan `PUBLISHED`; los documentos anteriores sin estado se siguen leyendo como publicados.
- Una reseña `HIDDEN` o `DELETED` ya no se puede responder.
- Respuestas y moderación actualizan `updatedAt`; queda documentado que el outbox de reseñas sigue siendo best effort porque Mongo y PostgreSQL no comparten transacción.

### Notas
- Se duplicó el filtro de IA de ms-04 en lugar de extraer un módulo común: cada servicio se compila y despliega por separado. La política, el prompt y las variables de entorno se mantienen alineados.

## [Consumidor de sitios 2026-11-05]

### Corregido
- **El relay del outbox nunca publicaba.** ms-03 dependia de `spring-kafka` suelto, sin el starter, y por eso el auto-configurador de Kafka no corria: no habia `KafkaTemplate` y el relay salia por el `ObjectProvider` vacio. Se cambia a `spring-boot-starter-kafka`; de paso llega el contenedor de listeners que necesita el consumidor nuevo.

### Agregado
- Consumidor de `catalog.site.changed` v2 (ms-04): con `SITE_EVENTS_ENABLED=true` refresca `sitio_nombre` y `manager_id` de todos los eventos del sitio. Es el primer consumidor de Kafka de ms-03, que hasta ahora solo publicaba.
- `V5`: columna `evento.sitio_actualizado_en`. El UPDATE solo aplica cuando el `updatedAt` del sitio es más nuevo que el último aplicado, así que un mensaje reordenado no revierte un cambio de gestora.
- Los mensajes `v1` se ignoran con un log: no traen `managerId` y no hay nada que aplicar.

### Notas
- El consumidor está apagado por defecto; sin él la revalidación sigue ocurriendo en caliente al responder reseñas (`GET /api/v1/sites/{siteId}`).
- Variables: `SITE_EVENTS_ENABLED`, `SITE_EVENTS_GROUP`, `SITE_EVENTS_TIMEOUT`.

## [Eventos de catálogo v2 2026-11-04]

### Agregado
- `activities.activity.changed` v2 y `activities.event.changed` v2 en `contracts/events/`, con sus fixtures. La v2 alinea el payload con lo que ms-03 modela: `category` como texto en lugar de `categoryId`, sin `latitude`/`longitude` (son del sitio en ms-04) y con los estados reales (`DRAFT`, `PUBLISHED`, `PAUSED`, `CANCELLED` en actividades; `DRAFT`, `PUBLISHED`, `POSTPONED`, `CANCELLED` en eventos).
- `contracts/events/envelope.v2.json`: el sobre de `envelope.v1.json` fija `eventVersion = 1`, así que un evento v2 necesita su propio sobre.
- ms-03 publica ambos eventos desde el outbox V4 al crear o modificar una actividad o un evento (`RegisterActivityUseCase`, `UpdateActivityUseCase`, `ChangeActivityStatusUseCase`, `CancelActivityUseCase`, `RegisterEventUseCase`, `ChangeEventStatusUseCase`).
- Pruebas de contrato contra los esquemas v2, validando también los fixtures.

### Limitaciones
- El encolado es best effort, igual que en reseñas: la fila se guarda primero y un fallo al encolar solo queda en el log. Actividades, eventos y outbox son PostgreSQL, así que podría hacerse atómico con el envoltorio transaccional que ya usan las reservas; queda como `TODO`.

## [Paginación del catálogo 2026-11-04]

### Cambiado
- Los listados de catálogo (`/events`, `/events/search`, `/activities`, `/activities/search`, `/activities/site/{id}`) paginan en SQL: `LIMIT`/`OFFSET` más un `COUNT` con el mismo filtro. Antes traían la tabla completa y cortaban la página en memoria; sus consultas no tenían `LIMIT`.
- Sustituidos en el puerto: `findPublished()` → `findPublishedPage(page, size)`, `searchPublishedByName` → `searchPublishedByNamePage`, `findPublishedByCategory` → `findPublishedByCategoryPage`, `findPublishedBySiteId` → `findPublishedBySiteIdPage`.
- Los puertos de caso de uso devuelven `Mono<PageResult<...>>`; los controladores usan `PageResponse.from(result, mapper)`.
- Visibilidad sin cambios: solo se publica lo que está `PUBLISHED`.

## [Paginación en base de datos 2026-11-04]

### Cambiado
- `GET /api/v1/reservations?status=` y su variante de gestora paginan en SQL: `LIMIT`/`OFFSET` más un `COUNT` del estado completo. Antes pedían hasta 200 filas y cortaban la página en memoria, así que no había página 2 ni `total` real.
- `ReservationRepositoryPort` expone `findPageByStatus` y `findPageByItemRefs`, que devuelven un `PageResult` (contenido, página, tamaño y total) creado en el dominio. `PageResponse.from(PageResult, mapper)` arma el sobre HTTP sin recalcular nada.
- Los puertos de caso de uso devuelven `Mono<PageResult<Reservation>>`; el controlador ya no hace `collectList()`.
- Un gestora sin sitios o sin ítems recibe una página vacía con `total = 0` en vez de una lista vacía.

### Pendiente
- Los listados de catálogo (`/events`, `/events/search`, `/activities`, `/activities/search`, `/activities/site/{id}`) siguen trayendo el resultado completo y cortándolo en memoria: sus consultas no tienen `LIMIT`.

## [Refresco del snapshot 2026-11-04]

### Corregido
- Responder una reseña de evento ya no confía en el `managerId` guardado al crear el evento: se consulta `GET /api/v1/sites/{siteId}` de ms-04 y manda la gestora actual. Una gestora reasignada pierde el derecho de inmediato, en vez de conservarlo hasta que alguien refresque el snapshot.
- Si ms-04 no responde, o el sitio ya no existe, se degrada al snapshot con un `WARN`: no se bloquean todas las respuestas cuando el catálogo está caído.

### Pendiente
- El snapshot `siteName`/`managerId` del evento sigue sin refrescarse desde Kafka: `catalog.site.changed` v1 no incluye `managerId` y haría falta un contrato v2.

## [Reportes de reseña 2026-11-04]

### Corregido
- `GET /api/v1/review-reports` ya no expone los reportes de todos los sitios: una `DESTINATION_MANAGER` solo ve los de sus sitios (ms-04 → sitios → eventos → reseñas → reportes) y `ADMIN` conserva el listado global. Los reportes de actividades no aparecen porque la actividad está a cargo de un guía.
- `PATCH /api/v1/review-reports/{reportId}` valida que quien resuelve sea `ADMIN` o la entidad a cargo del ítem reseñado (guía de la actividad, gestora del evento). Antes cualquier gestora podía resolver reportes ajenos. El rechazo responde 403 con `ReviewReportNotAllowedException`.

## [Eventos de reseña 2026-11-04]

### Agregado
- `reviews.review.changed` v1: al crear una reseña se escribe el evento en el outbox V4 con `reviewId`, `touristId`, `itemType`, `itemId`, `rating`, `status` y `updatedAt`. El comentario libre no se publica.
- `ReviewStatus` (`PUBLISHED`, `PENDING_REVIEW`, `HIDDEN`, `DELETED`) y `updatedAt` en el modelo y en las dos colecciones de Mongo. Las reseñas nuevas nacen `PUBLISHED`; los documentos anteriores se leen como `PUBLISHED` con `updatedAt = createdAt`.
- `ReviewResponse` expone `status` y `updatedAt`.
- Pruebas: fábrica del evento, contrato JSON contra `contracts/events/reviews.review.changed.v1.json` (`com.networknt:json-schema-validator`) y reglas del dominio.

### Limitaciones
- La reseña (MongoDB) y el outbox (PostgreSQL) no comparten transacción: si el encolado falla después de guardar, el evento se pierde y solo queda el log. Conciliar fuera de banda.
- No se publica nada al responder una reseña: el payload v1 no incluye la respuesta y se omitiría un evento sin información nueva.

## [Integración 2026-11-04]

### Integración con `microservicios`
- Se fusiona `origin/microservicios` (`a793069`) en `ms-03-symbolic-reservations`. El único conflicto fue `AGENTS.md` (borrado en el remoto y modificado aquí); se conservó la versión de la rama.
- **Roles en inglés**: ms-01 migró los códigos de rol a `TOURIST`, `GUIDE`, `SERVICE_PROVIDER`, `DESTINATION_MANAGER` y `ADMIN` (`V10__english_role_codes`). `UserRole.code()` devuelve el código inglés y los códigos en español quedan como alias de lectura (`legacyCode()`) para los tokens ya emitidos.
- **Filtro `managerId` de ms-04**: `GET /api/v1/sites?managerId=` ya está en `microservicios` (PR #36). ms-04 responde `403` cuando el `managerId` no es el del token y quien llama no es `ADMIN`; la paginación `content/page/size/total/totalPages` coincide con la que consume el adaptador de ms-03.
- **CVEs de compresión de Kafka**: se fijan `lz4-java 1.11.4` y `snappy-java 1.1.10.9`, como en ms-02 y ms-05, para que el escaneo Trivy del pipeline no marque la imagen.
- `reviews.review.changed` v1 añadió `PENDING_REVIEW`. ms-03 todavía no publica ese evento ni modela el estado de la reseña.
- 212 pruebas en verde sobre el árbol fusionado.

## [No publicado] — 2026-11-03

### Cambiado
- **JWT RS256**: el decoder valida con el JWKS de ms-01 (`JWK_SET_URI`) o con una llave pública PEM (`JWT_PUBLIC_KEY_LOCATION`). Se elimina `JWT_SECRET` y el soporte HS256.
- **Roles en inglés**: el claim `role` usa `TOURIST`, `GUIDE`, `SERVICE_PROVIDER`, `DESTINATION_MANAGER` y `ADMIN`, como ms-01 desde la migración `V10__english_role_codes`. Los códigos en español quedan como alias de lectura (`legacyCode()`) para los tokens ya emitidos; la autorización interna usa los identificadores ingleses.
- Los tokens con `scope=mfa` se rechazan como tokens de acceso.
- **Flyway**: migraciones versionadas en `db/migration` (`V1` baseline, `V2` reservas simbólicas). Se eliminan `schema.sql` y `SQL_INIT_MODE`; el DDL deja de ejecutarse en cada arranque. `V2` archiva las filas originales y mueve a cuarentena las reservas no representables.
- **Seguridad**: principal nulo → 401 en guardas y actividades; `guideId` sale del token para guías. `JWT_AUDIENCE` es obligatoria; el JWKS remoto exige HTTPS y timeouts; `scope`/`scp=mfa` se rechazan.
- **Catálogo y reservas**: el detalle público solo devuelve `PUBLISHED` a anónimos; el listado global de reservas es `ADMIN` y el de gestora queda acotado a sus sitios. Una gestora ya no puede enumerar reservas ajenas por `/tourists/{id}`, `/guides/{id}` o `/by-item`. `exists` aplica el mismo filtro de visibilidad. Los roles se comparan de forma exacta: primero el código inglés canónico y luego el alias español heredado. Swagger se desactiva por defecto.
- **Sitio del evento**: al crear un evento se consulta `GET /api/v1/sites/{siteId}` de ms-04; `siteName` y `managerId` salen del sitio, no del body. Una `DESTINATION_MANAGER` solo puede usar sus propios sitios. El `Authorization` del usuario se reenvía a ms-04.
- **Reservas por gestora**: `GET /api/v1/reservations?status=` resuelve los sitios de la gestora con el nuevo filtro `managerId` de ms-04 y devuelve solo las reservas de sus eventos/actividades. `ADMIN` conserva el listado global.
- **Usuarios de base de datos**: PostgreSQL usa `ms03_flyway` para DDL y `ms03_app` para DML; MongoDB usa `reviews_app` con `readWrite` solo en `reviews`. Los usuarios root quedan únicamente para el init y ya no hay credenciales por defecto en Compose.
- **V3**: índices para catálogo/reservas y checks `NOT VALID` de precio, cupo, duración, horario y estado en `evento`/`actividad`.
- **Errores RFC 9457**: `ProblemDetail` (`application/problem+json`) en el `GlobalExceptionHandler` y en los handlers de seguridad.
- **Paginación**: `page`/`size` en catálogo y listado de reservas por estado; `PageResponse` con `content`, `page`, `size`, `total`, `totalPages`.
- **Filtros en SQL**: los listados públicos de eventos/actividades filtran `PUBLISHED` en la consulta, no en memoria.
- **Outbox V4**: `outbox_events` recibe `bookings.reservation.changed` v1 en las transacciones de reserva; relay opcional a Kafka (`OUTBOX_ENABLED`). `NO_SHOW` queda pendiente de contrato v2.

### Migración
- Reemplazar `JWT_SECRET` por `JWK_SET_URI` (producción) o `JWT_PUBLIC_KEY_LOCATION` (local/tests). El servicio no arranca sin una fuente de llaves RS256.
- Ajustar `JWT_ISSUER=sm-xplore-ms-01` y `JWT_AUDIENCE=sm-xplore-services` para aceptar tokens de ms-01.
- Las bases existentes arrancan con baseline `0`, ejecutan `V1` (idempotente) y `V2`. Hacer backup: `V2` archiva en `reservations_legacy`/`reservation_items_legacy` y mueve a `reservations_quarantine` las reservas sin ítem, multi-ítem, con ítem inexistente, con inicio anterior a la reserva, con total no divisible o `PENDING` sin cupo.
- `V1` crea el índice de `reservation_items` como **no único** para no abortar con pares duplicados del esquema legacy; `V2` los cuenta como multi-ítem y los pone en cuarentena.
- `V2` conserva el `total` original y calcula `unit_price = round(total / number_of_people, 2)` solo si el reparto es exacto. No reescribe importes. El borrado de la tabla viva usa solo los IDs capturados en esa ejecución; una fila restaurada en cuarentena no se vuelve a borrar.
- `V2` elimina el CHECK legacy `booked_for > booked_at`, alinea `booked_for = item_starts_at` y manda a cuarentena las reservas creadas después del inicio del ítem. `JWT_ISSUER` y `JWT_AUDIENCE` ya no tienen valor por defecto.

## [No publicado] — 2026-10-26

### Añadido
- **Idempotencia en `POST /api/v1/reservations`**: header opcional `Idempotency-Key` (1–64 caracteres).
  - Índice único parcial `(tourist_id, idempotency_key)`; un reintento devuelve la reserva original sin consumir cupo.
  - El bloqueo por turista se toma antes de leer la clave, así un reintento concurrente no falla por cupo.
  - Si el índice rechaza una carrera, la transacción se revierte y se devuelve la reserva ganadora.
- **Cierre automático de reservas vencidas**: job diario (03:15 `America/Bogota`) que pasa a `NO_SHOW` las `CONFIRMED` cuyo ítem `PUBLISHED` terminó hace más de `app.reservations.auto-close.grace` (24 h por defecto). Las reservas de ítems `CANCELLED` pasan a `CANCELLED`; los ítems pausados o postergados se omiten. Un `NO_SHOW` se puede corregir a `COMPLETED` registrando la asistencia.

### Cambiado
- **Listados de reservas sin `notes`**: `?status=`, `/tourists/{id}`, `/guides/{id}` y `/by-item` usan `ReservationSummaryResponse`. El detalle `GET /{id}` conserva el texto libre.
- **Reservas simbólicas**: la reserva aparta cupo y nace `CONFIRMED`; el pago se resuelve con la entidad, fuera de la plataforma.
  - Una reserva = un ítem (`item_type`, `item_id`, `unit_price`); se elimina `reservation_items`.
  - Estados: `CONFIRMED`, `CANCELLED`, `NO_SHOW`, `COMPLETED`.
  - Creación atómica: `POST /api/v1/reservations` recibe `itemType` + `itemId`, bloquea el ítem, valida cupo y guarda en una transacción.
  - El cupo cuenta solo reservas `CONFIRMED`.
  - Cancelación del turista: hasta 24 h antes del inicio; staff sin límite.
  - `bookedFor` lo fija el servidor con el inicio del ítem; el valor del cliente se ignora.
  - Asistencia y no-show solo después del inicio.
  - Máximo 5 reservas activas por turista.

### Eliminado
- `PATCH /reservations/{id}/confirm`, `/reject` y `/request-cancellation`.
- `POST|GET /reservations/{id}/items` y `/reservation-items/**`.
- Tabla `reservation_items`, sus casos de uso, adaptadores, DTOs y tests.

### Migración
- Histórico, sustituido por `V1`/`V2`: el antiguo `schema.sql` agregaba las columnas nuevas, normalizaba los estados viejos y eliminaba reservas sin ítem.

## [No publicado] — 2026-10-25

### Añadido
- **Validar existencia de evento**: `GET /api/v1/events/{id}/exists` (200/404).
- **Consultar disponibilidad**: cupos libres de evento/actividad (`GET /api/v1/events/{id}/availability`, `GET /api/v1/activities/{id}/availability`).
- **Reportar reseña** (`POST /api/v1/reviews/{id}/report`, un reporte por usuario y reseña, 409 si se repite).
  | GET | `/api/v1/review-reports?status=PENDING` | `ADMIN`, `DESTINATION_MANAGER` |
| PATCH | `/api/v1/review-reports/{reportId}` | `ADMIN`, `DESTINATION_MANAGER` (`RESOLVED`/`DISMISSED`) |

- Los reportes viven en `review_reports`; un reporte por usuario y reseña.
- Las respuestas públicas no exponen IDs de usuario ni de quien responde.
- Solo se reseñan ítems publicados y vigentes; la guía/gestora no puede reseñar su propio ítem.
- Métricas calculadas con aggregation de Mongo (count + promedio).
- **Métricas de reputación** por ítem (`GET /api/v1/reviews/events|activities/{id}/metrics`: total y promedio).
- **Reseñas de eventos y actividades** con MongoDB reactivo.
  - Dos colecciones separadas: `event_reviews` y `activity_reviews`.
  - `user` se guarda anidado con `{ id, name }`, tomado de los claims del JWT.
  - Sin “likes / ¿te resultó útil?”.
  - Una reseña por usuario e ítem (índice único).
- **Respuesta de la entidad a la reseña**.
  - Actividad: guía dueño (`activity.guideId`).
  - Evento: gestora del sitio (`event.managerId`) o `ADMIN`.
  - Cualquier otra entidad → `403`; segunda respuesta → `409`.
- **Snapshot de sitio en eventos**: `siteId`, `siteName`, `managerId`.
  - Si crea una `DESTINATION_MANAGER`, `managerId` se toma del `sub` del JWT, no del body.
- **Reservas de eventos y actividades**.
  - Estados: `PENDING`, `CONFIRMED`, `CANCELLATION_REQUESTED`, `REJECTED`, `CANCELLED`, `NO_SHOW`, `COMPLETED`.
  - Ítems `EVENT` / `ACTIVITY` (los servicios pertenecen a ms-04).
  - Precio desde catálogo; total = suma de precios × número de personas.
  - Validación de estado publicado, vigencia y cupo disponible.
  - Código legible `R-####`.
- **Infraestructura propia** dentro de `microservices/ms-03-activities-and-events`:
  - `docker-compose.yml` con app, PostgreSQL y MongoDB.
  - `Dockerfile` multi-stage Java 25.
  - `schema.sql` idempotente.
- Cobertura de pruebas: **157 tests** unitarios/de slice.

### Endpoints nuevos
| Método | Ruta | Acceso |
|---|---|---|
| POST | `/api/v1/reviews` | Autenticado, sin reserva |
| GET | `/api/v1/reviews/events/{eventId}` | Público |
| GET | `/api/v1/reviews/activities/{activityId}` | Público |
| POST | `/api/v1/reviews/{reviewId}/response` | Entidad a cargo o ADMIN |
| POST | `/api/v1/reservations` | Autenticado (turista dueño; admin puede crear para otro) |
| GET | `/api/v1/reservations/{id}` | Dueño, guía o staff |
| GET | `/api/v1/reservations?status=` | `ADMIN`, `DESTINATION_MANAGER` |
| GET | `/api/v1/reservations/tourists/{touristId}` | Propio o staff |
| GET | `/api/v1/reservations/guides/{guideId}` | Propio o staff |
| GET | `/api/v1/reservations/by-item?itemType=&itemId=` | Guía dueño o staff |
| PATCH | `/api/v1/reservations/{id}/confirm` | Guía o staff |
| PATCH | `/api/v1/reservations/{id}/reject` | Guía o staff |
| PATCH | `/api/v1/reservations/{id}/cancel` | Dueño o staff |
| PATCH | `/api/v1/reservations/{id}/request-cancellation` | Dueño o staff |
| PATCH | `/api/v1/reservations/{id}/attendance` | Guía o staff |
| PATCH | `/api/v1/reservations/{id}/no-show` | Guía o staff |
| POST | `/api/v1/reservations/{id}/items` | Dueño o staff |
| POST | `/api/v1/reservations/{id}/items/batch` | Dueño o staff |
| GET | `/api/v1/reservations/{id}/items` | Dueño, guía o staff |
| GET | `/api/v1/reservation-items/{id}` | Dueño, guía o staff |
| GET | `/api/v1/reservation-items?type=` | `ADMIN`, `DESTINATION_MANAGER` |

### Cambios de contrato para el frontend
- `EventResponse` agrega `siteName`; `managerId` queda interno y **no** se expone públicamente.
- Las respuestas públicas de reseñas ya no incluyen `user.id` ni `responderId`.
- `EventRequest` acepta `siteName` y `managerId` (la gestora que crea el evento no puede falsear `managerId`: se sobrescribe con su JWT).
- Reseñas: `itemType` (`EVENT`/`ACTIVITY`), `itemId`, `rating` (1–5), `comment`.
- Respuesta de reseña: `{ "message": "..." }`.
- Totales de reserva ahora incluyen `numberOfPeople`.

### Decisiones
- Se puede **reseñar sin haber reservado**, pero solo ítems **publicados**, vigentes y sin auto-reseña de su guía/gestora.
- Las reseñas de eventos y actividades van en **colecciones separadas**.
- `managerId` se replica al crear el evento para autorizar respuestas sin consultar ms-04 en caliente.

### Pendiente
- Refresco de `siteName`/`managerId` si cambia la gestora del sitio (contrato con ms-04).
- Notificaciones: las maneja el front; falta definir el contrato con el back.
- Neo4j para “recibir info de eventos planificados” (recomendaciones).
- Modificar reserva (fecha/personas/pago) y su política.
- Itinerario, fuentes externas (clima/mapa/links) y pagos QR/Wompi.
- Pruebas de integración con Testcontainers y paginación de listados.

### Cómo ejecutar
```bash
cd microservices/ms-03-activities-and-events
cp .env.example .env    # definir JWK_SET_URI o JWT_PUBLIC_KEY_LOCATION
docker compose up -d --build
curl http://localhost:8082/actuator/health
```
