# Microservicio #1: Usuarios y Anomalías

Servicio encargado de la gestión de **Usuarios** y **Anomalías** de SM-Xplore. Incluye las **Reseñas sobre Guías y Prestadoras de Servicios**.

Estado actual: identidad, autenticación, perfiles, administración de usuarios, reseñas sobre guías y prestadoras con filtro de IA, detección y gestión de anomalías, y monitoreo administrativo (Java + Spring Boot).

## Stack

- Java 25
- Spring Boot 4.1.2 (Spring WebFlux)
- Spring R2DBC (`DatabaseClient`) sobre PostgreSQL 17 (sin JPA/Hibernate)
- Flyway para migraciones (usa JDBC únicamente durante el arranque)
- Spring Data MongoDB reactivo para reseñas
- Mailpit para correo local
- OpenPDF para los reportes de auditoría en PDF
- Gradle 9.5.1 (wrapper dentro de este directorio)
- Docker (imagen multi-etapa) y Docker Compose (perfil `full`)

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
SPRING_PROFILES_ACTIVE=dev ./gradlew bootRun
```

La configuración base (`application.yml`) es fail-closed: no hay perfil por defecto ni valores de desarrollo, así que
el arranque local exige `SPRING_PROFILES_ACTIVE=dev`. `application-dev.yml` aporta el administrador inicial, las llaves
JWT autogeneradas, Swagger y las métricas. Fuera de `dev`, `local` y `test` el validador de seguridad impide arrancar
con esa configuración.

La API queda en `http://localhost:8081`. Swagger: `http://localhost:8081/swagger-ui.html`.
Mailpit: `http://localhost:8025`.

### Docker

El `Dockerfile` es multi-etapa (JDK 25 para compilar con el wrapper, JRE 25 sin privilegios para ejecutar) y el perfil
`full` de Compose levanta el servicio junto con PostgreSQL, MongoDB y Mailpit:

```bash
docker compose --profile full up -d --build
curl http://localhost:8081/actuator/health
docker compose --profile full logs -f ms-01
docker compose --profile full down
```

- La imagen se etiqueta como `smxplore/ms-01:local`, corre como el usuario `app` (sin privilegios), escucha en `8081`
  y publica un `HEALTHCHECK` contra `/actuator/health`. El contenedor usa el sistema de archivos de sólo lectura, sin
  capacidades (`cap_drop: ALL`) y con `no-new-privileges`; sólo `/app/.local` (llaves JWT y MFA) y `/tmp` son escribibles.
- La imagen fija `SPRING_PROFILES_ACTIVE=prod` en el `Dockerfile` y valida la configuración al arrancar: si faltan
  las llaves JWT, MFA no es obligatoria para administradores o HSTS está apagado, el contenedor no arranca. Para el
  perfil `full` de Compose usa `SPRING_PROFILES_ACTIVE=dev` en desarrollo o entrega las variables de producción
  (`JWT_PRIVATE_KEY`/`JWT_PUBLIC_KEY`, `BOOTSTRAP_ADMIN_*`, `MFA_SECRET_KEY`, CORS) o monta el almacén de llaves en
  `/app/.local/jwt`.
- `docker compose up -d` sin perfil sigue levantando sólo la infraestructura para `./gradlew bootRun`.

### Integración continua

`.github/workflows/ms-01-ci.yml` se ejecuta en cada push o pull request que toque el servicio:

1. `verify`: JDK 25 y `./gradlew check` (pruebas con Testcontainers, ArchUnit y Spotless), con los reportes publicados
   como artefacto.
2. `docker`: construye la imagen con Buildx y caché de GitHub Actions, la analiza con Trivy (falla ante
   vulnerabilidades `CRITICAL`/`HIGH` con corrección disponible), levanta el stack con el perfil `full` y espera a
   que `/actuator/health` responda `UP`; si falla, imprime los logs del contenedor.

`/.github/dependabot.yml` propone actualizaciones semanales de Gradle, GitHub Actions y la imagen base.

### Variables principales

