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

## Pruebas

```bash
./gradlew test
./gradlew build
```

Las pruebas de integración usarán Testcontainers (PostgreSQL + MongoDB) y requieren Docker.
