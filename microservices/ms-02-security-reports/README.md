# Microservicio #2: Reportes de Seguridad

Servicio encargado de la gestión de **Reportes de Seguridad** de SM-Xplore.

## Requisitos

- Java 25
- Docker y Docker Compose (para la ejecución en contenedores)

## Ejecución con Docker

```bash
# Kafka y la red compartida smxplore-net viven en infrastructure/
docker compose -f infrastructure/compose.yaml up -d

cd microservices/ms-02-security-reports
cp .env.example .env
# Configura JWK_SET_URI (producción) o JWT_PUBLIC_KEY_LOCATION (local/tests).
# ms-01 publica sus llaves en http://localhost:8081/.well-known/jwks.json
docker compose up --build
```

Servicios:

- API: `http://localhost:8080`
- MongoDB: `localhost:27017` (replica set `rs0`, necesario para transacciones)
- Kafka: `kafka:9092` dentro de `smxplore-net`
- Swagger UI: `http://localhost:8080/swagger-ui.html`
- Health: `http://localhost:8080/actuator/health`

Para reiniciar la base de datos desde cero:

```bash
docker compose down -v
```

## Ejecución local

```bash
export JWK_SET_URI="http://localhost:8081/.well-known/jwks.json"
export JWT_ISSUER="sm-xplore-ms-01"
export JWT_AUDIENCE="sm-xplore-services"
export MONGODB_URI="mongodb://localhost:27017/security_reports"
./gradlew bootRun
```

El servicio solo acepta RS256. En local/tests se puede usar `JWT_PUBLIC_KEY_LOCATION` con la llave pública PEM de ms-01 en lugar del JWKS.

## Variables de entorno

| Variable | Default | Descripción |
|---|---|---|
| `MONGODB_URI` | `mongodb://localhost:27017/security_reports` | URI de MongoDB. |
| `JWK_SET_URI` | vacío | JWKS de ms-01. Obligatorio si no se define `JWT_PUBLIC_KEY_LOCATION`. |
| `JWT_PUBLIC_KEY_LOCATION` | vacío | Llave pública PEM para local/tests. |
| `JWT_ISSUER` | `sm-xplore-ms-01` | Issuer esperado en el token. |
| `JWT_AUDIENCE` | `sm-xplore-services` | Audiencia esperada. |
| `JWT_USER_ID_CLAIM` | `userId` | Claim alternativo si no viene `sub`. |
| `JWT_ROLE_CLAIM` | `role` | Claim que contiene el rol. |
| `JWT_NAME_CLAIM` | `name` | Claim que contiene el nombre del usuario. |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:3000,http://localhost:4200` | Orígenes permitidos, separados por coma. |
| `REPORT_RATE_LIMIT_ENABLED` | `true` | Activa el límite por IP para crear reportes. |
| `REPORT_RATE_LIMIT_PER_MINUTE` | `30` | Máximo de reportes por IP por minuto. |
| `MONGO_AUTO_INDEX_CREATION` | `false` | Crea índices al arrancar. En Docker Compose se activa; en producción se recomienda gestionarlos con migraciones. |
| `KAFKA_BOOTSTRAP_SERVERS` | `localhost:29092` | Broker Kafka compartido. |
| `OUTBOX_ENABLED` | `true` | Activa el relay del outbox de reportes. |
| `OUTBOX_PRODUCER` | `ms-02` | Valor de `producer` en los eventos. |
| `OUTBOX_POLL_INTERVAL_MS` / `OUTBOX_BATCH_SIZE` | `1000` / `50` | Frecuencia y lote del relay. |
| `OUTBOX_MAX_ATTEMPTS` / `OUTBOX_*_BACKOFF` | `20` / `1s`–`1m` | Reintentos y backoff. |
| `OUTBOX_LEASE_DURATION` | `3m` | Lease del worker que publica. |
| `OUTBOX_RETENTION_DAYS` | `30` | Retención de eventos publicados/fallidos. |
| `SPRINGDOC_API_DOCS_ENABLED` | `true` | Habilita `/v3/api-docs`. |
| `SPRINGDOC_SWAGGER_UI_ENABLED` | `true` | Habilita Swagger UI. |

## Contrato de reportes

- Crear: `title`, `description`, `location`, `photosUrls`.
- Actualizar: `title` y `description` mediante `PATCH /api/v1/security-reports/{reportId}/content`.
- Leer: `title`, `description`, `location`, `photosUrls`, `status`, `createdAt`, `updatedAt`.
- El usuario y su nombre se toman del JWT; no se aceptan en el body.
- Flujo de estado: `ONGOING → REPORTED` con `/close`; `REPORTED → SOLVED` con `/solve`.
- Alertas cercanas: `GET /api/v1/security-reports/nearby?latitude=&longitude=&radiusKm=&status=&size=`.
- Cada escritura publica `security.report.changed` por el outbox transaccional de MongoDB.

