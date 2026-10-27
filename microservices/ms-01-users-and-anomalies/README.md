# Microservicio #1: Usuarios y Anomalías

Servicio encargado de la gestión de **Usuarios** y, más adelante, **Anomalías** de SM-Xplore. Incluye las **Reseñas sobre Guías y Prestadoras de Servicios**.

Estado actual: identidad, autenticación, perfiles, administración de usuarios y reseñas sobre guías y prestadoras (Java + Spring Boot). Las anomalías se incorporan en la siguiente fase.

## Stack

- Java 25
- Spring Boot 4.1.2 (Spring WebFlux)
- Spring R2DBC (`DatabaseClient`) sobre PostgreSQL 17 (sin JPA/Hibernate)
- Flyway para migraciones (usa JDBC únicamente durante el arranque)
- Spring Data MongoDB reactivo para reseñas
- Mailpit para correo local
- Gradle 9.5.1 (wrapper dentro de este directorio)

## Arquitectura

Arquitectura hexagonal / clean architecture:

```text
infrastructure -> application -> domain
```

- `domain`: modelo puro, sin Spring, Jakarta, R2DBC, Reactor ni Jackson. `Usuario` es la clase base y de ella heredan `Turista`, `Guia`, `PrestadoraServicios`, `GestoraDestinos` y `Admin`, igual que en el diagrama de dominio. Las reglas de contraseña e invitación viven en `domain/policy`.
- `application`: casos de uso, puertos de entrada/salida y DTOs. Los puertos devuelven `Mono`/`Flux`; los casos de uso se ejecutan dentro de `TransactionPort`, implementado con `TransactionalOperator`.
- `infrastructure`: controladores WebFlux, DTOs HTTP, adaptadores R2DBC y MongoDB, correo, JWT y configuración. Los controladores devuelven `Mono`/`Flux` y la seguridad es reactiva (`SecurityWebFilterChain`, `WebFilter`, `ReactiveJwtDecoder`).
- Las dependencias se verifican con ArchUnit.

```text
com.smxplore.users
├── domain/{model,exception,policy}
├── application/{dto,port/in,port/out,service}
└── infrastructure
    ├── adapter/in/{rest,dto}
    ├── adapter/out/{persistence/r2dbc,persistence/mongo,email,security}
    ├── config
    ├── rest
    └── security
```

## Ejecutar localmente

El build y el `docker-compose.yml` viven en este directorio:

```bash
docker compose up -d
./gradlew bootRun
```

La API queda en `http://localhost:8081`. Swagger: `http://localhost:8081/swagger-ui.html`.
Mailpit: `http://localhost:8025`.

### Variables principales

| Variable | Default local |
| --- | --- |
| `DB_URL` | `jdbc:postgresql://localhost:5432/smxplore_users` (solo Flyway) |
| `R2DBC_URL` | `r2dbc:postgresql://localhost:5432/smxplore_users` |
| `DB_USERNAME` | `smxplore` |
| `DB_PASSWORD` | `smxplore` |
| `MONGODB_URI` | `mongodb://smxplore:smxplore@localhost:27017/smxplore_reviews?authSource=admin` |
| `MAIL_HOST` / `MAIL_PORT` | `localhost` / `1025` |
| `JWT_ISSUER` | `sm-xplore-ms-01` |
| `JWT_AUDIENCE` | `sm-xplore-services` |
| `JWT_TTL` | `PT60M` |
| `JWT_PRIVATE_KEY` / `JWT_PUBLIC_KEY` | Vacías en desarrollo; ver abajo |
| `JWT_KEY_STORE` | `.local/jwt/keys.pem` |
| `ACCOUNT_INVITATION_TTL` | `PT48H` |
| `BOOTSTRAP_ADMIN_ENABLED` | `true` |
| `BOOTSTRAP_ADMIN_EMAIL` | `admin@smxplore.local` |
| `BOOTSTRAP_ADMIN_PASSWORD` | `ChangeMe123!` |
| `FRONTEND_BASE_URL` | `http://localhost:5173` |

Cambiar las credenciales de bootstrap fuera de desarrollo.

### Llaves JWT

