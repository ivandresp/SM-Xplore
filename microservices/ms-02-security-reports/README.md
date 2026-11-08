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
| `MONGODB_URI` | `mongodb://localhost:27017/security_reports` | URI de MongoDB. Fuera de `local`/`test` debe incluir credenciales (`mongodb://usuario:clave@host/db`), salvo que la plataforma use autenticación externa (MS02-13). |
| `JWK_SET_URI` | vacío | JWKS de ms-01. Obligatorio si no se define `JWT_PUBLIC_KEY_LOCATION`. |
| `JWT_PUBLIC_KEY_LOCATION` | vacío | Llave pública PEM para local/tests. |
| `JWT_ISSUER` | `sm-xplore-ms-01` | Issuer esperado en el token. |
| `JWT_AUDIENCE` | `sm-xplore-services` | Audiencia esperada. |
| `JWT_USER_ID_CLAIM` | `userId` | Claim alternativo si no viene `sub`. |
| `JWT_ROLE_CLAIM` | `role` | Claim que contiene el rol. |
| `JWT_NAME_CLAIM` | `name` | Claim que contiene el nombre del usuario. |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:3000,http://localhost:4200` | Orígenes permitidos, separados por coma. |
| `REPORT_RATE_LIMIT_ENABLED` | `true` | Activa el límite por cuenta de escrituras (MS02-03). |
| `REPORT_RATE_LIMIT_PER_MINUTE` | `30` | Reportes creados por cuenta por minuto. |
| `COMMENT_RATE_LIMIT_PER_MINUTE` | `30` | Comentarios creados por cuenta por minuto. |
| `WRITE_RATE_LIMIT_PER_MINUTE` | `60` | Resto de escrituras (fotos, contenido, cierre, resolución) por cuenta por minuto. |
| `TRUSTED_PROXIES` | `127.0.0.1/32,::1/128,172.16.0.0/12,10.0.0.0/8,192.168.0.0/16` | Proxies que pueden informar `X-Real-IP`; fuera de ellos se usa la IP de la conexión. |
| `AUTH_VERSION_ENABLED` | `true` | Valida el claim `ver` contra ms-01 (MS02-05). En local/test se apaga explícitamente. |
| `MS01_BASE_URL` | `http://ms-01:8081` | Base para consultar la versión de autenticación del usuario. |
| `MFA_REQUIRE_ADMIN` | `true` | Exige `mfa=true` a los administradores (fuera de local/test). |
| `JWT_ALLOW_INSECURE_JWKS` | `false` | Solo local/dev: acepta JWKS por HTTP no loopback. |
| `PHOTO_ALLOWED_HOSTS` | vacío | Allowlist de hosts para `photosUrls` (admite `*.dominio`). Obligatoria fuera de local/test. |
| `PHOTO_ALLOW_HTTP` / `PHOTO_ALLOW_ANY_HOST` | `false` / `false` | Relajaciones solo para local/test. |
| `MANAGEMENT_EXPOSED_ENDPOINTS` | `health,info` | Endpoints de actuator expuestos; fuera de local/test no puede incluir métricas. |
| `MONGO_AUTO_INDEX_CREATION` | `false` | Crea índices al arrancar. En Docker Compose se activa; en producción se recomienda gestionarlos con migraciones. |
| `KAFKA_BOOTSTRAP_SERVERS` | `localhost:29092` | Broker Kafka compartido. |
| `KAFKA_SECURITY_PROTOCOL` | `PLAINTEXT` | Fuera de `local`/`dev`/`test` debe ser `SSL` o `SASL_SSL` (MS02-09). |
| `KAFKA_SASL_MECHANISM` / `KAFKA_SASL_JAAS_CONFIG` | vacío | Credenciales SASL; obligatorias si el protocolo es `SASL_SSL`. |
| `KAFKA_SSL_TRUSTSTORE_LOCATION` / `KAFKA_SSL_TRUSTSTORE_PASSWORD` | vacío | Truststore para el TLS del broker. |
| `OUTBOX_ENABLED` | `true` | Activa el relay del outbox de reportes. |
| `OUTBOX_PRODUCER` | `ms-02` | Valor de `producer` en los eventos. |
| `OUTBOX_POLL_INTERVAL_MS` / `OUTBOX_BATCH_SIZE` | `1000` / `50` | Frecuencia y lote del relay. |
| `OUTBOX_MAX_ATTEMPTS` / `OUTBOX_*_BACKOFF` | `20` / `1s`–`1m` | Reintentos y backoff. |
| `OUTBOX_LEASE_DURATION` | `3m` | Lease del worker que publica. |
| `OUTBOX_RETENTION_DAYS` | `7` | Retención de eventos publicados/fallidos (antes 30; el payload lleva datos del reporte, MS02-09). |
| `HSTS_ENABLED` | `false` | Añade `Strict-Transport-Security`; en el perfil `prod` está activo (MS02-12). |
| `SPRINGDOC_API_DOCS_ENABLED` | `false` | Habilita `/v3/api-docs` (solo local/dev). |
| `SPRINGDOC_SWAGGER_UI_ENABLED` | `false` | Habilita Swagger UI (solo local/dev). |