| Variable | Default local |
| --- | --- |
| `SPRING_PROFILES_ACTIVE` | Sin valor por defecto: `dev` en local, `prod` en la imagen |
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
| `JWT_ALLOW_INSECURE_JWKS` | `false`; `true` solo en el perfil `dev` |
| `ACCOUNT_INVITATION_TTL` | `PT48H` |
| `AUTH_RATE_LIMIT_ENABLED` | `true` |
| `AUTH_RATE_LIMIT_PER_MINUTE` | `30` por IP |
| `AUTH_ACCOUNT_MAX_ATTEMPTS` | `10` fallos por cuenta y ventana (`AUTH_ACCOUNT_WINDOW`, `PT15M`) |
| `AUTH_TRUSTED_PROXIES` | `127.0.0.1/32,::1/128,172.16.0.0/12,10.0.0.0/8,192.168.0.0/16` |
| `LOGIN_LOCKOUT_ENABLED` | `true` |
| `LOGIN_LOCKOUT_MAX_ATTEMPTS` | `5` |
| `LOGIN_LOCKOUT_WINDOW` / `LOGIN_LOCKOUT_DURATION` | `PT15M` |
| `MFA_REQUIRED_FOR_ADMINS` | `true` en la base y en `prod`; `false` en `dev` y pruebas |
| `MFA_CHALLENGE_TTL` | `PT5M` |
| `MFA_SECRET_KEY` | Vacía en desarrollo (llave local); obligatoria fuera de desarrollo: 32 bytes en Base64 |
| `INTERNAL_API_KEY` | Clave maestra HMAC; vacía en desarrollo deshabilita `/internal/**` (`503`) |
| `INTERNAL_ALLOWED_SERVICES` | Lista opcional de servicios autorizados (coma); vacía acepta cualquier servicio bien firmado |
| `INTERNAL_MAX_CLOCK_SKEW` | `PT5M`, tolerancia del `X-Internal-Timestamp` |
| `INTERNAL_RATE_LIMIT_PER_MINUTE` | `60` peticiones por servicio y minuto |
| `RETENTION_ENABLED` | `true` |
| `RETENTION_AUTH_ARTIFACTS_DAYS` | `30` |
| `RETENTION_AUDIT_LOG_DAYS` | `1825` (cinco años) |
| `SECURITY_HSTS_ENABLED` | `true` en la base y en `prod`; `false` en `dev` y pruebas |
| `REVIEW_AI_ENABLED` | `false`; activa el filtro de reseñas con IA |
| `LITTLELLM_URL` / `LITTLELLM_API_KEY` | URL y llave del servidor LLM local |
| `LITTLELLM_MODEL` | `littlellm` |
| `REVIEW_AI_TIMEOUT` | `PT5S` |
| `REVIEW_AI_THRESHOLD` | `0.7` |
| `REVIEW_AI_TEMPERATURE` | `0.0` (muestreo voraz: veredictos estables y reproducibles) |
| `REVIEW_AI_ACTION` | `PENDING_REVIEW` (`HIDE` para ocultar de inmediato) |
| `REVIEW_AI_FAIL_OPEN` | `false` en la base y en `prod`; `true` solo en `dev` |
| `BOOTSTRAP_ADMIN_ENABLED` | `false` en la base; `true` en el perfil `dev` con credenciales de ejemplo |
| `BOOTSTRAP_ADMIN_EMAIL` | `admin@smxplore.local` solo en `dev` |
| `BOOTSTRAP_ADMIN_PASSWORD` | `ChangeMe123!` solo en `dev`; fuera de desarrollo debe ser distinta |
| `FRONTEND_BASE_URL` | `http://localhost:5173` |
| `FRONTEND_VERIFY_EMAIL_PATH` | `/verificar-correo` |
| `EMAIL_VERIFICATION_ENABLED` | `true`: exige correo verificado para publicar reseñas |
| `EMAIL_VERIFICATION_TTL` | `PT24H` |
| `ACTUATOR_PUBLIC_METRICS` | `false`: métricas y Prometheus exigen JWT; `true` solo en `dev` |

Cambiar las credenciales de bootstrap fuera de desarrollo.

### Llaves JWT