## Evento `security.report.changed`

- Topic y `eventType`: `security.report.changed`.
- Productor: `ms-02`; `aggregateId` = `reportId`; key de Kafka = `aggregateId`.
- Payload: `reportId`, `reporterId`, `title`, `status`, `latitude`, `longitude`, `createdAt`, `updatedAt`.
- Contrato: `contracts/events/security.report.changed.v1.json`.
- Se publica al crear, cambiar contenido/fotos, cerrar y resolver un reporte.
- ms-05 no lo consume; está pensado para alertas, notificaciones y analítica.

## Paginación por cursor

Los listados usan scroll infinito con cursor opaco:

```http
GET /api/v1/security-reports?status=ONGOING&size=20&cursor=...
GET /api/v1/security-reports/by-user/{userId}?size=20&cursor=...
GET /api/v1/security-reports/{reportId}/comments?size=20&cursor=...
```

Respuesta:

```json
{
  "content": [],
  "nextCursor": "opaque-value",
  "hasNext": true
}
```

- `size`: entre 1 y 100. Por defecto 20.
- `cursor`: se omite en la primera petición.
- Cuando `nextCursor` es `null` o `hasNext` es `false`, no hay más páginas.

Reportes: orden `createdAt DESC`, `_id DESC`. Comentarios: orden `createdAt ASC`, `_id ASC`. La consulta `nearby` usa un índice `2dsphere` sobre `position` y ordena por distancia; no usa cursor.

## Seguridad

En `prod`, este servicio valida el claim `ver` contra ms-01 (`MS01_BASE_URL`) y exige
`mfa=true` a los administradores. La validación es fail-closed: si ms-01 no responde, las
peticiones se rechazan. En local/tests queda desactivada con `AUTH_VERSION_ENABLED=false`.

- Solo se acepta RS256; el token lo emite ms-01. Los códigos de rol en inglés son los únicos que se emiten; los alias en español (`TURISTA`, `GUIA`, `PRESTADORA_SERVICIOS`, `GESTORA_DESTINOS`) se siguen leyendo mientras expiran los tokens anteriores a `V10`.
- `/actuator/metrics` y `/actuator/prometheus` están expuestos sin JWT para el scraping; en producción deben aislarse por red (puerto de management separado o token de scrape).
- El `issuer` (`iss`) debe ser `sm-xplore-ms-01` y la audiencia `sm-xplore-services`.
- Los roles usados son los códigos en inglés: `ADMIN`, `DESTINATION_MANAGER`, `GUIDE`, `SERVICE_PROVIDER`, `TOURIST`.
- `GET /security-reports` (listado global por estado) requiere `ADMIN` o `DESTINATION_MANAGER`.
- Los usuarios solo pueden modificar sus propios reportes, salvo staff.
- El detalle de un reporte (`GET /security-reports/{reportId}`) y sus comentarios solo los ve su autor o el staff (`ADMIN`/`DESTINATION_MANAGER`): sin ese control cualquier usuario autenticado leía el título, la descripción, las fotos y el denunciante de un incidente.
- Los tokens con `scope=mfa` se rechazan: son desafíos de segundo factor, no tokens de acceso.
- La llave pública se obtiene del JWKS de ms-01 o de un PEM local; nunca se comparte una llave privada.
- Los errores usan RFC 9457 (`application/problem+json`) con `type`, `title`, `status`, `detail` e `instance`.
- Se agregan cabeceras `X-Content-Type-Options`, `X-Frame-Options`, `Referrer-Policy`, `Permissions-Policy`, `Cross-Origin-Opener-Policy` y `Cross-Origin-Resource-Policy`.
- La creación de reportes tiene un límite fijo por IP (`REPORT_RATE_LIMIT_PER_MINUTE`, 30 por defecto); al superarlo responde `429` con `Retry-After`.

## Migraciones y datos legados

- Al arrancar se ejecuta un backfill idempotente de `createdAt`, `updatedAt` y `position` (GeoJSON) para reportes antiguos.
- Si un comentario legado no tiene `createdAt`, el mapper usa `Instant.EPOCH` como respaldo para evitar errores de paginación.
- Los índices usan sufijo `_v2` para evitar conflictos con definiciones anteriores. Los índices antiguos pueden eliminarse manualmente después de verificar el plan de consulta.
- El outbox vive en `security_report_outbox` y requiere MongoDB en replica set.

## Pruebas

```bash
./gradlew clean test
./gradlew build
```

Las pruebas de integración usan Testcontainers con `mongo:7.0` en replica set y requieren Docker. La suite tiene **38 pruebas** y valida el contrato `security.report.changed`, el evento generado, la geolocalización y los errores RFC 9457.
