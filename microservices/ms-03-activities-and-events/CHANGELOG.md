# Changelog — ms-03 Actividades y Eventos

Cambios de este microservicio. La documentación del curso (`docs/`) no se modifica.

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
- `schema.sql` agrega las columnas nuevas, normaliza los estados viejos (`PENDING` → `CONFIRMED`, `REJECTED`/`CANCELLATION_REQUESTED` → `CANCELLED`) y elimina reservas sin ítem. El `DELETE` queda como no-op tras la primera ejecución.

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
cp .env.example .env    # definir JWT_SECRET
docker compose up -d --build
curl http://localhost:8082/actuator/health
```
