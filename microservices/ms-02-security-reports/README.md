# Microservicio #2: Reportes de Seguridad

Servicio encargado de la gestión de **Reportes de Seguridad** de SM-Xplore.

## Requisitos

- Java 25
- Docker y Docker Compose (para la ejecución en contenedores)

## Ejecución con Docker

```bash
cp .env.example .env
# Edita .env y define un JWT_SECRET real:
# openssl rand -base64 48
docker compose up --build
```

Servicios:

- API: `http://localhost:8080`
- MongoDB: `localhost:27017`
- Swagger UI: `http://localhost:8080/swagger-ui.html`
- Health: `http://localhost:8080/actuator/health`

Para reiniciar la base de datos desde cero:

```bash
docker compose down -v
```

## Ejecución local

```bash
export JWT_SECRET="$(openssl rand -base64 48)"
export MONGODB_URI="mongodb://localhost:27017/security_reports"
./gradlew bootRun
```

`JWT_SECRET` es obligatorio y debe tener al menos 32 bytes. El servicio no arranca con el secreto de desarrollo.

## Variables de entorno

| Variable | Default | Descripción |
|---|---|---|
| `MONGODB_URI` | `mongodb://localhost:27017/security_reports` | URI de MongoDB. |
| `JWT_SECRET` | sin valor | Obligatorio. Secreto HS256 de mínimo 32 bytes. |
| `JWT_ISSUER` | `https://securityreports.local` | Issuer esperado en el token. |
| `JWT_AUDIENCE` | vacío | Audiencia esperada. Si se define, se valida el claim `aud`. |
| `JWT_ROLE_CLAIM` | `role` | Claim que contiene el rol. |
| `JWT_NAME_CLAIM` | `name` | Claim que contiene el nombre del usuario. |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:3000,http://localhost:4200` | Orígenes permitidos, separados por coma. |
| `MONGO_AUTO_INDEX_CREATION` | `false` | Crea índices al arrancar. En Docker Compose se activa; en producción se recomienda gestionarlos con migraciones. |
| `SPRINGDOC_API_DOCS_ENABLED` | `true` | Habilita `/v3/api-docs`. |
| `SPRINGDOC_SWAGGER_UI_ENABLED` | `true` | Habilita Swagger UI. |

## Contrato de reportes

- Crear: `title`, `description`, `location`, `photosUrls`.
- Actualizar: `title` y `description` mediante `PATCH /api/v1/security-reports/{reportId}/content`.
- Leer: `title`, `description`, `location`, `photosUrls`, `status`, `createdAt`.
- El usuario y su nombre se toman del JWT; no se aceptan en el body.
- Flujo de estado: `ONGOING → REPORTED` con `/close`; `REPORTED → SOLVED` con `/solve`.

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

Reportes: orden `createdAt DESC`, `_id DESC`. Comentarios: orden `createdAt ASC`, `_id ASC`.

## Seguridad

- El token debe incluir `sub` como UUID, el claim de nombre y el claim de rol.
- El `issuer` (`iss`) identifica al emisor del token; se valida contra `JWT_ISSUER` para rechazar tokens de otros emisores.
- Roles usados: `ADMIN`, `DESTINATION_MANAGER`, `GUIDE`, `SERVICE_PROVIDER`, `TOURIST`.
- `GET /security-reports` (listado global por estado) requiere `ADMIN` o `DESTINATION_MANAGER`.
- `GET /by-user/{userId}` solo permite al propio usuario o a staff.
- Los usuarios solo pueden modificar sus propios reportes, salvo staff.
- El detalle de un reporte y sus comentarios están disponibles para cualquier usuario autenticado (alertas públicas).
- El secreto JWT nunca debe commitearse.

## Migraciones y datos legados

- Al arrancar se ejecuta un backfill idempotente de `createdAt` para reportes y comentarios antiguos sin ese campo.
- Si un comentario legado no tiene `createdAt`, el mapper usa `Instant.EPOCH` como respaldo para evitar errores de paginación.
- Los índices usan sufijo `_v2` para evitar conflictos con definiciones anteriores. Los índices antiguos pueden eliminarse manualmente después de verificar el plan de consulta.

## Pruebas

```bash
./gradlew clean test
./gradlew build
```

Las pruebas de integración usan Testcontainers con `mongo:7.0` y requieren Docker.