Las llaves RSA se cargan, en este orden, desde `JWT_PRIVATE_KEY`/`JWT_PUBLIC_KEY` (PEM o base64
PKCS#8/X.509), desde `JWT_KEY_STORE` o, solo en el perfil `dev`, se generan una vez y se guardan en
`.local/jwt/keys.pem` (ignorado por git). El `kid` del JWKS se deriva del thumbprint de la llave
pública, así que se mantiene estable entre reinicios.

`spring.profiles.default` ya no existe: el perfil se elige explícitamente con `SPRING_PROFILES_ACTIVE`. Solo en el
perfil `dev` el servicio puede generar la pareja de llaves si no existe y publicar un JWKS con llave de ejemplo
(`JWT_ALLOW_INSECURE_JWKS=true`). En cualquier otro entorno las llaves deben entregarse por variables o almacén; si
faltan, el servicio falla al arrancar en lugar de emitir tokens con una llave efímera.

## Protección de acceso

- **Límite por IP**: `AuthRateLimitWebFilter` aplica una ventana fija de un minuto sobre `/api/v1/auth/login`, `/forgot-password`, `/reset-password`, `/mfa/verify` y los registros (`/register/turista`, `/register/prestadora`, `/register/guia`, `/register/gestora`). Al superar `AUTH_RATE_LIMIT_PER_MINUTE` responde `429` con `Retry-After` y `code=RATE_LIMIT_EXCEEDED`. La IP del cliente se resuelve con `ClientAddressResolver`: solo si la conexión llega desde una red de `AUTH_TRUSTED_PROXIES` (el gateway) se acepta `X-Real-IP`; `X-Forwarded-For` nunca se lee, así que un cliente no puede elegir su propia IP.
- **Límite por cuenta**: además del límite por IP, `InMemoryAccountAttemptLimiter` cuenta los fallos por correo en `/auth/login` y por usuario en `/auth/mfa/verify` (`AUTH_ACCOUNT_MAX_ATTEMPTS`, 10 en `AUTH_ACCOUNT_WINDOW`, `PT15M`). Rotar direcciones no evita el cupo de la cuenta y el bloqueo por cuenta no revela si la cuenta existe.
- **Respuesta uniforme ante fallos de acceso (MS01-04)**: un correo inexistente, una contraseña incorrecta, una cuenta inactiva y una cuenta bloqueada responden todos `401` con `code=INVALID_CREDENTIALS` y el mismo mensaje, sin `retryAfterSeconds` ni códigos que distingan el caso. Así, comparar respuestas ya no sirve para enumerar cuentas. En el registro, `EMAIL_ALREADY_EXISTS` (`409`) sí confirma la existencia del correo: es un riesgo aceptado y documentado porque el registro público necesita dar una respuesta útil; lo mitiga el límite por IP añadido a los dos endpoints de registro. Si se quiere cerrar por completo, el registro debe responder `202` genérico y confirmar por correo.
- **Bloqueo por cuenta (MS01-05)**: tras `LOGIN_LOCKOUT_MAX_ATTEMPTS` fallos consecutivos dentro de la ventana, la cuenta queda bloqueada durante `LOGIN_LOCKOUT_DURATION`; el estado se guarda en `login_attempts` para sobrevivir reinicios y compartirse entre instancias. Un inicio de sesión exitoso limpia el contador. Al bloquearse se envía un correo de aviso (`EmailAccountLockedNotifier`) y el usuario puede desbloquearse restableciendo la contraseña, porque `ResetPasswordService` limpia el contador persistente y el límite por cuenta. El bloqueo sigue siendo un vector de denegación de servicio para un atacante que conozca el correo; se acepta y se mitiga con la notificación y el autodesbloqueo (un captcha o un desafío progresivo lo cerrarían del todo).
- La detección de anomalías sigue registrando los fallos por separado: el bloqueo es preventivo, la anomalía es diagnóstica.
- **Cabeceras**: `X-Content-Type-Options`, `X-Frame-Options`, `Referrer-Policy`, `Permissions-Policy`, `Cross-Origin-Opener-Policy` y `Cross-Origin-Resource-Policy`. HSTS solo se emite con `SECURITY_HSTS_ENABLED=true`, cuando el servicio está detrás de TLS.
- **Enumeración por tiempos**: un correo inexistente también ejecuta una comparación BCrypt señuelo, así que el inicio de sesión tarda lo mismo exista o no la cuenta.
- **Cierre de sesión**: `POST /api/v1/users/me/logout` incrementa `auth_version` y descarta la versión cacheada, de modo que los tokens ya emitidos dejan de valer de inmediato en ms-01. Al ser tokens sin estado, otros servicios los aceptarán hasta su `exp`; el contrato JWT pide validar `ver` contra ms-01 si necesitan revocación inmediata.

## Autenticación multifactor (RNF01)

TOTP (RFC 6238, HMAC-SHA1, ventana de ±1 paso) implementado sin dependencias externas. Está disponible para cualquier cuenta y es obligatoria para administradores con `MFA_REQUIRED_FOR_ADMINS=true`.

1. `POST /api/v1/users/me/mfa/enroll` recibe `{password}` y devuelve `secret`, `otpauthUri` y 10 códigos de recuperación; estos se muestran una sola vez y se guardan hasheados.
2. `POST /api/v1/users/me/mfa/confirm` recibe `{code, password}` y activa el segundo factor; confirmar incrementa `authVersion` (los tokens emitidos antes dejan de valer), deja auditoría `MFA_ACTIVADO` y envía un correo de aviso.
3. Desde ese momento `POST /api/v1/auth/login` responde `mfaRequired=true` y `mfaChallengeToken` en lugar de `accessToken`.
4. `POST /api/v1/auth/mfa/verify` con `{challengeToken, code}` devuelve el token de acceso; acepta TOTP o un código de recuperación de un solo uso.
5. `POST /api/v1/users/me/mfa/disable` exige contraseña y código.

Exigir la contraseña actual en la inscripción y la confirmación evita que un token robado sirva para inscribir un
autenticador controlado por el atacante; es un cambio incompatible para clientes que llamaban `enroll` sin cuerpo.
La inscripción y la confirmación responden `400` si falta la contraseña y `400 INVALID_CURRENT_PASSWORD` si no coincide.

El desafío es un JWT con `scope=mfa` y `MFA_CHALLENGE_TTL` de vigencia; el decodificador de acceso lo rechaza, así que no sirve como credencial. Cada paso TOTP se registra en `last_used_step` y no puede reutilizarse.

El desafío también está protegido contra reutilización y contra cambios de sesión (MS01-06 y MS01-08): incluye un `jti` que se persiste en `mfa_challenges` y se marca como usado en el primer canje exitoso, de modo que el mismo `mfaChallengeToken` no sirve dos veces; y lleva el claim `ver`, que `/api/v1/auth/mfa/verify` compara con el `authVersion` vigente de la cuenta. Si el usuario cambió la contraseña, cerró sesión o fue bloqueado entre el login y la verificación, el desafío se rechaza y hay que iniciar sesión de nuevo. La verificación además tiene límite por cuenta (`AUTH_ACCOUNT_MAX_ATTEMPTS`), para que probar códigos desde muchas IPs no baste.

Con `MFA_REQUIRED_FOR_ADMINS=true`, un administrador sin MFA recibe `403` con `code=MFA_ENROLLMENT_REQUIRED` en todo salvo su perfil y la propia inscripción. El token de acceso incluye `mfa=false` hasta que complete el segundo factor.

El secreto TOTP se cifra con AES-GCM antes de guardarse en `mfa_enrollments`; la llave se toma de
`MFA_SECRET_KEY` (32 bytes en Base64) o, en desarrollo, de `.local/mfa/secret.key`. Los códigos de recuperación
tienen 160 bits de entropía (20 bytes en Base32, 32 caracteres agrupados de 4 en 4) y se guardan con **SHA-256
con sal aleatoria de 16 bytes por código** (`v1$<sal>$<hash>`, ver `SaltedSha256RecoveryCodeHasher`), no con un
hash desnudo: un volcado de `mfa_recovery_codes` ya no permite probar un diccionario compartido entre usuarios
ni correlacionar códigos iguales. Protege los respaldos de PostgreSQL y la llave fuera de la base de datos.

Los códigos de recuperación se pueden rotar sin desactivar el segundo factor con
`POST /api/v1/users/me/mfa/recovery-codes` (contraseña + código TOTP): invalida los anteriores y devuelve 10 nuevos.
La regeneración queda auditada como `CODIGOS_RECUPERACION_REGENERADOS`.

## Perfil `prod`

`application-prod.yml` desactiva el administrador inicial, exige llaves JWT explícitas, activa MFA para administradores y HSTS, apaga Swagger, oculta los detalles de salud y habilita el apagado ordenado. `ProductionSecurityValidatorRunner` se ejecuta en todos los perfiles: en `dev`, `local` y `test` solo advierte, y en cualquier otro perfil detiene el arranque si hay llaves JWT ausentes o autogeneradas, `JWT_ALLOW_INSECURE_JWKS=true`, contraseña de bootstrap por defecto o vacía, MFA opcional para administradores o HSTS deshabilitado. `server.forward-headers-strategy` es `none` en `prod`: la IP del cliente se resuelve solo con proxies de confianza. El perfil `prod` mantiene `REVIEW_AI_FAIL_OPEN=false`: si el filtro de IA no responde, la reseña no se publica (`503 REVIEW_FILTER_UNAVAILABLE`).

El administrador inicial solo se crea si la tabla `users` está vacía, es decir, en el primer arranque real de una instalación nueva (MS01-12). Si más adelante se elimina o se le cambia el rol, no se recrea con la contraseña de la variable de entorno; la creación queda auditada como `ADMIN_INICIAL_CREADO`. Para instalaciones existentes, crea administradores desde `POST /api/v1/admin/users` o con el flujo de invitación.

## Endurecimiento MS01-13 a MS01-19 (fase BAJA)

- **MS01-13 (CSV):** toda celda de la exportación de auditoría que empiece por `=`, `+`, `-`, `@`, tabulador o
  retorno de carro se prefija con un apóstrofo, igual que en ms-03, para que Excel o LibreOffice no la evalúen como
  fórmula (DDE / exfiltración). El PDF no es vulnerable porque se dibuja como texto, no como fórmulas.
- **MS01-14 (Actuator):** solo `health` e `info` son públicos. `metrics` y `prometheus` exigen JWT salvo que se
  habilite `ACTUATOR_PUBLIC_METRICS=true` (el perfil `dev` lo hace para trabajar en local). En `prod` la exposición
  queda en `health,info`; el gateway además devuelve 404 para `/actuator/**` desde fuera.
- **MS01-15 (revocación):** la caché de `authVersion` baja a 15 s y, además, cada operación que incrementa `ver`
  (cambio y restablecimiento de contraseña, cambio de rol o estado, baja y aprobación/rechazo de prestadora) descarta
  la entrada de caché del usuario en el acto. Si hay réplicas, cada instancia mantiene su propia caché: el TTL acota
  la ventana máxima de inconsistencia.
- **MS01-16 (validador de arranque):** fuera de `dev`/`local`/`test` el arranque falla si faltan llaves JWT reales,
  si `JWT_ALLOW_INSECURE_JWKS=true`, si `JWT_TTL` supera 12 h, si el bootstrap está activo con contraseña vacía, por
  defecto o débil, si MFA no es obligatorio para administradores, si `MFA_SECRET_KEY` está vacía, si HSTS está
  deshabilitado, si `INTERNAL_API_KEY` no está configurada o si CORS permite `*` o un origen local.
- **MS01-17 (llaves de desarrollo):** el PEM que se autogenera en `.local/jwt/keys.pem` se crea con permisos `600`
  y su directorio con `700` cuando el sistema de archivos soporta POSIX; en otros sistemas solo se documenta.
- **MS01-18 (verificación de correo):** al registrarse (turista o prestadora) se emite un token de un solo uso de
  24 h (`password_reset_tokens.purpose = EMAIL_VERIFICATION`) y se envía un enlace
  `FRONTEND_BASE_URL + FRONTEND_VERIFY_EMAIL_PATH?token=...`. `POST /api/v1/auth/verify-email` consume el token y
  marca la cuenta como verificada (auditoría `CORREO_VERIFICADO`); `POST /api/v1/users/me/email-verification`
  reenvía el enlace (invalida los anteriores). Las cuentas sin verificar pueden iniciar sesión y editar su perfil,
  pero no publicar reseñas (`403 FORBIDDEN_OPERATION`). La tabla `email_verifications` marca como verificadas las
  cuentas que ya existían en la migración `V12`; las cuentas creadas por un administrador deben verificar su correo
  igual que el resto. Riesgo residual aceptado: no se consulta HIBP (no hay servicio externo en la infraestructura
  del proyecto) y no se verifica la entregabilidad real del buzón, solo la posesión del enlace.
- **MS01-19 (contraseñas):** además de 12–72 caracteres se exigen al menos tres de cuatro clases (minúsculas,
  mayúsculas, dígitos, símbolos) y se rechaza una lista local de contraseñas comunes o previsibles. Los mensajes de
  error son genéricos para no filtrar la causa.

## Fase informativa MS01-20 a MS01-24

- **MS01-20 (perfil público y datos profesionales):** `GET /api/v1/public/users/{id}` solo devuelve perfiles
  disponibles (guías activos y prestadoras aprobadas) y nunca expone correo, teléfono ni documentos de identidad. Sí
  publica la licencia del guía y el NIT de la prestadora porque la app ciudadana los usa para verificar que el
  prestador es real; es una **decisión de producto aceptada** que esos identificadores profesionales sean consultables
  y por tanto raspables. Si el negocio cambia de criterio, basta con recortar `PublicProfileDetails`.
- **MS01-21 (tokens de desafío MFA):** ms-01 ya rechaza un token con `scope=mfa` en su decodificador de acceso y lo
  prueba `MfaChallengeTokenContractTest`. **Obligación para ms-02..ms-05:** sus decodificadores deben rechazar tokens
  con `scope=mfa` (o usar un `aud` distinto para el desafío); ms-01 no puede imponerlo desde su código. Pendiente del
  dueño de cada servicio, anotado en el contrato JWT.
- **MS01-22 (cuota de reportes de reseñas):** además del duplicado por reseña, cada turista puede reportar como máximo
  `REVIEW_REPORT_MAX_PER_WINDOW` (20) reseñas por ventana `REVIEW_REPORT_WINDOW` (`PT24H`). Al agotarse responde `429`
  con `code=REPORT_LIMIT_EXCEEDED` y `Retry-After`. La cuota es en memoria y por instancia, así que con varias réplicas
  el límite efectivo se multiplica; riesgo aceptado y documentado. Los reportes no se auditan en `audit_logs` (deuda
  anotada); el buzón de moderación sigue ordenando por `updatedAt`.
- **MS01-23 (ventana de revocación y TTL):** el TTL por defecto baja de 60 a 30 minutos (`JWT_TTL=PT30M`). No hay
  refresh token. `logout`, cambio/restablecimiento de contraseña, cambio de rol o estado y baja incrementan `ver` y en
  ms-01 la sesión se invalida en el acto (caché de 15 s); en los demás servicios el token sigue siendo aceptado hasta
  `exp` salvo que validen `ver` contra `GET /api/v1/users/me/auth-version`, como recomienda el contrato. No se añade
  lista de revocación por `jti` en esta fase: riesgo residual aceptado y recomendación firmada en el contrato.
- **MS01-24 (limitación de anomalías):** las anomalías del mismo tipo ya no se duplicaban mientras hubiera una
  pendiente; ahora además hay un enfriamiento `ANOMALY_MIN_INTERVAL` (`PT10M`) entre creaciones del mismo tipo. Las
  señales siguen siendo contadores globales por instancia, así que un atacante todavía puede provocar como máximo una
  anomalía por tipo y ventana de enfriamiento (fatiga de alertas acotada, no eliminada); riesgo aceptado y documentado.
  La detección no bloquea tráfico; recomendación futura: correlacionar por IP/cuenta y separar tráfico autenticado.

## Retención de datos y ARCO (Ley 1581)

La política completa (finalidades, plazos, procedimiento ARCO y canal de atención) se entrega con la documentación del proyecto, fuera de este repositorio.
La aplicación aplica estas reglas todos los días a las 03:30 (`RetentionScheduler`):

- Artefactos de autenticación con más de `RETENTION_AUTH_ARTIFACTS_DAYS` (30 por defecto): tokens de restablecimiento,
  contadores de intentos fallidos y códigos de recuperación ya usados.
- Entradas de auditoría con más de `RETENTION_AUDIT_LOG_DAYS` (1825 por defecto, cinco años).

La baja de una cuenta anonimiza sus datos personales, invalida sus sesiones y conserva reseñas y auditoría sin
asociarlas a una persona identificable. Los plazos y el horario se cambian por variables de entorno sin recompilar.

## Endpoints implementados

| Método | Ruta | Acceso |
| --- | --- | --- |
| POST | `/api/v1/auth/register/turista` | Público |
| POST | `/api/v1/auth/register/prestadora` | Público, queda `PENDIENTE` |
| POST | `/api/v1/auth/register/guia` | Público, queda `PENDIENTE` y sin acceso hasta que una gestora la apruebe |
| POST | `/api/v1/auth/register/gestora` | Público, queda `PENDIENTE` y sin acceso hasta que un administrador la apruebe |
| POST | `/api/v1/auth/login` | Público |
| POST | `/api/v1/auth/forgot-password` | Público |
| POST | `/api/v1/auth/reset-password` | Público |
| POST | `/api/v1/auth/mfa/verify` | Público, canjea el desafío MFA por un token |
| POST | `/api/v1/auth/verify-email` | Público, consume el token de verificación de correo |
| POST | `/api/v1/users/me/email-verification` | Autenticado, reenvía el enlace de verificación |
| GET | `/api/v1/users/me/mfa` | Autenticado, estado de la inscripción |
| POST | `/api/v1/users/me/mfa/enroll` | Autenticado, exige contraseña; genera secreto TOTP y códigos de recuperación |
| POST | `/api/v1/users/me/mfa/confirm` | Autenticado, exige contraseña y código; activa el segundo factor y revoca sesiones |
| POST | `/api/v1/users/me/mfa/disable` | Autenticado, exige contraseña y código |
| POST | `/api/v1/users/me/mfa/recovery-codes` | Autenticado, rota los códigos de recuperación |
| GET | `/api/v1/users/me` | Autenticado |
| PATCH | `/api/v1/users/me` | Autenticado |
| PUT | `/api/v1/users/me/password` | Autenticado |
| POST | `/api/v1/users/me/logout` | Autenticado, revoca todos los tokens de la cuenta |
| GET | `/api/v1/public/users/{id}` | Público, solo guías activos y prestadoras aprobadas |
| POST | `/api/v1/reviews` | TOURIST, crea una reseña de guía o prestadora |
| POST | `/api/v1/reviews/{id}/response` | GUÍA o PRESTADORA, responde su propia reseña |
| POST | `/api/v1/reviews/{id}/report` | Autenticado, reporta una reseña |
| GET | `/api/v1/public/reviews` | Público, reseñas visibles de un destino |
| GET | `/api/v1/public/reviews/summary` | Público, reputación de un destino |
| GET | `/api/v1/admin/reviews/reported` | ADMIN, cola de moderación |
| GET | `/api/v1/admin/reviews/ai-flagged` | ADMIN, reseñas retenidas por el filtro de IA |
| PATCH | `/api/v1/admin/reviews/{id}/moderation` | ADMIN, oculta o mantiene una reseña |
| GET | `/api/v1/admin/users` | ADMIN |
| GET | `/api/v1/admin/users/{id}` | ADMIN |
| POST | `/api/v1/admin/users` | ADMIN, crea guía/gestora/admin con invitación |
| PATCH | `/api/v1/admin/users/{id}/status` | ADMIN |
| PATCH | `/api/v1/admin/users/{id}/role` | ADMIN, migra el perfil al nuevo rol |
| DELETE | `/api/v1/admin/users/{id}` | ADMIN, baja definitiva con anonimización |
| POST | `/api/v1/admin/users/{id}/provider-review` | ADMIN |
| POST | `/api/v1/admin/users/{id}/guide-review` | ADMIN, aprueba o rechaza el alta de un guía |
| GET | `/api/v1/registrations` | ADMIN o GESTORA_DESTINOS, cola de altas `PENDIENTE`/`RECHAZADO` (guías, prestadoras y gestoras); `role=DESTINATION_MANAGER` solo lo puede consultar ADMIN |
| POST | `/api/v1/registrations/{id}/review` | ADMIN o GESTORA_DESTINOS, aprueba o rechaza un alta; el motivo es obligatorio al rechazar y `APROBADO` es terminal (revocar = estado de usuario). El alta de una gestora solo la puede decidir ADMIN |
| POST | `/api/v1/admin/users/{id}/password-reset` | ADMIN |
| GET | `/api/v1/admin/audit-logs` | ADMIN |
| GET | `/api/v1/admin/audit-logs/export` | ADMIN, reporte CSV de auditoría |
| GET | `/api/v1/admin/audit-logs/export/pdf` | ADMIN, reporte PDF de auditoría (UC6) |
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
| GET | `/internal/users/{userId}/contact` | Interno, firmado con `X-Internal-Service` + `X-Internal-Timestamp` + `X-Internal-Signature` (sin JWT) |
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
- `targetType` acepta `GUIDE` o `SERVICE_PROVIDER`, y `targetId` es el UUID del perfil reseñado. Es un tipo de destino de reseña, no un código de rol; los códigos históricos se aceptan como alias.
- Solo se reseñan guías activos y prestadoras aprobadas; hay una reseña por turista y destino (índice único).
- El destino puede responder una vez y cualquier usuario autenticado puede reportar la reseña.
- El administrador consulta las reportadas y decide `HIDE` o `KEEP`; una reseña oculta desaparece de los listados públicos y la decisión queda auditada.
- La reputación devuelve cantidad de reseñas, promedio y distribución de 1 a 5 estrellas.
- **Anonimato del listado público (MS01-10)**: `GET /api/v1/public/reviews` devuelve `PublicReviewView`, que no incluye `touristId` ni ningún dato del autor. Antes el `touristId` viajaba en la respuesta y coincide con el claim `sub` del JWT, así que cualquiera podía reconstruir el recorrido de un turista (destinos, fechas y calificaciones) y cruzarlo con otros servicios. El identificador sigue disponible en los DTOs internos y administrativos.

#### Filtro de reseñas con IA (review bombing)

Cuando `REVIEW_AI_ENABLED=true`, cada reseña con texto se analiza antes de guardarse contra un servidor LLM local
compatible con la API de chat de OpenAI (`POST /v1/chat/completions`). El adaptador envía `LITTLELLM_URL`, la
llave en `Authorization: Bearer` (cabecera y prefijo configurables) y el modelo `LITTLELLM_MODEL`.

- El prompt pide un JSON `{"flagged", "score", "categories", "reason"}` y evalúa por igual elogios y ataques coordinados, spam, extorsión y texto genérico.
- La temperatura por defecto es `REVIEW_AI_TEMPERATURE=0.0`: es una tarea de clasificación, no de generación, así que se usa muestreo voraz para que la misma reseña reciba el mismo veredicto y el umbral no oscile. Súbela solo si el servidor LLM lo exige (rango 0–2).
- Si `flagged=true` y `score >= REVIEW_AI_THRESHOLD`, la reseña queda en `PENDIENTE_REVISION` (acción `PENDING_REVIEW`) o directamente `HIDDEN` (acción `HIDE`). En ambos casos sale de los listados públicos y de la reputación.
- El administrador la ve en `GET /api/v1/admin/reviews/ai-flagged` con la puntuación, categorías, motivo y modelo, y la resuelve con el mismo `PATCH /moderation` (`KEEP` la publica, `HIDE` la confirma).
- El resultado se persiste en la reseña (`aiStatus`, `aiScore`, `aiCategories`, `aiReason`, `aiModel`, `aiCheckedAt`).
- El texto de la reseña viaja delimitado por un marcador aleatorio por petición (`<resena-XXXX>…</resena-XXXX>`) y el prompt lo declara explícitamente como **datos no confiables, nunca instrucciones** (MS01-09). Además se recortan los caracteres de control y la longitud se limita a 2000 caracteres. Un intento de inyectar instrucciones no cambia el rol del mensaje; aun así el filtro es una capa de apoyo, no la única defensa: la moderación humana sigue disponible.
- La respuesta del LLM se valida de forma estricta: `flagged` debe ser booleano, `score` numérico entre 0 y 1 y `categories` una lista. Cualquier desviación del contrato se trata como `UNAVAILABLE`, no como veredicto limpio.
- Si el LLM falla, expira (`REVIEW_AI_TIMEOUT`) o devuelve un formato inválido, la reseña se guarda con `aiStatus=UNAVAILABLE` y se publica solo cuando `REVIEW_AI_FAIL_OPEN=true`; en la base y en `prod` el valor es `false`, así que la creación responde `503 REVIEW_FILTER_UNAVAILABLE` (fail-closed). El perfil `dev` lo deja en `true` para no bloquear el trabajo local sin LLM.
- Solo se envía el texto de la reseña y la calificación; no se envían datos del turista. El LLM debe correr en la infraestructura del proyecto.
- Se asume el contrato de chat de OpenAI. Si littleLLM expone otro esquema, solo cambia `LittleLlmReviewAiFilter`; la configuración y el resto del flujo se mantienen.

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
- Reseñas: totales, visibles, ocultas, pendientes del filtro de IA, reportadas y promedio de las visibles.
- Anomalías: totales, pendientes, resueltas y pendientes por severidad.
- Disponibilidad: estado agregado y detalle de PostgreSQL y MongoDB.

`GET /api/v1/admin/monitoring/availability` permite consultar solo la disponibilidad, con el estado y la
latencia en milisegundos de cada componente. Ninguna de las dos consultas modifica el contador de fallos que
alimenta la detección de `CAIDA_SERVICIO`.

`GET /api/v1/admin/audit-logs/export` genera un CSV UTF-8 (con BOM para Excel) de hasta 10 000 entradas,
respetando los mismos filtros del listado: actor, acción, usuario afectado y rango de fechas. El archivo
incluye fecha, actor, rol, acción, usuario afectado y detalles.

`GET /api/v1/admin/audit-logs/export/pdf` entrega el mismo reporte en PDF tamaño carta horizontal, con encabezado
(fecha de generación, filtros y cantidad de registros), tabla de auditoría y numeración de páginas (UC6).

Las métricas de reservas, sitios, actividades y eventos pertenecen a ms-03/ms-04 y todavía no se agregan en
este overview.

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
- `role` usa los códigos en inglés (`TOURIST`, `GUIDE`, `SERVICE_PROVIDER`, `DESTINATION_MANAGER`, `ADMIN`). Los códigos históricos en español se aceptan solo al leer datos antiguos durante la migración.
- `ver` se valida en ms-01 contra `users.auth_version`; otros servicios deben validar firma, `iss`, `aud` y `exp`.
- El TTL de acceso por defecto es **30 minutos** (`JWT_TTL=PT30M`) y no hay refresh token. La revocación por `ver` se
  aplica de inmediato solo dentro de ms-01; para acotar la ventana, cada servicio debe consultar
  `GET /api/v1/users/me/auth-version` (o replicar `ver`) al validar tokens de acciones sensibles. Es un riesgo residual
  aceptado hasta que exista una lista de revocación distribuida.
- **Los servicios que acepten tokens deben rechazar los que traigan `scope=mfa`** (son desafíos de segundo factor, no
  credenciales de acceso). ms-01 lo hace en su `jwtDecoder` y lo cubre `MfaChallengeTokenContractTest`; ms-02..ms-05
  deben replicar esa comprobación (o usar un `aud` diferente para el desafío).
- El servicio de reportes de seguridad debe confiar en la llave pública publicada en `/.well-known/jwks.json`.

## Endpoint interno de contacto (P2-9)

`GET /internal/users/{userId}/contact` permite que otro microservicio resuelva el contacto de una cuenta sin
depender de un token de usuario. Responde `200` con `{userId, email, fullName, role, phone, active}` (`phone`
puede ser `null`) y `404` con `code=USER_NOT_FOUND` si el identificador no existe.

No usa JWT porque el llamador es un servicio, no una persona. Desde la fase de hardening (MS01-11) la autorización
es una **firma HMAC por servicio**, no una clave única compartida: cada microservicio tiene su propia identidad y
una clave derivada, de modo que rotar o revocar a uno no obliga a tocar a los demás, y la clave maestra nunca viaja
en la petición. La clave maestra se toma de `INTERNAL_API_KEY` / `app.internal.api-key`; si está vacía el endpoint
responde `503` y no queda abierto.

Encabezados obligatorios:

- `X-Internal-Service`: nombre del servicio llamador (`^[a-z0-9-]{2,32}$`, por ejemplo `ms-03-reservations`). Si
  `INTERNAL_ALLOWED_SERVICES` no está vacío, debe estar en la lista.
- `X-Internal-Timestamp`: segundos epoch; se rechaza si se desvía más de `INTERNAL_MAX_CLOCK_SKEW` (`PT5M`).
- `X-Internal-Signature`: HMAC-SHA-256 en hexadecimal.

Derivación y firma:

```
claveServicio = HMAC-SHA-256(INTERNAL_API_KEY, nombreServicio)
mensaje       = nombreServicio + "\n" + método + "\n" + ruta + "\n" + timestamp
firma         = hex(HMAC-SHA-256(claveServicio, mensaje))   // comparación en tiempo constante
```

`ruta` es la ruta completa de la petición (`/internal/users/{uuid}/contact`). Enviar `X-Internal-Api-Key` ya no
autoriza nada.

Respuestas y trazas:

- firma o timestamp inválidos → `403` con `code=INVALID_INTERNAL_SIGNATURE`;
- servicio mal formado o no permitido → `403` con `code=INVALID_INTERNAL_SERVICE`;
- más de `INTERNAL_RATE_LIMIT_PER_MINUTE` peticiones del mismo servicio en un minuto → `429` con
  `code=RATE_LIMIT_EXCEEDED`;
- `200` con `{userId, email, fullName, role, phone, active}` (`phone` puede ser `null`) o `404` con
  `code=USER_NOT_FOUND`;
- clave maestra no configurada → `503` con `code=INTERNAL_API_NOT_CONFIGURED`.

Todas las llamadas, exitosas o no, quedan en `internal_access_log` (`service`, `target_user_id`, `outcome`,
`occurred_at`), con lo que una lectura de PII deja rastro y se puede auditar quién consultó qué. La firma se valida
antes de consultar la cuenta, así que un llamador no autorizado no puede inferir qué identificadores existen.
`SecurityConfig` marca `permitAll` solo para esa ruta exacta; el resto de `/internal/**` sigue exigiendo un token de
usuario y el gateway bloquea `/internal/**` hacia el exterior. Es un mecanismo **provisional**: P3-8 lo sustituye por
autenticación servicio a servicio con credenciales de corta duración.

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
monitoreo levantan PostgreSQL y MongoDB para validar el overview, la disponibilidad y las exportaciones CSV y PDF.
Las pruebas de seguridad cubren los vectores RFC 6238, el flujo MFA completo (inscripción con contraseña, desafío,
replay y recuperación), la obligatoriedad para administradores, el bloqueo por intentos, el límite por IP y por
cuenta, la resolución de la IP del cliente tras un proxy de confianza, las cabeceras y el validador de arranque.
Las pruebas del filtro de IA usan un servidor HTTP falso
que imita a littleLLM y cubren respuesta limpia, bombing, error 500, JSON inválido, timeout y la llave de API.
`PasswordResetMailpitTest` levanta un Mailpit real, recupera el correo de restablecimiento por su API y comprueba
que el enlace cambia la contraseña y que el token no se puede reutilizar. Las pruebas unitarias de regresión de la
fase BAJA cubren la neutralización de fórmulas en el CSV, la exposición de Actuator sin autenticación, la
invalidación inmediata de la caché de `authVersion` al cambiar la contraseña, el validador de arranque endurecido,
los permisos POSIX de la llave JWT, la verificación de correo (token de un solo uso, propósito y puerta de reseñas)
y la política de contraseñas. La fase informativa añade pruebas del contrato de tokens de desafío MFA (MS01-21), de
la cuota de reportes de reseñas (MS01-22) y del enfriamiento en la detección de anomalías (MS01-24). La suite son
**60 clases y 218 pruebas**; las pruebas de integración se omiten automáticamente cuando no hay Docker.

El workflow `.github/workflows/ms-01-ci.yml` ejecuta `check` en cada push o pull request y, en un job aparte,
construye la imagen, levanta el stack completo y espera a que `/actuator/health` responda `UP`.
