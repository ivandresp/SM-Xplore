# ms-04 — Sitios y Servicios

Microservicio de catálogo turístico de SM-Xplore. Gestiona:

- **Zonas** turísticas en PostgreSQL.
- **Sitios** turísticos y **servicios** de prestadoras en MongoDB.
- **Categorías** del catálogo en MongoDB.
- **Reseñas** de sitios y servicios en MongoDB.
- Publicación de eventos de catálogo y reseñas por **Kafka** con patrón **transactional outbox**.

Las reservas de servicios quedan para la siguiente iteración; PostgreSQL será su base de datos.

## Estado

Primera entrega implementada:

- API reactiva con WebFlux.
- Arquitectura hexagonal.
- MongoDB reactivo con transacciones y outbox.
- PostgreSQL reactivo con R2DBC para zonas.
- Seguridad RS256/JWKS o llave pública PEM.
- CRUD y consultas de categorías, zonas, sitios, servicios y reseñas.
- Filtros por zona, categoría, sitio, prestadora, estado, tipo y texto.
- Reseñas con respuesta del propietario y moderación administrativa.
- Eventos Kafka versionados según `contracts/events/`.
- Outbox con relay, reintentos y métricas.
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
│   ├── review
│   ├── service
│   ├── site
│   └── zone
└── infrastructure
    ├── adapters/in/web
    ├── adapters/out/kafka
    ├── adapters/out/mongo
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
  `dto/review` y `dto/common`.
- Los casos de uso se cablean como beans en `BeanConfiguration`.
- Los adaptadores de persistencia son explícitos: `ReactiveMongoTemplate` y `DatabaseClient`.
- Las escrituras de sitios, servicios, categorías y reseñas guardan entidad + evento de outbox
  dentro de una transacción de MongoDB.

## Bases de datos

### MongoDB

Base `smxplore` (configurable con `MONGO_DATABASE`). Colecciones:

- `categories`
- `sites`
- `services`
- `reviews`
- `outbox_event`

MongoDB debe ejecutarse como **replica set** para soportar transacciones. El `docker-compose.yml`
levanta un nodo con replica set `rs0` y un contenedor de inicialización.

### PostgreSQL

Base `smxplore` (configurable con `POSTGRES_DB`). Tabla actual:

- `zones`

El esquema se crea desde `src/main/resources/schema.sql` con `spring.sql.init.mode=always`.

## API

Todas las rutas requieren JWT salvo health, actuator y Swagger.

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

Filtros disponibles:

- Sitios: `categoryId`, `zoneId`, `status`, `search`, `page`, `size`.
- Servicios: `categoryId`, `zoneId`, `siteId`, `providerId`, `status`, `type`, `search`, `page`, `size`.
- Zonas: `status`, `type`, `search`, `page`, `size`.
- Reseñas: `itemType`, `itemId`, `touristId`, `status`, `page`, `size`.

Las escrituras aceptan `X-Correlation-Id`; si no se envía, se genera uno.

## Seguridad

- Solo RS256. No se acepta HS256.
- Producción / ms-01: `JWK_SET_URI=https://ms-01/.../.well-known/jwks.json`.
- Local/tests: `JWT_PUBLIC_KEY_LOCATION=file:/ruta/public.pem`.
- Identidad desde `sub` o, si no viene, del claim `userId` (`JWT_USER_ID_CLAIM`).
- Roles: `GESTORA_DESTINOS`, `PRESTADORA_SERVICIOS`, `TURISTA`, `ADMIN`.
- Las reglas de propiedad se validan en la capa de aplicación: una prestadora solo modifica sus
  servicios; una gestora solo modifica sus zonas/sitios; el `ADMIN` puede moderar.

## Kafka y contrato

Topics publicados:

| Topic | Evento | Contrato |
|---|---|---|
| `catalog.category.changed` | Categoría creada/actualizada | `contracts/events/catalog.category.changed.v1.json` |
| `catalog.site.changed` | Sitio creado/actualizado | `contracts/events/catalog.site.changed.v1.json` |
| `catalog.service.changed` | Servicio creado/actualizado | `contracts/events/catalog.service.changed.v1.json` |
| `reviews.review.changed` | Reseña creada/moderada | `contracts/events/reviews.review.changed.v1.json` |

- `bookings.reservation.changed` se publicará cuando se implementen las reservas.
- La key de Kafka es el `aggregateId`.
- El sobre incluye `eventId`, `eventVersion=1`, `occurredAt`, `correlationId` y `producer=ms-04`.
- `reviews.review.changed` es compartido: ms-03 publica actividades/eventos y ms-04 sitios/servicios.
- El outbox se escribe en la misma transacción Mongo que la entidad. Un relay programado publica
  los pendientes y los marca como `PUBLISHED`; los fallos se reintentan con backoff.
- Si Kafka está caído, la API sigue escribiendo entidades y el outbox queda pendiente.

### Métricas

En `/actuator/prometheus`:

- `catalog_outbox_published_total{event_type}`.
- `catalog_outbox_failed_total{event_type}`.
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
| `JWK_SET_URI` | vacío | JWKS de ms-01 |
| `JWT_PUBLIC_KEY_LOCATION` | vacío | PEM local/test |
| `JWT_ISSUER` | vacío | Issuer esperado |
| `JWT_AUDIENCE` | vacío | Audiencia esperada |
| `JWT_USER_ID_CLAIM` | `userId` | Claim de identidad si no viene `sub` |
| `OUTBOX_ENABLED` | `true` | Activa el relay |
| `OUTBOX_PRODUCER` | `ms-04` | Campo `producer` del sobre |
| `OUTBOX_POLL_INTERVAL_MS` | `1000` | Intervalo del relay |
| `OUTBOX_MAX_ATTEMPTS` | `20` | Intentos antes de marcar `FAILED` |
| `OUTBOX_INITIAL_BACKOFF` | `1s` | Backoff inicial |
| `OUTBOX_MAX_BACKOFF` | `1m` | Backoff máximo |
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

Resultado actual: **18 tests, 0 fallos; bootJar BUILD SUCCESSFUL**.

La suite usa Testcontainers con:

- `mongo:7.0` en replica set.
- `postgres:16-alpine`.
- `apache/kafka:4.1.0` para el test del outbox.

Cubre:

- flujo completo de categoría, zona, sitio, servicio y reseña;
- validación de reglas de propiedad y roles;
- rechazo de reseñas duplicadas;
- persistencia de zonas en PostgreSQL y catálogo en MongoDB;
- eventos de outbox y su serialización contra los JSON Schema;
- publicación real del outbox a Kafka.

## Pendiente

- Reservas de servicios: modelo, endpoints y eventos.
- Reindex/lectura paginada para ms-05.
- Moderación/reportes de reseñas y métricas de reputación.
- Retención de documentos `outbox_event`.
- Pruebas de carga y runbook operativo.