## Contrato de reportes

- Crear: `title`, `description`, `location`, `photosUrls`.
- Actualizar: `title` y `description` mediante `PATCH /api/v1/security-reports/{reportId}/content`.
- Leer: `title`, `description`, `location`, `photosUrls`, `status`, `createdAt`, `updatedAt`.
- El usuario y su nombre se toman del JWT; no se aceptan en el body.
- Flujo de estado: `ONGOING → REPORTED` con `/close`; `REPORTED → SOLVED` con `/solve`.
- Alertas cercanas: `GET /api/v1/security-reports/nearby?latitude=&longitude=&radiusKm=&status=&size=`.
  Devuelve una proyección anonimizada (ver «Proyección de `nearby`» más abajo); `size` se limita a 50 y `radiusKm` a 20 km.
- Cada escritura publica `security.report.changed` por el outbox transaccional de MongoDB.

### Proyección de `nearby` (MS02-01)

`GET /api/v1/security-reports/nearby` no devuelve el reporte completo: expone solo `id`, `title`, `status`,
`latitude`/`longitude` redondeadas a 3 decimales (~110 m), `createdAt` y `updatedAt`. Nunca incluye `user`,
`description` ni `photosUrls`, ni siquiera para el autor del reporte o el staff, porque el endpoint es un mapa
de seguridad y no una vía de lectura del detalle. Acepta como máximo `size=50` y `radiusKm=20` (5 km por defecto).

```json
[
  {
    "id": "…",
    "title": "Robo en la playa",
    "status": "ONGOING",
    "latitude": 10.392,
    "longitude": -75.479,
    "createdAt": "2026-11-08T12:00:00Z",
    "updatedAt": "2026-11-08T12:00:00Z"
  }
]
```

## Evento `security.report.changed`

- Topic y `eventType`: `security.report.changed`.
- Productor: `ms-02`; `aggregateId` = `reportId`; key de Kafka = `aggregateId`.
- Payload: `reportId`, `reporterId`, `title`, `status`, `latitude`, `longitude`, `createdAt`, `updatedAt`.
- `latitude`/`longitude` se publican redondeadas a 3 decimales (~110 m): es la minimización posible sin romper el
  contrato v1, que exige `reporterId` y `title` (MS02-09). Ver «Riesgos aceptados».
- Fuera de `local`/`dev`/`test` el arranque exige TLS o SASL para Kafka (`KAFKA_SECURITY_PROTOCOL`, MS02-09).
- Contrato: `contracts/events/security.report.changed.v1.json`.
- Se publica al crear, cambiar contenido/fotos, cerrar y resolver un reporte.
- ms-05 no lo consume; está pensado para alertas, notificaciones y analítica.

### Sin consumidor en esta entrega (P2-10 → P3-9)

- El evento **se produce** (outbox transaccional → Kafka) pero **ningún servicio lo consume a propósito** en esta
  entrega. No es un olvido: la tabla de consumidores de `contracts/README.md` también lo marca como «no».
- **Motivo**: el ciclo del reporte ya se cubre por la API (crear, listar, cerrar, resolver, comentar). Un consumidor
  sin caso de producto cerrado agregaría acoplamiento, otro tópico que operar y riesgo de doble notificación. El
  evento se publica igual para no perder la señal y para mantener el contrato v1 validado y estable (tests de
  contrato del productor).
- **Decisión pendiente P3-9**: (a) dejar `security.report.changed` solo documentado como señal de dominio sin
  consumidor, o (b) que ms-05 lo consuma como señal (por ejemplo, penalizar la proximidad de una zona reportada) con
  su proyección y su contract test de consumidor. Hasta que P3-9 se firme, **no se agrega consumidor**.
- **Consecuencia operativa**: el outbox publica igualmente y la retención purga `PUBLISHED`/`FAILED` a los 7 días
  (`OUTBOX_RETENTION_DAYS`, MS02-09). No hay alertas push ni correo de reportes (RF80 queda diferido; ver la
  trazabilidad P2-13).

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

Reportes: orden `createdAt DESC`, `_id DESC`. Comentarios: orden `createdAt ASC`, `_id ASC`. La consulta `nearby` usa un índice `2dsphere` sobre `position` y ordena por distancia; no usa cursor, devuelve la proyección anonimizada y limita `size` a 50 y `radiusKm` a 20 km.

## Seguridad

### Defaults y perfiles (MS02-05)