Las llaves RSA se cargan, en este orden, desde `JWT_PRIVATE_KEY`/`JWT_PUBLIC_KEY` (PEM o base64
PKCS#8/X.509), desde `JWT_KEY_STORE` o, solo en el perfil `dev`, se generan una vez y se guardan en
`.local/jwt/keys.pem` (ignorado por git). El `kid` del JWKS se deriva del thumbprint de la llave
pública, así que se mantiene estable entre reinicios.

`spring.profiles.default` es `dev` para el arranque local y, en ese perfil, el servicio puede generar la
pareja de llaves si no existe. En otros entornos hay que definir `SPRING_PROFILES_ACTIVE` y entregar
las llaves explícitamente: si faltan, el servicio falla al arrancar en lugar de emitir tokens con una
llave efímera. `JWT_ALLOW_GENERATED_KEYS=true` permite habilitar la generación fuera de `dev`.

## Endpoints implementados

| Método | Ruta | Acceso |
| --- | --- | --- |
| POST | `/api/v1/auth/register/turista` | Público |
| POST | `/api/v1/auth/register/prestadora` | Público, queda `PENDIENTE` |
| POST | `/api/v1/auth/login` | Público |
| POST | `/api/v1/auth/forgot-password` | Público |
| POST | `/api/v1/auth/reset-password` | Público |
| GET | `/api/v1/users/me` | Autenticado |
| PATCH | `/api/v1/users/me` | Autenticado |
| PUT | `/api/v1/users/me/password` | Autenticado |
| GET | `/api/v1/public/users/{id}` | Público, solo guías activos y prestadoras aprobadas |
| POST | `/api/v1/reviews` | TURISTA, crea una reseña de guía o prestadora |
| POST | `/api/v1/reviews/{id}/response` | GUÍA o PRESTADORA, responde su propia reseña |
| POST | `/api/v1/reviews/{id}/report` | Autenticado, reporta una reseña |
| GET | `/api/v1/public/reviews` | Público, reseñas visibles de un destino |
| GET | `/api/v1/public/reviews/summary` | Público, reputación de un destino |
| GET | `/api/v1/admin/reviews/reported` | ADMIN, cola de moderación |
| PATCH | `/api/v1/admin/reviews/{id}/moderation` | ADMIN, oculta o mantiene una reseña |
| GET | `/api/v1/admin/users` | ADMIN |
| GET | `/api/v1/admin/users/{id}` | ADMIN |
| POST | `/api/v1/admin/users` | ADMIN, crea guía/gestora/admin con invitación |
| PATCH | `/api/v1/admin/users/{id}/status` | ADMIN |
| PATCH | `/api/v1/admin/users/{id}/role` | ADMIN, migra el perfil al nuevo rol |
| DELETE | `/api/v1/admin/users/{id}` | ADMIN, baja definitiva con anonimización |
| POST | `/api/v1/admin/users/{id}/provider-review` | ADMIN |
| POST | `/api/v1/admin/users/{id}/password-reset` | ADMIN |
| GET | `/api/v1/admin/audit-logs` | ADMIN |
| GET | `/.well-known/jwks.json` | Público |
| GET | `/actuator/health` | Público |

### Sitio web de las prestadoras

El registro de prestadoras y `PATCH /api/v1/users/me` aceptan el campo opcional `websiteUrl` (máximo
255 caracteres, solo `http://` o `https://`). Un valor vacío en la actualización elimina el enlace, y el
campo se devuelve en el perfil administrativo y en el perfil público de la prestadora.

### Roles y bajas

- `PATCH /api/v1/admin/users/{id}/role` recibe el nuevo `role` y los datos exigidos por ese rol (`dniType`/`dniNumber` para turista o guía, `license` para guía, `nit` para prestadora o gestora, etc.). Migra la fila de perfil, conserva la identidad de la cuenta e invalida los JWT anteriores incrementando `authVersion`.
- `DELETE /api/v1/admin/users/{id}` aplica una baja lógica: deja la cuenta en estado `ELIMINADO`, reemplaza nombre, correo, teléfono, documento/NIT, licencia y contraseña por valores anónimos, invalida tokens de recuperación y conserva el registro para trazabilidad. No permite eliminar la propia cuenta ni al último administrador activo.

### Reseñas

Las reseñas de guías y prestadoras viven en MongoDB (`smxplore_reviews`, colección `reviews`):

- Solo un turista puede crearlas, con calificación de 1 a 5 y comentario opcional.
- `targetType` acepta `GUIA` o `PRESTADORA_SERVICIOS`, y `targetId` es el UUID del perfil reseñado.
- Solo se reseñan guías activos y prestadoras aprobadas; hay una reseña por turista y destino (índice único).
- El destino puede responder una vez y cualquier usuario autenticado puede reportar la reseña.
- El administrador consulta las reportadas y decide `HIDE` o `KEEP`; una reseña oculta desaparece de los listados públicos y la decisión queda auditada.
- La reputación devuelve cantidad de reseñas, promedio y distribución de 1 a 5 estrellas.

## Contrato JWT para otros microservicios

Tokens RS256, access-only, sin refresh token. Claims relevantes:

```json
{
  "sub": "<uuid del usuario>",
  "id": "<uuid del usuario>",
  "name": "<nombre completo>",
  "role": "ADMIN",
  "ver": 0,
  "providerStatus": "PENDIENTE",
  "iss": "sm-xplore-ms-01",
  "aud": "sm-xplore-services",
  "iat": 0,
  "exp": 0,
  "jti": "<uuid>"
}
```

- `sub` contiene el UUID. Si el microservicio de reportes de seguridad espera `id` o `userId`, se debe alinear antes de integrarlo.
- `providerStatus` solo aparece para prestadoras.
- `role` usa los códigos en español (`TURISTA`, `GUIA`, `PRESTADORA_SERVICIOS`, `GESTORA_DESTINOS`, `ADMIN`), aunque el enum Java esté en inglés y exponga `Role.code()`.
- `ver` se valida en ms-01 contra `users.auth_version`; otros servicios deben validar firma, `iss`, `aud` y `exp`.
- El servicio de reportes de seguridad debe confiar en la llave pública publicada en `/.well-known/jwks.json`.

## Pruebas

```bash
./gradlew test
./gradlew test --tests "com.smxplore.users.infrastructure.adapter.in.rest.AuthFlowTest"
./gradlew check
```

Las pruebas de integración usan Testcontainers y requieren Docker. La aplicación configura R2DBC con
`spring.r2dbc.*`; Flyway usa `spring.flyway.url`/`user`/`password` (JDBC) solo para migrar el esquema,
porque Spring Boot desactiva el `DataSource` JDBC cuando hay un `ConnectionFactory` R2DBC. Las pruebas de
reseñas levantan además un contenedor MongoDB y usan `spring.mongodb.uri`.
