# Microservicio #1: Usuarios y Anomalías

Servicio encargado de la gestión de **Usuarios** y, más adelante, **Anomalías** de SM-Xplore. Incluye las **Reseñas sobre Guías y Prestadoras de Servicios**.

Estado actual: identidad, autenticación, perfiles y administración de usuarios (Java + Spring Boot). Las reseñas en MongoDB y las anomalías se incorporan en las siguientes fases.

## Stack

- Java 25
- Spring Boot 4.1.2
- Gradle 9.5.1 (wrapper)
- PostgreSQL 17 para usuarios
- MongoDB 8 para reseñas (fase siguiente)
- Mailpit para correo local

## Arquitectura

Arquitectura hexagonal / clean architecture:

```text
infrastructure -> application -> domain
```

- `domain`: modelo puro, sin Spring, JPA, Mongo ni Jackson. `Usuario` es la clase base y de ella heredan `Turista`, `Guia`, `PrestadoraServicios`, `GestoraDestinos` y `Admin`, igual que en el diagrama de dominio.
- `application`: casos de uso, puertos de entrada/salida y DTOs de aplicación. `RoleDetailsView` es solo un DTO de lectura para la API; no es una entidad de dominio.
- `infrastructure`: controladores REST, adaptadores JPA/Mongo, correo, JWT y configuración.
- Las dependencias se verifican con ArchUnit.
- Los casos de uso se escriben dentro de `TransactionPort`, implementado con `TransactionTemplate`.

```text
com.smxplore.users
├── identity
│   ├── domain/model, domain/exception
│   ├── application/port/in, application/port/out, application/service, application/dto
│   └── infrastructure/adapter/in/rest, adapter/out/persistence/jpa, adapter/out/email, adapter/out/security
└── shared/infrastructure/{config,security,rest}
```

## Ejecutar localmente

```bash
docker compose -f microservices/docker-compose.yml up -d
./gradlew :microservices:ms-01-users-and-anomalies:bootRun
```

La API queda en `http://localhost:8081`. Swagger: `http://localhost:8081/swagger-ui.html`.
Mailpit: `http://localhost:8025`.

### Variables principales

| Variable | Default local |
| --- | --- |
| `DB_URL` | `jdbc:postgresql://localhost:5432/smxplore_users` |
| `DB_USERNAME` | `smxplore` |
| `DB_PASSWORD` | `smxplore` |
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
| GET | `/api/v1/admin/users` | ADMIN |
| GET | `/api/v1/admin/users/{id}` | ADMIN |
| POST | `/api/v1/admin/users` | ADMIN, crea guía/gestora/admin con invitación |
| PATCH | `/api/v1/admin/users/{id}/status` | ADMIN |
| POST | `/api/v1/admin/users/{id}/provider-review` | ADMIN |
| POST | `/api/v1/admin/users/{id}/password-reset` | ADMIN |
| GET | `/api/v1/admin/audit-logs` | ADMIN |
| GET | `/.well-known/jwks.json` | Público |
| GET | `/actuator/health` | Público |

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
- `ver` se valida en ms-01 contra `users.auth_version`; otros servicios deben validar firma, `iss`, `aud` y `exp`.
- El servicio de reportes de seguridad debe confiar en la llave pública publicada en `/.well-known/jwks.json`.

## Pruebas

```bash
./gradlew :microservices:ms-01-users-and-anomalies:test
./gradlew :microservices:ms-01-users-and-anomalies:test --tests "com.smxplore.users.identity.infrastructure.adapter.in.rest.AuthFlowTest"
./gradlew :microservices:ms-01-users-and-anomalies:check
```

Las pruebas de integración usan Testcontainers y requieren Docker.