Por defecto el servicio es **estricto**: valida `ver` contra ms-01, exige `mfa=true` a los administradores, no
publica métricas ni springdoc, exige HTTPS para el JWKS y una allowlist de fotos. Solo los perfiles `local`, `dev`
y `test` relajan esos controles, de forma explícita y avisando en el log; el arranque fuera de esos perfiles falla
si falta cualquiera de las condiciones. `application-local.yaml` contiene el conjunto relajado para Docker Compose.

- Solo se acepta RS256; el token lo emite ms-01. Los códigos de rol en inglés son los únicos que se emiten; los alias en español (`TURISTA`, `GUIA`, `PRESTADORA_SERVICIOS`, `GESTORA_DESTINOS`) se siguen leyendo mientras expiran los tokens anteriores a `V10`.
- El `issuer` (`iss`) debe ser `sm-xplore-ms-01` y la audiencia `sm-xplore-services`.
- Los roles usados son los códigos en inglés: `ADMIN`, `DESTINATION_MANAGER`, `GUIDE`, `SERVICE_PROVIDER`, `TOURIST`.
- `GET /security-reports` (listado global por estado) y `/counts` requieren `ADMIN` o `DESTINATION_MANAGER`; cerrar y resolver también.
- Los usuarios solo pueden modificar sus propios reportes, salvo staff.
- El detalle de un reporte (`GET /security-reports/{reportId}`) y sus comentarios solo los ve su autor o el staff (`ADMIN`/`DESTINATION_MANAGER`): sin ese control cualquier usuario autenticado leía el título, la descripción, las fotos y el denunciante de un incidente.
- `GET /security-reports/nearby` solo devuelve la proyección anonimizada (`id`, `title`, `status`, coordenadas con 3 decimales, fechas): ni el denunciante, ni la descripción, ni las fotos, ni siquiera al autor o al staff; y acota `size` ≤ 50 y `radiusKm` ≤ 20 km (MS02-01).
- Los tokens con `scope=mfa` se rechazan: son desafíos de segundo factor, no tokens de acceso.
- La llave pública se obtiene del JWKS de ms-01 o de un PEM local; nunca se comparte una llave privada.
- Los errores usan RFC 9457 (`application/problem+json`) con `type`, `title`, `status`, `detail` e `instance`.

### Comentarios (MS02-02)

Solo el autor del reporte o el staff (`ADMIN`/`DESTINATION_MANAGER`) puede comentarlo, y no se admiten comentarios
en reportes `SOLVED`. La regla se aplica en el caso de uso, después de cargar el reporte; un reporte ajeno responde
`404` igual que uno inexistente (MS02-10) y un reporte resuelto `409`.

### Límite de escrituras (MS02-03)

El cupo es **por cuenta** (claim `sub`), no por IP: detrás del gateway todas las peticiones comparten la IP del
proxy y el límite anterior era global. Hay cupos separados para crear reportes (30/min), comentar (30/min) y el
resto de escrituras (60/min); cada uno responde `429` con `Retry-After: 60` y `application/problem+json`. La IP
solo se usa como respaldo cuando no hay principal, y solo se acepta `X-Real-IP` si la conexión viene de un proxy de
la lista `TRUSTED_PROXIES`; `X-Forwarded-For` nunca se lee.

### Actuator y documentación (MS02-04)

Públicos sin JWT: solo `/actuator/health` (y `/actuator/health/**`) e `/actuator/info`. Métricas, Prometheus,
`env`, springdoc y Swagger UI no se exponen fuera de `local`/`dev`; si se habilitan en local, siguen exigiendo JWT
salvo Swagger UI (deshabilitado por defecto). El puerto de la API se publica solo en `127.0.0.1` en Compose.

### Contenido y cabeceras (MS02-06)

El contenido (título, descripción, comentarios) debe ser texto plano: el servicio rechaza etiquetas HTML
(`<script>`, `<img …>`, comentarios, etc.) y caracteres de control con `400`. La API añade
`Content-Security-Policy: default-src 'none'; frame-ancestors 'none'; base-uri 'none'` y `Cache-Control: no-store`
en las rutas `/api/`, además de `X-Content-Type-Options`, `X-Frame-Options`, `Referrer-Policy`,
`Permissions-Policy`, `Cross-Origin-Opener-Policy` y `Cross-Origin-Resource-Policy`.

### Fotos (MS02-07)

`photosUrls` solo admite URLs `https` cuyo host esté en `PHOTO_ALLOWED_HOSTS` (los subdominios se declaran con
`*.dominio`). Un host desconocido, `http://` o un esquema distinto se rechazan con `400`. El servicio no sube
archivos: guarda URLs que luego carga el navegador, por eso la allowlist evita el seguimiento de la IP del staff y
el contenido mixto.

### Correlation id (MS02-08)

