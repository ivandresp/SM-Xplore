# Microservicio #1: Usuarios y Anomalías

Servicio encargado de la gestión de **Usuarios** y **Anomalías** de SM-Xplore. Incluye las **Reseñas sobre Guías y Prestadoras de Servicios**.

Estado actual: identidad, autenticación, perfiles, administración de usuarios, reseñas sobre guías y prestadoras, detección y gestión de anomalías, y monitoreo administrativo (Java + Spring Boot).

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
- `infrastructure`: controladores WebFlux, DTOs HTTP, adaptadores R2DBC y MongoDB, monitoreo, correo, JWT y configuración. Los controladores devuelven `Mono`/`Flux` y la seguridad es reactiva (`SecurityWebFilterChain`, `WebFilter`, `ReactiveJwtDecoder`).
- Las dependencias se verifican con ArchUnit.

```text
com.smxplore.users
├── domain/{model,exception,policy}
├── application/{dto,port/in,port/out,service}
└── infrastructure
    ├── adapter/in/{rest,dto,web}
    ├── adapter/out/{persistence/r2dbc,persistence/mongo,monitoring,email,security}
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
| `ANOMALY_DETECTION_ENABLED` | `true` |
| `ANOMALY_DETECTION_INTERVAL_MS` | `30000` |
| `MAIL_HOST` / `MAIL_PORT` | `localhost` / `1025` |
| `JWT_ISSUER` | `sm-xplore-ms-01` |
| `JWT_AUDIENCE` | `sm-xplore-services` |
| `JWT_TTL` | `PT60M` |
| `JWT_PRIVATE_KEY` / `JWT_PUBLIC_KEY` | Vacías en desarrollo; ver abajo |
| `JWT_KEY_STORE` | `.local/jwt/keys.pem` |
| `ACCOUNT_INVITATION_TTL` | `PT48H` |
| `AUTH_RATE_LIMIT_ENABLED` | `true` |
| `AUTH_RATE_LIMIT_PER_MINUTE` | `30` por IP |
| `LOGIN_LOCKOUT_ENABLED` | `true` |
| `LOGIN_LOCKOUT_MAX_ATTEMPTS` | `5` |
| `LOGIN_LOCKOUT_WINDOW` / `LOGIN_LOCKOUT_DURATION` | `PT15M` |
| `MFA_REQUIRED_FOR_ADMINS` | `false` en desarrollo y pruebas; `true` en `prod` |
| `MFA_CHALLENGE_TTL` | `PT5M` |
| `SECURITY_HSTS_ENABLED` | `false` en desarrollo; `true` en `prod` |
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

## Protección de acceso

- **Límite por IP**: `AuthRateLimitWebFilter` aplica una ventana fija de un minuto sobre `/api/v1/auth/login`, `/forgot-password`, `/reset-password` y `/mfa/verify`. Al superar `AUTH_RATE_LIMIT_PER_MINUTE` responde `429` con `Retry-After` y `code=RATE_LIMIT_EXCEEDED`. Se usa la dirección remota de la conexión: detrás de un proxy inverso, todas las solicitudes comparten su IP salvo que el proxy preserve la del cliente.
- **Bloqueo por cuenta**: tras `LOGIN_LOCKOUT_MAX_ATTEMPTS` fallos consecutivos dentro de la ventana, la cuenta queda bloqueada durante `LOGIN_LOCKOUT_DURATION` y responde `429` con `code=TOO_MANY_ATTEMPTS`. Un inicio de sesión exitoso limpia el contador y la recuperación de contraseña sigue disponible. El estado se guarda en `login_attempts`, no en memoria, para sobrevivir reinicios y compartirse entre instancias.
- La detección de anomalías sigue registrando los fallos por separado: el bloqueo es preventivo, la anomalía es diagnóstica.
- El bloqueo se aplica por cuenta existente y responde `429`, así que un atacante que conozca un correo puede provocar un bloqueo temporal o inferir que la cuenta existe. El límite por IP reduce ese abuso; si se necesita evitar por completo, conviene añadir un captcha o un desafío progresivo.
- **Cabeceras**: `X-Content-Type-Options`, `X-Frame-Options`, `Referrer-Policy`, `Permissions-Policy`, `Cross-Origin-Opener-Policy` y `Cross-Origin-Resource-Policy`. HSTS solo se emite con `SECURITY_HSTS_ENABLED=true`, cuando el servicio está detrás de TLS.

## Autenticación multifactor (RNF01)

TOTP (RFC 6238, HMAC-SHA1, ventana de ±1 paso) implementado sin dependencias externas. Está disponible para cualquier cuenta y es obligatoria para administradores con `MFA_REQUIRED_FOR_ADMINS=true`.

1. `POST /api/v1/users/me/mfa/enroll` devuelve `secret`, `otpauthUri` y 10 códigos de recuperación; estos se muestran una sola vez y se guardan hasheados.
2. `POST /api/v1/users/me/mfa/confirm` recibe un código TOTP y activa el segundo factor.
3. Desde ese momento `POST /api/v1/auth/login` responde `mfaRequired=true` y `mfaChallengeToken` en lugar de `accessToken`.
4. `POST /api/v1/auth/mfa/verify` con `{challengeToken, code}` devuelve el token de acceso; acepta TOTP o un código de recuperación de un solo uso.
5. `POST /api/v1/users/me/mfa/disable` exige contraseña y código.

El desafío es un JWT con `scope=mfa` y `MFA_CHALLENGE_TTL` de vigencia; el decodificador de acceso lo rechaza, así que no sirve como credencial. Cada paso TOTP se registra en `last_used_step` y no puede reutilizarse.

Con `MFA_REQUIRED_FOR_ADMINS=true`, un administrador sin MFA recibe `403` con `code=MFA_ENROLLMENT_REQUIRED` en todo salvo su perfil y la propia inscripción. El token de acceso incluye `mfa=false` hasta que complete el segundo factor.

El secreto TOTP se guarda en base32 en `mfa_enrollments` y los códigos de recuperación como hash SHA-256. Protege los respaldos de PostgreSQL y, en producción, considera cifrar la columna del secreto con una llave gestionada fuera de la base de datos.

## Perfil `prod`

`application-prod.yml` desactiva el administrador inicial, exige llaves JWT explícitas, activa MFA para administradores y HSTS, apaga Swagger, oculta los detalles de salud y habilita el apagado ordenado. `ProductionSecurityValidator` impide arrancar con llaves generadas o con la contraseña de bootstrap por defecto.

## Endpoints implementados

| Método | Ruta | Acceso |
| --- | --- | --- |
| POST | `/api/v1/auth/register/turista` | Público |
| POST | `/api/v1/auth/register/prestadora` | Público, queda `PENDIENTE` |
| POST | `/api/v1/auth/login` | Público |
| POST | `/api/v1/auth/forgot-password` | Público |
| POST | `/api/v1/auth/reset-password` | Público |
| POST | `/api/v1/auth/mfa/verify` | Público, canjea el desafío MFA por un token |
| GET | `/api/v1/users/me/mfa` | Autenticado, estado de la inscripción |
| POST | `/api/v1/users/me/mfa/enroll` | Autenticado, genera secreto TOTP y códigos de recuperación |
| POST | `/api/v1/users/me/mfa/confirm` | Autenticado, activa el segundo factor |
| POST | `/api/v1/users/me/mfa/disable` | Autenticado, exige contraseña y código |
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
| GET | `/api/v1/admin/audit-logs/export` | ADMIN, reporte CSV de auditoría |
| GET | `/api/v1/admin/monitoring/overview` | ADMIN, indicadores de usuarios, reseñas y anomalías |
| GET | `/api/v1/admin/monitoring/availability` | ADMIN, disponibilidad de PostgreSQL y MongoDB |
| GET | `/api/v1/admin/anomalies` | ADMIN, lista y filtra anomalías |
| GET | `/api/v1/admin/anomalies/{id}` | ADMIN, detalle de una anomalía |
| PATCH | `/api/v1/admin/anomalies/{id}/severity` | ADMIN, reclasifica por severidad |
| PATCH | `/api/v1/admin/anomalies/{id}/resolve` | ADMIN, marca la anomalía como resuelta |
| POST | `/api/v1/admin/anomalies/detect` | ADMIN, fuerza una evaluación de umbrales |
| GET | `/api/v1/admin/anomaly-thresholds` | ADMIN, umbrales de detección |
| PUT | `/api/v1/admin/anomaly-thresholds/{type}` | ADMIN, actualiza un umbral |
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

### Anomalías (RF99–RF102, Admin US11–US14)

Las anomalías se persisten en PostgreSQL (`anomalies` y `anomaly_thresholds`) y se detectan automáticamente:

- **Accesos sospechosos** (`ACCESO_SOSPECHOSO`): inicios de sesión fallidos dentro de una ventana.
- **Caída de servicio** (`CAIDA_SERVICIO`): verificaciones consecutivas fallidas de PostgreSQL o MongoDB.
- **Pico de solicitudes** (`PICO_SOLICITUDES`): solicitudes HTTP dentro de una ventana.

Los umbrales por defecto son 5 fallos en 300 segundos para accesos, 2 verificaciones fallidas para
disponibilidad y 300 solicitudes en 60 segundos para picos. El administrador puede cambiarlos por tipo con
`PUT /api/v1/admin/anomaly-thresholds/{type}`, incluyendo la severidad con la que nace la anomalía.

Cada anomalía nace en estado `DETECTADA`; el administrador puede reclasificarla (`BAJA`, `MEDIA`, `ALTA`,
`CRITICA`) y resolverla con una nota. Una anomalía resuelta no se vuelve a resolver y la clasificación y la
resolución quedan auditadas (`ANOMALIA_CLASIFICADA`, `ANOMALIA_RESUELTA`, `UMBRAL_ANOMALIA_ACTUALIZADO`).
La detección corre cada 30 segundos y también se puede disparar con `POST /api/v1/admin/anomalies/detect`.

Los contadores de fallos y solicitudes viven en memoria de cada instancia, por lo que la detección es local;
en un despliegue con varias réplicas cada una evalúa sus propias señales. Los umbrales y las anomalías sí son
globales porque se guardan en PostgreSQL. La verificación de disponibilidad consulta las dependencias desde
el propio servicio, así que una caída total de PostgreSQL no puede registrarse a sí misma; para ese caso se
recomienda un monitor externo que consuma la API.

### Monitoreo administrativo (Admin US9, US10 y US16)

`GET /api/v1/admin/monitoring/overview` devuelve, en una sola llamada, los indicadores que pertenecen a
ms-01:

- Usuarios: total, activos, bloqueados, suspendidos, eliminados, por rol, prestadoras aprobadas,
  prestadoras pendientes y altas de los últimos 30 días.
- Reseñas: totales, visibles, ocultas, reportadas y promedio de las visibles.
- Anomalías: totales, pendientes, resueltas y pendientes por severidad.
- Disponibilidad: estado agregado y detalle de PostgreSQL y MongoDB.

`GET /api/v1/admin/monitoring/availability` permite consultar solo la disponibilidad, con el estado y la
latencia en milisegundos de cada componente. Ninguna de las dos consultas modifica el contador de fallos que
alimenta la detección de `CAIDA_SERVICIO`.

`GET /api/v1/admin/audit-logs/export` genera un CSV UTF-8 (con BOM para Excel) de hasta 10 000 entradas,
respetando los mismos filtros del listado: actor, acción, usuario afectado y rango de fechas. El archivo
incluye fecha, actor, rol, acción, usuario afectado y detalles.

Las métricas de reservas, sitios, actividades y eventos pertenecen a ms-03/ms-04 y todavía no se agregan en
este overview. La exportación PDF de reportes (UC6) sigue pendiente; por ahora el reporte se entrega en CSV.

## Contrato JWT para otros microservicios

Tokens RS256, access-only, sin refresh token. Claims relevantes:

```json
{
  "sub": "<uuid del usuario>",
  "id": "<uuid del usuario>",
  "name": "<nombre completo>",
  "role": "ADMIN",
  "ver": 0,
  "mfa": true,
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
- `mfa` indica si el token se emitió después de superar el segundo factor; ms-02 puede exigirlo para acciones sensibles.
- Los tokens de desafío (`scope=mfa`) no son tokens de acceso y el servicio los rechaza fuera de `/api/v1/auth/mfa/verify`.
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
reseñas levantan además un contenedor MongoDB y usan `spring.mongodb.uri`. Las pruebas de anomalías usan
PostgreSQL y desactivan la detección programada con `app.anomalies.detection.enabled=false`. Las pruebas de
monitoreo levantan PostgreSQL y MongoDB para validar el overview, la disponibilidad y la exportación CSV.
Las pruebas de seguridad cubren los vectores RFC 6238, el flujo MFA completo (inscripción, desafío, replay y
recuperación), la obligatoriedad para administradores, el bloqueo por intentos, el límite por IP, las
cabeceras y el validador del perfil de producción.
