# Microservicio #3: Actividades y Eventos

Servicio encargado de la gestión de **Actividades** y **Eventos** de SM-Xplore.
Incluye las **Reservas** y las **Reseñas** de ambos.

## Stack

- Java 25 + Spring Boot 4.1.2
- Spring WebFlux (reactivo)
- PostgreSQL (R2DBC) para eventos, actividades y reservas
- MongoDB (reactivo) para reseñas
- JWT HS256 (mismo contrato que ms-02)

## Requisitos

- Java 25
- Docker y Docker Compose

## Ejecución con Docker

```bash
cp .env.example .env
# Edita .env y define un JWT_SECRET real:
# openssl rand -base64 48
docker compose up --build
```

Servicios:

- API: `http://localhost:8082`
- PostgreSQL: `localhost:5432`
- MongoDB: `localhost:27017`
- Swagger UI: `http://localhost:8082/swagger-ui.html`
- Health: `http://localhost:8082/actuator/health`

Para reiniciar las bases desde cero:

```bash
docker compose down -v
```

## Ejecución local

```bash
export JWT_SECRET="$(openssl rand -base64 48)"
export R2DBC_URL="r2dbc:postgresql://localhost:5432/activities_events"
export MONGODB_URI="mongodb://localhost:27017/reviews"
./gradlew bootRun
```

`JWT_SECRET` es obligatorio y debe tener al menos 32 bytes. El servicio no arranca sin él.

## Variables de entorno

| Variable | Default | Descripción |
|---|---|---|
| `SERVER_PORT` | `8082` | Puerto HTTP del servicio. |
| `APP_PORT` | `8082` | Puerto publicado por Docker Compose. |
| `R2DBC_URL` | `r2dbc:postgresql://localhost:5432/activities_events` | Conexión reactiva a PostgreSQL. |
| `POSTGRES_USER` / `POSTGRES_PASSWORD` | `postgres` / `postgres` | Credenciales de PostgreSQL. |
| `MONGODB_URI` | `mongodb://localhost:27017/reviews` | Conexión a MongoDB. |
| `JWT_SECRET` | sin valor | Obligatorio. Secreto HS256 de mínimo 32 bytes. |
| `JWT_ISSUER` | `https://activitiesevents.local` | Issuer esperado en el token. |
| `JWT_AUDIENCE` | vacío | Audiencia esperada; si se define, se valida. |
| `JWT_ROLE_CLAIM` | `role` | Claim que contiene el rol. |
| `JWT_NAME_CLAIM` | `name` | Claim que contiene el nombre. |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:3000,http://localhost:4200` | Orígenes permitidos. |
| `MONGO_AUTO_INDEX_CREATION` | `false` | Crea índices al arrancar. En Docker se activa. |
| `SQL_INIT_MODE` | `never` | Ejecuta `schema.sql` al arrancar. En Docker Compose se activa (`always`). |

## Alcance

- Actividades y eventos (registro, consulta, estado, cupos).
- Reservas de actividades y eventos.
- Reseñas de actividades y eventos.

Las reseñas viven en MongoDB; el resto en PostgreSQL. Neo4j para preferencias queda diferido.

## Endpoints

| Método | Ruta | Acceso |
|---|---|---|
| GET | `/api/v1/events` | Público |
| GET | `/api/v1/events/{eventId}` | Público |
| GET | `/api/v1/events/search?name=` | Público |
| POST | `/api/v1/events` | `ADMIN`, `DESTINATION_MANAGER` |
| PATCH | `/api/v1/events/{eventId}/status` | `ADMIN`, `DESTINATION_MANAGER` |
| PATCH | `/api/v1/events/{eventId}/attendance` | `ADMIN`, `DESTINATION_MANAGER` |
| GET | `/api/v1/activities` | Público |
| GET | `/api/v1/activities/{activityId}` | Público |
| GET | `/api/v1/activities/search?category=` | Público |
| GET | `/api/v1/activities/site/{siteId}` | Público |
| POST | `/api/v1/activities` | `ADMIN`, `DESTINATION_MANAGER`, `GUIDE` |
| PATCH | `/api/v1/activities/{activityId}` | `ADMIN`, `DESTINATION_MANAGER`, `GUIDE` |
| PATCH | `/api/v1/activities/{activityId}/availability` | `ADMIN`, `DESTINATION_MANAGER`, `GUIDE` |
| PATCH | `/api/v1/activities/{activityId}/slots` | `ADMIN`, `DESTINATION_MANAGER`, `GUIDE` |
| PATCH | `/api/v1/activities/{activityId}/status` | `ADMIN`, `DESTINATION_MANAGER`, `GUIDE` |
| POST | `/api/v1/activities/{activityId}/cancel` | `ADMIN`, `DESTINATION_MANAGER`, `GUIDE` |

Los errores usan el formato uniforme `{timestamp, status, error, message, path}`.

## Limitaciones conocidas

- Los listados públicos no paginan todavía (catalog pequeño).
- No hay bloqueo optimista en las escrituras (ver `shortcut:` en los adaptadores).
- La búsqueda por nombre usa `ILIKE` sin índice `pg_trgm`.
- El detalle por UUID no filtra por estado; los listados públicos sí (solo `PUBLISHED`).
- Faltan pruebas de integración con Testcontainers (declaradas en el build).
- Neo4j (preferencias) queda diferido.

## Reservas

| Método | Ruta | Acceso |
|---|---|---|
| POST | `/api/v1/reservations` | Autenticado (turista crea la suya; admin puede crear para otro) |
| GET | `/api/v1/reservations/{id}` | Dueño, guía de la reserva o staff |
| GET | `/api/v1/reservations?status=` | `ADMIN`, `DESTINATION_MANAGER` |
| GET | `/api/v1/reservations/tourists/{touristId}` | El propio turista o staff |
| GET | `/api/v1/reservations/guides/{guideId}` | El propio guía o staff |
| GET | `/api/v1/reservations/by-item?itemType=&itemId=` | Guía dueño del ítem o staff (eventos: staff) |
| PATCH | `/api/v1/reservations/{id}/confirm` | Guía de la reserva o staff |
| PATCH | `/api/v1/reservations/{id}/reject` | Guía de la reserva o staff |
| PATCH | `/api/v1/reservations/{id}/cancel` | Dueño o staff |
| PATCH | `/api/v1/reservations/{id}/request-cancellation` | Dueño o staff |
| PATCH | `/api/v1/reservations/{id}/attendance` | Guía de la reserva o staff |
| PATCH | `/api/v1/reservations/{id}/no-show` | Guía de la reserva o staff |
| POST | `/api/v1/reservations/{id}/items` | Dueño o staff |
| POST | `/api/v1/reservations/{id}/items/batch` | Dueño o staff |
| GET | `/api/v1/reservations/{id}/items` | Dueño, guía o staff |
| GET | `/api/v1/reservation-items/{id}` | Dueño, guía o staff |
| GET | `/api/v1/reservation-items?type=` | `ADMIN`, `DESTINATION_MANAGER` |

Reglas: los ítems de una reserva comparten tipo (`EVENT` o `ACTIVITY`); una reserva de evento admite un solo ítem; el precio sale del catálogo, nunca del cliente; las actividades deben ser del mismo guía.

## Pruebas

```bash
./gradlew test
./gradlew build
```

Las pruebas de integración usarán Testcontainers (PostgreSQL + MongoDB) y requieren Docker.