`X-Correlation-Id` se acepta solo si cumple `[A-Za-z0-9._:-]{1,128}`; en cualquier otro caso se genera un UUID
nuevo en lugar de rechazar la petición. El valor viaja al outbox y a Kafka, así que no puede usarse para inflar el
documento ni inyectar caracteres de control en los logs.

### Evento y Kafka (MS02-09)

El evento conserva los campos del contrato v1, pero las coordenadas se publican redondeadas a 3 decimales y el
payload ya no aporta precisión de domicilio. `KafkaSecurityGuard` falla el arranque fuera de `local`/`dev`/`test` si
`KAFKA_SECURITY_PROTOCOL` es `PLAINTEXT` o si `SASL_SSL` no trae `KAFKA_SASL_JAAS_CONFIG`. La retención del outbox
baja a 7 días porque el payload contiene datos del reporte.

### Sin oráculo de existencia (MS02-10)

Un reporte ajeno responde igual que uno inexistente: `404` en detalle, `PATCH /content`, `/photos` y comentarios;
el staff sigue viendo cualquier reporte. Así no se puede enumerar qué IDs existen probando respuestas `403` vs
`404` con tokens autenticados.

### Coordenadas finitas (MS02-11)

`Location` y la consulta `nearby` rechazan `NaN` e `Infinity` (`Double.isFinite`) con `400`; antes esas
comparaciones dejaban pasar los valores no finitos y terminaban en un error del driver de MongoDB.

### HSTS y caché (MS02-12)

Además de CSP y `Cache-Control: no-store`, la API añade `Pragma: no-cache` en `/api/` y, con `HSTS_ENABLED=true`
(activo en `prod`), `Strict-Transport-Security: max-age=31536000; includeSubDomains`. HSTS solo debe activarse
detrás de un terminador TLS; en local queda apagado.

### MongoDB con autenticación (MS02-13)

Fuera de `local`/`test`, `MONGODB_URI` debe llevar credenciales (o la plataforma debe autenticar por otro mecanismo:
X.509, IAM). El `docker-compose.yml` de la raíz arranca mongo sin `--auth`; es deuda de plataforma (no se edita desde
este servicio) y está reportada al Lead.

### Errores sin detalle interno (MS02-14)

`IllegalArgumentException` y `ServerWebInputException` responden `400` con mensajes genéricos («Invalid request
parameter.» / «Malformed request.») y el detalle queda en el log; los errores de validación de campos públicos y de
dominio siguen siendo descriptivos para el cliente.

### Riesgos aceptados

- **XSS residual en el cliente**: el servicio devuelve JSON y rechaza HTML, pero si un cliente renderizara el
  contenido como HTML sin escapar, el navegador ejecutaría el contenido malicioso. La mitigación definitiva es del
  frontend (escape al renderizar) más la CSP; no se puede resolver solo en el backend sin corromper el texto.
- **Límite en memoria por instancia**: los cupos viven en el proceso; con varias réplicas el límite efectivo se
  multiplica por réplica. Aceptado para esta entrega; un limitador compartido (Redis) queda fuera de alcance.
- **`/actuator/health` público**: expone el estado del servicio, no datos de negocio; es el patrón de plataforma.
- **Sin subida de archivos**: no hay validación de tipo/tamaño de imagen porque el servicio solo almacena URLs; esa
  responsabilidad (y el escaneo de contenido) corresponde al almacenamiento de imágenes de la plataforma.
- **PII en el evento `security.report.changed` (MS02-09)**: el contrato v1 exige `reporterId` y `title`; se
  minimizó lo posible (coordenadas a 3 decimales, retención de outbox a 7 días) y el transporte exige TLS/SASL fuera
  de dev. Eliminar más campos requiere una versión v2 del contrato y la decisión P3-9 sobre el consumidor.
- **MongoDB sin autenticación en el compose raíz (MS02-13)**: es responsabilidad de plataforma; el servicio exige
  credenciales en la URI fuera de `local`/`test` (documentado, no impuesto en código para no romper autenticación
  externa tipo X.509/IAM).
- **Mensajes de error genéricos (MS02-14)**: se pierde detalle en la respuesta, pero el log del servidor conserva la
  causa; es el intercambio aceptado para no filtrar internals.

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

Las pruebas de integración usan Testcontainers con `mongo:7.0` en replica set y requieren Docker. La suite tiene **128 pruebas** (96 unitarias + 32 de integración): unitarias para las reglas de dominio y de seguridad (contenido, allowlist de fotos, límites por cuenta, guard de arranque, HSTS, Kafka, correlation id, coordenadas finitas) e integración para el contrato `security.report.changed`, el evento generado, la geolocalización (incluida la proyección anonimizada de `nearby`), la autorización de comentarios, el 404 sin oráculo y los errores RFC 9457.
