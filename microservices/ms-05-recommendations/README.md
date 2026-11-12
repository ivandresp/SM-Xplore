# ms-05 — Recomendaciones

Microservicio de recomendaciones para turistas de SM-Xplore. Es el único servicio que abre el
driver de Neo4j. El grafo es una **proyección** de ms-03, ms-04 y ms-01; no es fuente de verdad.

## Estado

Fases **0-1** y **2 (lado consumidor)** implementadas:

- API de recomendaciones, preferencias y vistas.
- Ranking por afinidad, calidad de reseñas, proximidad y novedad.
- Diversidad por sitio y categoría.
- Seguridad RS256/JWKS o llave pública PEM.
- Neo4j reactivo, constraints, seed y tests con Testcontainers.
- Consumidores Kafka para `catalog.*`, `activities.*` y `reviews.review.changed`.
- Idempotencia por `eventId`, descarte de eventos viejos por `updatedAt`, reintentos con
  backoff y DLQ.
- Agregados de reseñas materializados en Neo4j (`ratingAvg`, `ratingCount`, `lastReviewAt`).
- Tests de contrato contra `contracts/events/` y tests de integración con Kafka + Neo4j.
- Filtros por categoría, tipo de ítem y valoración mínima, aplicados antes de puntuar.
- Paginación (`page`/`size`) de la lista ya diversificada, con metadatos `total`, `totalPages` y
  `hasNext`.
- Reemplazo masivo de preferencias explícitas (`PUT /api/v1/preferences`).
- Warmup opcional al arranque (Neo4j, fallback de candidatos y versión de la cache en Redis).

Decisiones importantes:

- **No se usa `RESERVO`**. Ni ranking, ni popularidad, ni exclusión. Ver
  `docs/adr/0002-no-reservation-signal.md`.
- La calidad se calcula con promedio bayesiano de reseñas.
- Las vistas crean una preferencia inferida de peso bajo; una preferencia explícita siempre
  tiene prioridad.
- El idioma **no viaja en el JWT**. El usuario lo elige en el frontend y se envía en
  `Accept-Language`; si falta, se usa `es`.
- ms-03 y ms-04 todavía no publican eventos reales. En esta fase se simulan con fixtures y
  `KafkaTemplate`; el outbox queda pendiente en esos servicios. Ver
  `docs/fase-2-consumidores.md`.

## Stack

- Java 25
- Spring Boot 4.1.2
- Gradle 9.8.2 (Kotlin DSL)
- WebFlux + Reactor
- Spring Security + OAuth2 Resource Server
- Spring Kafka (consumidores y plantilla para la DLQ)
- Neo4j Java Driver 6.x (API Reactive Streams)
- Spring Data Neo4j (solo auto-configuración del driver)
- Lombok + MapStruct
- Testcontainers 2.x (Neo4j y Kafka)

## Arquitectura hexagonal

```text
src/main/java/com/smxplore/recommendations
├── domain
│   ├── model
│   │   ├── catalog
│   │   ├── event
│   │   ├── geo
│   │   ├── preference
│   │   ├── ranking
│   │   ├── recommendation
│   │   └── tourist
│   ├── exception
│   └── ports
│       ├── in
│       └── out
├── application
│   ├── interaction
│   ├── preference
│   ├── projection
│   └── recommendation
└── infrastructure
    ├── adapters/in/kafka
    ├── adapters/in/web
    ├── adapters/out/neo4j
    └── config
```

- El dominio no depende de Spring.
- Los modelos están agrupados por uso: catálogo, geografía, preferencias, ranking, recomendación y turista.
- Los puertos devuelven `Mono`/`Flux` y sus métodos de entrada se llaman `handle`.
- Los casos de uso se cablean como beans en `BeanConfiguration`.
- El adaptador Neo4j ejecuta Cypher explícito; el ranking y la diversidad son políticas de la capa `application`.
- MapStruct mapea dominio ↔ DTO.

## API

| Método | Ruta | Rol | Descripción |
|---|---|---|---|
| GET | `/api/v1/recommendations?siteId=&lat=&lng=&limit=20&page=0&size=20&categoryId=&itemType=&minRating=` | `TOURIST` | Recomendaciones del turista autenticado |
| GET | `/api/v1/recommendations?touristId=&...` | `ADMIN` | Consulta para soporte |
| GET | `/api/v1/preferences` | `TOURIST` | Lista los gustos del turista |
| POST | `/api/v1/preferences` | `TOURIST` | Gusto explícito por categoría |
| PUT | `/api/v1/preferences` | `TOURIST` | Reemplaza en bloque los gustos explícitos |
| DELETE | `/api/v1/preferences/{categoryId}` | `TOURIST` | Quitar gusto |
| POST | `/api/v1/interactions/view/{itemType}/{itemId}` | `TOURIST` | Registrar vista |
| POST | `/internal/admin/reindex?entity=&updatedSince=` | `ADMIN` | Reindexado del catálogo desde ms-04 |
| GET | `/actuator/health` | público | Liveness/readiness |
| GET | `/swagger-ui.html` | público | Documentación OpenAPI |

El `touristId` sale de `sub` o, si no viene, del claim configurado `userId`. Un `TOURIST` no
puede consultar a otro turista; solo `ADMIN` puede usar el query param `touristId`.

El idioma preferido del turista se envía en `Accept-Language` (`es`, `en-US`, etc.). Se usa al
crear o refrescar su perfil en Neo4j y no se lee de ningún claim del JWT.

#### Filtros y paginación

`GET /api/v1/recommendations` acepta, además de `siteId`, `lat`, `lng` y `limit`:

- `categoryId` (UUID): solo candidatos de esa categoría.
- `itemType` (`SITE`, `ACTIVITY`, `EVENT` o `SERVICE`): solo candidatos de ese tipo; un valor
  desconocido responde `400`.
- `minRating` (`0`..`5`): descarta candidatos con `ratingAvg` inferior; fuera de rango responde
  `400`.
- `page` (`>= 0`, por defecto `0`) y `size` (`1`..`maxLimit`, por defecto `limit`): paginan la
  lista **después** de puntuar y diversificar.

Los filtros se aplican al conjunto de candidatos **antes** de puntuar y antes de decidir si se
lanza el fallback: un filtro que deja la lista de afinidad vacía sí provoca fallback, pero el
fallback también queda filtrado. `limit` sigue acotando cuántos elementos se generan; la
paginación reparte esa lista ya diversificada. En la respuesta, `total` es el tamaño de esa lista
(`<= limit`), `totalPages = ceil(total / size)` y `hasNext = (page + 1) * size < total`. Una
página posterior a la última devuelve `items` vacío y `hasNext=false` (no es un error). La clave
de cache incluye página, tamaño y filtros, y normaliza coordenadas a 4 decimales y `minRating` a 2.

#### Reemplazo masivo de preferencias

`PUT /api/v1/preferences` con `{"categoryIds": ["<uuid>", ...]}` sustituye en una sola operación
los gustos **explícitos** del turista autenticado (máximo 20, sin duplicados ni nulos; un
`categoryIds` ausente o fuera de contrato responde `400`). Las preferencias **inferidas** por
vistas se conservan. Una lista vacía elimina todos los gustos explícitos. Si algún `categoryId`
no existe, responde `404` y la transacción no modifica nada (rollback completo). La respuesta
`200` devuelve el conjunto de preferencias resultante, incluidas las inferidas.

### Eventos Kafka

Topics consumidos:

| Topic | Productor esperado | Efecto en ms-05 |
|---|---|---|
| `catalog.site.changed` | ms-04 | Upsert de `Sitio` y enlaces con categoría/ítems hijos |
| `catalog.service.changed` | ms-04 | Upsert de `Servicio` |
| `catalog.category.changed` | ms-04 | Upsert de `Categoria` y jerarquía |
| `catalog.zone.changed` | ms-04 | Upsert de `Zona` y enlace `Sitio -[:PERTENECE_A]-> Zona` |
| `activities.event.changed` | ms-03 | Upsert de `Evento` |
| `activities.activity.changed` | ms-03 | Upsert de `Actividad` |
| `reviews.review.changed` | ms-03 y ms-04 | Arista `RESEÑO` y agregados de calidad |

`bookings.reservation.changed` **no se consume**. Grupo de consumo:
`ms-05-recommendations`. La key debe ser el `aggregateId`.

El productor es parte del contrato (MS05-01): `ProjectionEventDecoder` rechaza el mensaje si
el `producer` no es el esperado para el topic (`ms-04` para `catalog.*`;
`ms-03-activities-and-events` para `activities.*` y para las reseñas de actividades/eventos;
`ms-04` también para las reseñas de sitios y servicios), y también si `occurredAt` o el
`updatedAt` del evento caen más
de `EVENTS_CONSUMER_MAX_FUTURE_SKEW` (5 minutos por defecto) en el futuro. Un evento con un
`updatedAt` futuro congelaría la entidad para el resto de eventos legítimos, porque la
comparación de frescura descarta todo lo anterior o igual.

Cada mensaje se procesa en una transacción de Neo4j que, en la misma operación:

1. comprueba `ProcessedEvent.eventId` para deduplicar;
2. compara `updatedAt` contra el nodo o la arista existente y descarta eventos viejos;
3. aplica el cambio;
4. registra el `eventId` con el resultado (`APPLIED`, `DUPLICATE` o `STALE`).

Los fallos de contrato van directo a `<topic>.dlq`. Los fallos transitorios se reintentan con
backoff exponencial y después van a la DLQ. El offset se confirma solo después de aplicar el
cambio o de publicar en la DLQ.

### Reindexado del catálogo

El grafo es una proyección: si se borra o se desincroniza, se reconstruye sin esperar a que
ms-04 vuelva a emitir cada evento. `POST /internal/admin/reindex?entity=SITES|SERVICES|CATEGORIES|ACTIVITIES|EVENTS`
recorre el feed `GET /api/v1/admin/catalog/export` de ms-04 (catálogo) o de ms-03 (actividades y
eventos) y aplica **el mismo camino de proyección** que los consumidores de Kafka:

- reenvía el token del administrador que invoca el endpoint (ms-05 no tiene credenciales propias);
- cada ítem del feed se convierte en el evento equivalente (`catalog.site.changed`, etc.) y pasa
  por `ProjectionEventDecoder`, así que las validaciones de contrato son las mismas;
- la versión del sobre es la que envía el productor en el ítem (`eventVersion`). Si un feed
  antiguo no la manda, ms-05 la infiere del payload (un sitio con `managerId` es v2) en lugar de
  asumir v1;
- el `eventId` se deriva de `eventType + aggregateId + updatedAt`, de modo que repetir una página
  es idempotente y una entidad que cambió después genera un marcador nuevo;
- `updatedSince` permite reanudar un recorrido interrumpido y `REINDEX_MAX_PAGES` evita bucles
  infinitos si el feed no termina;
- la respuesta resume `pages`, `events`, `applied`, `duplicate` y `stale`.

Reindexado con límites (MS05-03):

- el bearer ADMIN solo se envía a hosts de `REINDEX_ALLOWED_HOSTS`
  (por defecto `localhost`, `127.0.0.1`, `::1`, `ms-03`, `ms-04`, `host.docker.internal`);
  se valida en cada petición, no solo al arrancar;
- una petición no puede proyectar más de `REINDEX_MAX_EVENTS_PER_REQUEST` eventos (2000 por
  defecto); si el feed pide más, se responde 400 con la indicación de acotar `updatedSince`;
- dos reindexados del mismo administrador deben estar separados por `REINDEX_MIN_INTERVAL`
  (60 segundos por defecto); el segundo responde 400 con los segundos que faltan;
- cada corrida deja una línea de auditoría con `actor`, `entity`, `pages`, `events`, `applied`,
  `duplicate`, `stale` y duración, más otra al iniciar.

Docker-local: ms-04 se publica en `http://localhost:8084` (`CATALOG_EXPORT_BASE_URL`) y ms-03 en
`http://localhost:8082` (`MS03_BASE_URL`), que son los valores por defecto.

### Ejemplo de respuesta

```json
{
  "touristId": "66666666-6666-6666-6666-666666666601",
  "generatedAt": "2026-10-26T12:00:00Z",
  "source": "MIXED",
  "page": 0,
  "size": 20,
  "total": 7,
  "totalPages": 1,
  "hasNext": false,
  "items": [
    {
      "itemType": "EVENT",
      "itemId": "44444444-4444-4444-4444-444444444401",
      "name": "Festival del Mar",
      "categoryId": "11111111-1111-1111-1111-111111111106",
      "siteId": "22222222-2222-2222-2222-222222222201",
      "score": 0.8123,
      "reason": "Coincide con tu interés en Música · Bien valorado (4.7/5, 120 reseñas) · A 2.3 km",
      "signals": {
        "affinity": 0.91,
        "quality": 0.87,
        "distanceKm": 2.3,
        "novelty": 0.62
      }
    }
  ]
}
```

`source` puede ser `AFFINITY`, `QUALITY`, `NEARBY`, `FALLBACK` o `MIXED`.

## Seguridad

En `prod`, este servicio valida el claim `ver` contra ms-01 (`MS01_BASE_URL`) y exige
`mfa=true` a los administradores. La validación es fail-closed: si ms-01 no responde, las
peticiones se rechazan. En local/tests queda desactivada con `AUTH_VERSION_ENABLED=false`.

- Solo RS256. No se acepta HS256.
- Producción / ms-01: `JWK_SET_URI=https://ms-01/.../.well-known/jwks.json`.
- Local / tests: `JWT_PUBLIC_KEY_LOCATION=file:/ruta/public.pem` o `classpath:...`.
- `JWT_ISSUER=sm-xplore-ms-01` y `JWT_AUDIENCE=sm-xplore-services`.
- El rol se lee del claim configurado (`role` por defecto) y se prefija con `ROLE_`.
- Los roles esperados son `TOURIST` y `ADMIN` (códigos en inglés de ms-01).
- Los tokens con `scope=mfa` se rechazan: son desafíos de segundo factor, no tokens de acceso.
- El UUID del turista se lee de `sub`; si no existe, se usa el claim configurado
  `JWT_USER_ID_CLAIM` (`userId` por defecto). Los claims esperados son `userId`/`sub`, `role`
  y `name`.
- El idioma **no** es un claim del token; se toma de `Accept-Language`.
- Errores en RFC 9457 (`ProblemDetail`), incluidos 401/403.

### Endurecimiento de la fase 2 de seguridad

Estas tres correcciones salen de la auditoría estática (`MS05-01`, `MS05-02` y `MS05-03`) y
están activas por defecto, también en local.

**MS05-01 — proyección solo desde los productores esperados.** `ProjectionEventDecoder` valida
`producer` contra el topic (`catalog.*` → `ms-04`; `activities.*` → `ms-03-activities-and-events`;
`reviews.review.changed` → `ms-03-activities-and-events` o `ms-04`, porque el contrato tiene dos
productores legítimos) y
rechaza `occurredAt`/`updatedAt` que estén más de `EVENTS_CONSUMER_MAX_FUTURE_SKEW` en el futuro
(`5m` por defecto, máximo `1h`). Un mensaje inválido no se proyecta: sigue el camino normal de
DLQ (`<topic>.dlq`). Esto no sustituye la autenticación del broker; la defensa de red sigue
siendo necesaria.

**MS05-02 — cuota por turista en recomendaciones.** Cada `TOURIST` (el `sub` del token) puede
pedir como máximo `RECOMMENDATIONS_QUOTA_MAX_REQUESTS` recomendaciones por
`RECOMMENDATIONS_QUOTA_WINDOW` (`120` por `1m` por defecto). Al superarla se responde
`429 Too Many Requests` con `ProblemDetail` (`application/problem+json`) y cabecera
`Retry-After` en segundos. La cuota es por instancia y en memoria (Caffeine acotado por
`RECOMMENDATIONS_QUOTA_MAX_ENTRIES`); el rate limiting por IP del gateway es la primera capa y
esto limita el abuso de un solo token. `ADMIN` también consume su propio presupuesto al consultar
en nombre de un turista. Se desactiva con `RECOMMENDATIONS_QUOTA_ENABLED=false` (los tests del
perfil `test` ya lo hacen).

**MS05-03 — reindexado acotado y auditable.** Ver la sección de reindexado: allowlist de hosts
para el bearer, tope de eventos por petición, intervalo mínimo por administrador y auditoría por
corrida. En un entorno real conviene además que ms-04/ms-03 emitan un token de servicio para este
flujo y no reutilicen el token humano.

### Endurecimiento de la fase 3 de seguridad (hallazgos BAJA)

Estas correcciones salen de la auditoría estática (`MS05-04` a `MS05-10` y `MS05-13`) y también
están activas por defecto; las relajaciones viven solo en los perfiles `local` y `test`.

**MS05-04 — presupuesto en las escrituras del grafo.** Las vistas y las preferencias dejan de ser
ilimitadas. Cada `TOURIST` tiene una ventana fija para `POST /api/v1/interactions/view/**`
(`RECOMMENDATIONS_VIEW_QUOTA_MAX_REQUESTS`, `60`/`1m` por defecto) y otra para
`POST`/`PUT`/`DELETE /api/v1/preferences` (`RECOMMENDATIONS_PREFERENCE_QUOTA_MAX_REQUESTS`, `30`/`1m`).
Además, una vista repetida del mismo ítem dentro de `RECOMMENDATIONS_VIEW_DEDUPE_WINDOW` (`1m`) se
responde `204` sin tocar Neo4j: no incrementa `VIO.count` ni reescribe la afinidad inferida. Al
superar un presupuesto se responde `429` con `Retry-After`. Se desactiva por bloque con
`RECOMMENDATIONS_VIEW_QUOTA_ENABLED=false` / `RECOMMENDATIONS_PREFERENCE_QUOTA_ENABLED=false`, o
todo con el interruptor maestro `RECOMMENDATIONS_QUOTA_ENABLED=false`. Como la cuota de
recomendaciones, es **por instancia y en memoria**.

**MS05-05 — Redis y la clave de versión del grafo.** Las claves siguen el prefijo del servicio
(`RECOMMENDATIONS_CACHE_KEY_PREFIX`, `ms05:recommendations:`) y la versión del grafo vive en
`ms05:recommendations:graph:version`, namespaceada para que otro workload no pueda invalidar la
cache escribiendo una clave genérica. Solo el job de proyección incrementa esa versión. Un valor
leído de Redis se valida antes de usarlo: tamaño máximo, JSON legible, `touristId` coherente con la
clave, ítems acotados y `score` en `[0,1]`; si no cuadra se descarta como un *miss*. El perfil
`prod` exige `REDIS_PASSWORD` (falla al arrancar si falta). **Pendiente de plataforma:** el Redis
del `compose.yaml` raíz todavía no usa `requirepass`/ACL; debe hacerse allí (el compose raíz es de
plataforma, este servicio no lo modifica) y no exponerlo fuera de la red interna.

**MS05-06 — revocación de tokens activa por defecto.** `AUTH_VERSION_ENABLED` pasa a `true` por
defecto; solo `local` y `test` lo desactivan explícitamente porque no tienen ms-01 disponible. En
`prod` la validación es fail-closed y la ventana efectiva de revocación es
`AUTH_VERSION_CACHE_TTL` (`60s`) más la vida del token emitido por ms-01.

**MS05-07 — actuator y documentación cerrados por defecto.** Se exponen solo `health` e `info`
(`MANAGEMENT_ENDPOINTS`); `metrics` y `prometheus` requieren rol `ADMIN`; Swagger/OpenAPI y los
`/webjars` quedan detrás de autenticación salvo `SECURITY_DOCS_PUBLIC=true`. Los perfiles `local`
y `test` abren métricas y documentación para el trabajo diario; `prod` mantiene todo cerrado.

**MS05-08 — Neo4j sin DDL implícito.** `NEO4J_INITIALIZE_SCHEMA` pasa a `false` por defecto
(coincide con `@ConditionalOnProperty(matchIfMissing = false)`); el esquema solo se crea cuando se
pide explícitamente. **Pendiente de plataforma:** el compose de `deploy/prod` sigue usando el
usuario `neo4j` (superusuario); lo correcto es un usuario dedicado con permisos mínimos sobre las
etiquetas/propiedades de ms-05 y ejecutar `cypher/schema.cypher` como job de migración, no en cada
arranque. Este repositorio no toca el compose raíz ni el de `deploy/prod`.

**MS05-09 — límites de longitud.** `ProjectionEventDecoder` acota los textos que llegan por Kafka
(`name` 200, `description` 2000, `type` 50, `category` 200, `status` 50, `language` 35,
`correlationId`/`producer` 100, `eventType` 60; un exceso va a DLQ) y `RequestLanguageResolver`
descarta etiquetas `Accept-Language` de más de 35 caracteres antes de persistir
`TuristaProfile.language`. El feed de reindexado se valida con el mismo decodificador.

**MS05-10 — compose del servicio cerrado.** El `docker-compose.yml` de este servicio publica la API
solo en `127.0.0.1`, exige `NEO4J_PASSWORD` (`${NEO4J_PASSWORD:?set NEO4J_PASSWORD}`) y deja
`JWT_ALLOW_INSECURE_JWKS`, `SEED_ENABLED`, `NEO4J_INITIALIZE_SCHEMA` y `SPRINGDOC_*` en `false`
salvo opt-in explícito. Para desarrollo local hay que exportar `NEO4J_PASSWORD` y, si se usan
llaves PEM locales, `JWT_ALLOW_INSECURE_JWKS=true` de forma consciente.

**MS05-13 — auditoría del acceso de ADMIN a otro turista.** Cuando un `ADMIN` consulta
recomendaciones de un `touristId` distinto del suyo, se registra
`audit: admin <sub> read recommendations for tourist <touristId>` y se incrementa el contador
`recommendations.admin.access`. La capacidad de soporte se mantiene, pero deja de ser invisible:
se puede alertar sobre ella sin romper el flujo.

### Endurecimiento de la fase informativa de seguridad (MS05-11, MS05-12, MS05-14 y MS05-15)

**MS05-11 — CSP y HSTS en las respuestas del servicio.** `SecurityHeadersWebFilter` añade siempre
`Content-Security-Policy: default-src 'none'; frame-ancestors 'none'; base-uri 'none'; form-action 'none'`
(el servicio solo devuelve JSON) y `Strict-Transport-Security: max-age=31536000; includeSubDomains`
cuando la petición llegó por TLS (`request.getSslInfo()`) o el gateway la reescribió con
`X-Forwarded-Proto: https`. **Pendiente de plataforma:** el gateway termina TLS y es quien debe
enviar HSTS al cliente; ms-05 no puede cubrir el primer acceso ni las respuestas que no pasan por
él. Esta es una decisión de plataforma, no de este servicio.

**MS05-12 — autorización cerrada por defecto.** La cadena de seguridad ya no usa
`anyExchange().authenticated()` como red de seguridad: la última regla es
`anyExchange().denyAll()`. Cada ruta debe declararse de forma explícita con su rol
(`/actuator/health|info` público; `metrics`/`prometheus` y `/internal/admin/**` con `ADMIN`;
`/api/v1/recommendations` con `TOURIST|ADMIN`; preferencias e interacciones con `TOURIST`;
documentación solo con `SECURITY_DOCS_PUBLIC=true`). Consecuencia operativa: **toda ruta nueva
responde 403 hasta que se añada su regla**, y su test de autorización por rol. `SecurityAuthorizationTest`
fija este comportamiento, incluida una ruta de prueba no declarada que debe dar 403.

**MS05-14 — una sola regla de identidad.** `JwtIdentityResolver` centraliza la lectura del UUID:
`sub` y, si falta, el claim configurable `JWT_USER_ID_CLAIM` (`userId` por defecto). Lo usan tanto
`JwtUserResolver` (resolución de la petición) como `AuthVersionValidator` (validación del claim
`ver`), que antes caía a un claim fijo `id` y podía devolver 401 con emisores que solo envían
`userId`. Requiere UUID válido; un valor ausente o no-UUID se rechaza con `InvalidJwtException`.

**MS05-15 — imagen endurecida.** El `Dockerfile` ya parte de `eclipse-temurin:25-jdk` y
`eclipse-temurin:25-jre` pineados por digest, no instala paquetes del sistema durante el build,
purgea `apt` (y sus librerías) en la etapa de runtime y corre con el usuario no root `app`. El
healthcheck usa la propia JVM (`java` + `HealthCheck`), así que la imagen no necesita `curl`. `DockerImageHardeningTest` fija esos
invariantes. **Pendiente de plataforma/CI:** no hay escaneo de dependencias (Trivy/Grype) ni SBOM en
la tubería; se documenta como riesgo aceptado hasta que CI lo añada. Las dependencias con CVE ya
están forzadas en `build.gradle.kts` (`lz4-java`, `snappy-java`) y los starters van por BOM de
Spring Boot.

**JWKS** (JSON Web Key Set) es el estándar para publicar las llaves públicas con las que se
verifican los JWT. ms-01 firma con su llave privada y expone las públicas en un endpoint como
`/.well-known/jwks.json`; ms-05 descarga ese conjunto, elige la llave por el `kid` del header
del JWT y cachea el resultado. Así ms-01 puede rotar llaves sin reiniciar a los consumidores.
La llave pública PEM es solo el atajo local/test.

Si no hay ni JWKS ni llave pública, el servicio falla al arrancar. Es intencional: no queremos
un arranque silencioso sin validación de tokens.

## Configuración

| Variable | Default | Descripción |
|---|---|---|
| `SERVER_PORT` | `8080` | Puerto interno |
| `NEO4J_URI` | `bolt://localhost:7687` | URI de Neo4j |
| `NEO4J_USER` | `neo4j` | Usuario |
| `NEO4J_PASSWORD` | (requerido en compose) | Contraseña; el `docker-compose.yml` falla si no se define |
| `REDIS_HOST` / `REDIS_PORT` | `localhost` / `6379` | Redis compartido: cache de recomendaciones y versión del grafo |
| `REDIS_PASSWORD` | vacío | Contraseña de Redis; obligatoria en `prod` (MS05-05) |
| `RECOMMENDATIONS_CACHE_ENABLED` | `true` | Activa la cache en memoria + Redis |
| `RECOMMENDATIONS_CACHE_KEY_PREFIX` | `ms05:recommendations:` | Prefijo de claves de la cache (MS05-05) |
| `RECOMMENDATIONS_CACHE_VERSION_KEY` | `ms05:recommendations:graph:version` | Clave de la versión del grafo; solo la escribe la proyección |
| `RECOMMENDATIONS_CACHE_LOCAL_TTL` | `10s` | Vida de la capa en memoria (Caffeine) |
| `RECOMMENDATIONS_CACHE_REDIS_TTL` | `1m` | Vida de la capa compartida (Redis) |
| `RECOMMENDATIONS_CACHE_LOCAL_MAX_SIZE` | `1000` | Entradas máximas de la capa en memoria |
| `RECOMMENDATIONS_QUOTA_ENABLED` | `true` | Interruptor maestro de cuotas por turista (MS05-02/MS05-04) |
| `RECOMMENDATIONS_QUOTA_MAX_REQUESTS` | `120` | Recomendaciones por ventana |
| `RECOMMENDATIONS_QUOTA_WINDOW` | `1m` | Ventana fija de la cuota de recomendaciones |
| `RECOMMENDATIONS_QUOTA_MAX_ENTRIES` | `10000` | Turistas con ventana activa en memoria |
| `RECOMMENDATIONS_VIEW_QUOTA_ENABLED` | `true` | Cuota de vistas registradas (MS05-04) |
| `RECOMMENDATIONS_VIEW_QUOTA_MAX_REQUESTS` | `60` | Vistas por ventana |
| `RECOMMENDATIONS_VIEW_QUOTA_WINDOW` | `1m` | Ventana fija de la cuota de vistas |
| `RECOMMENDATIONS_VIEW_DEDUPE_WINDOW` | `1m` | Ventana de deduplicación de vistas del mismo ítem |
| `RECOMMENDATIONS_PREFERENCE_QUOTA_ENABLED` | `true` | Cuota de escrituras de preferencias (MS05-04) |
| `RECOMMENDATIONS_PREFERENCE_QUOTA_MAX_REQUESTS` | `30` | Altas/bajas de preferencia por ventana |
| `RECOMMENDATIONS_PREFERENCE_QUOTA_WINDOW` | `1m` | Ventana fija de la cuota de preferencias |
| `NEO4J_INITIALIZE_SCHEMA` | `false` | Crea constraints/índices al arrancar; solo con opt-in explícito (MS05-08) |
| `SEED_ENABLED` | `false` | Carga datos de demo; usar solo en local/test |
| `JWK_SET_URI` | vacío | JWKS de ms-01 |
| `JWT_PUBLIC_KEY_LOCATION` | vacío | Llave pública PEM para local/test |
| `JWT_ISSUER` | `sm-xplore-ms-01` | Issuer esperado |
| `JWT_AUDIENCE` | `sm-xplore-services` | Audiencia esperada |
| `JWT_USER_ID_CLAIM` | `userId` | Claim de identidad si no viene `sub` |
| `JWT_ROLE_CLAIM` | `role` | Claim de rol |
| `JWT_NAME_CLAIM` | `name` | Claim de nombre |
| `JWT_ALLOW_INSECURE_JWKS` | `false` | Permite JWKS por HTTP; solo desarrollo (MS05-10) |
| `AUTH_VERSION_ENABLED` | `true` | Revoca tokens contra la versión de ms-01; `local`/`test` lo desactivan (MS05-06) |
| `MS01_BASE_URL` | vacío | Base URL de ms-01 para validar la versión del token |
| `AUTH_VERSION_CACHE_TTL` | `60s` | Ventana de cache de la versión (ventana efectiva de revocación) |
| `SECURITY_DOCS_PUBLIC` | `false` | Publica Swagger/OpenAPI sin JWT; solo `local`/`test` (MS05-07) |
| `MANAGEMENT_ENDPOINTS` | `health,info` | Endpoints de actuator expuestos; `metrics`/`prometheus` exigen ADMIN (MS05-07) |
| `KAFKA_BOOTSTRAP_SERVERS` | `localhost:29092` | Broker Kafka |
| `KAFKA_CONSUMER_GROUP_ID` | `ms-05-recommendations` | Grupo de consumo |
| `KAFKA_AUTO_OFFSET_RESET` | `earliest` | Política si no hay offset |
| `EVENTS_CONSUMER_ENABLED` | `false` | Activa los consumidores |
| `EVENTS_CONSUMER_MAX_RETRIES` | `3` | Reintentos antes de la DLQ |
| `EVENTS_CONSUMER_INITIAL_BACKOFF` | `500ms` | Backoff inicial |
| `EVENTS_CONSUMER_MAX_BACKOFF` | `5s` | Backoff máximo |
| `EVENTS_CONSUMER_PROCESSING_TIMEOUT` | `15s` | Timeout de una proyección |
| `EVENTS_CONSUMER_BACKOFF_MULTIPLIER` | `2.0` | Multiplicador del backoff |
| `EVENTS_CONSUMER_CONCURRENCY` | `1` | Hilos por listener |
| `EVENTS_CONSUMER_DLQ_SUFFIX` | `.dlq` | Sufijo del topic de descarte |
| `EVENTS_CONSUMER_MAX_FUTURE_SKEW` | `5m` | Tolerancia de reloj para `occurredAt`/`updatedAt` (MS05-01, máximo `1h`) |
| `EVENTS_RETENTION_ENABLED` | `true` | Purga periódica de marcadores `ProcessedEvent` |
| `EVENTS_RETENTION_DAYS` | `30` | Antigüedad mínima para borrar un marcador |
| `EVENTS_RETENTION_BATCH_SIZE` | `1000` | Marcadores por lote |
| `EVENTS_RETENTION_MAX_BATCHES` | `20` | Lotes máximos por corrida |
| `EVENTS_RETENTION_INTERVAL` | `1h` | Espera entre corridas |
| `CATALOG_EXPORT_BASE_URL` | `http://localhost:8084` | Base URL de ms-04 para el reindexado |
| `MS03_BASE_URL` | `http://localhost:8082` | Base URL de ms-03 para reindexar actividades y eventos |
| `REINDEX_PAGE_SIZE` | `200` | Ítems pedidos por página (máximo 200) |
| `REINDEX_MAX_PAGES` | `500` | Tope de páginas por reindexado |
| `REINDEX_TIMEOUT` | `30s` | Timeout de cada llamada al feed |
| `REINDEX_ALLOWED_HOSTS` | `localhost,127.0.0.1,::1,ms-03,ms-04,host.docker.internal` | Hosts a los que se puede enviar el bearer ADMIN (MS05-03) |
| `REINDEX_MAX_EVENTS_PER_REQUEST` | `2000` | Tope de eventos proyectados por petición |
| `REINDEX_MIN_INTERVAL` | `60s` | Espera mínima entre dos reindexados del mismo administrador |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:3000,http://localhost:4200` | Orígenes |
| `RECOMMENDATIONS_WARMUP_ENABLED` | `true` | Warmup de Neo4j/Redis al arrancar; un fallo solo deja un `warn` |
| `RECOMMENDATIONS_WARMUP_TIMEOUT` | `30s` | Timeout del warmup de arranque |
| `RECOMMENDATIONS_*` | ver `application.yaml` | Pesos, límites y diversidad |

El warmup de arranque cuenta los ítems `PUBLISHED`, ejecuta la consulta de fallback con un
turista sintético y lee en Redis la clave de versión del grafo. No bloquea el arranque si falla:
solo registra un `warn`. Está desactivado en el perfil `test` para no retrasar los contenedores.

## Ejecución con Docker

La red `smxplore-net` debe existir. Levántala una vez desde la raíz:

```bash
cd infrastructure
cp .env.example .env
docker compose up -d

cd ../microservices/ms-05-recommendations
cp .env.example .env
# NEO4J_PASSWORD es obligatorio: el compose falla si no se define.
# Configura también JWK_SET_URI o JWT_PUBLIC_KEY_LOCATION, JWT_ISSUER.
docker compose up --build
```

Servicios (publicados solo en `127.0.0.1`):

- API: `http://localhost:8085`
- Swagger: `http://localhost:8085/swagger-ui.html` (con `SECURITY_DOCS_PUBLIC=true` o JWT)
- Health: `http://localhost:8085/actuator/health`
- Neo4j HTTP: `http://localhost:7474`
- Neo4j Bolt: `localhost:7687`

El compose del servicio no activa el seed, ni el DDL de esquema, ni JWKS inseguro por defecto:
esos valores se opt-in explícitamente (ver `docker-compose.yml`).

Si usas una llave pública PEM dentro de Docker, monta el archivo en el contenedor y apunta
`JWT_PUBLIC_KEY_LOCATION` a esa ruta (por ejemplo `file:/run/secrets/jwt-public.pem`). En
producción se recomienda JWKS de ms-01 y no montar llaves.

Para borrar datos:

```bash
docker compose down -v
```

## Ejecución local

```bash
export JAVA_HOME=/usr/lib/jvm/java-25-openjdk-amd64
export NEO4J_URI=bolt://localhost:7687
export NEO4J_USER=neo4j
export NEO4J_PASSWORD=change-me
export JWT_PUBLIC_KEY_LOCATION=file:/ruta/ms-01-public.pem
export JWT_ISSUER=sm-xplore-ms-01
export JWT_AUDIENCE=sm-xplore-services
export SEED_ENABLED=true
export SPRING_PROFILES_ACTIVE=local
./gradlew bootRun
```

El perfil `local` activa el seed y logs DEBUG. No activa seguridad por sí solo.

## Seed de demo

Con `SEED_ENABLED=true` se cargan:

- 9 categorías con jerarquía (`Naturaleza → Playas/Ecoturismo/Aventura`, `Cultura → Música`).
- 3 sitios, 4 actividades, 5 eventos (uno vencido) y 4 servicios.
- 3 turistas: Ana (Música/Playas/Gastronomía), Luis (Aventura/Ecoturismo) y un turista nuevo.
- Preferencias, vistas y reseñas.
- **Ninguna arista `RESERVO`.**

El seed es idempotente (`MERGE`).

## Pruebas

```bash
./gradlew test
```

Las pruebas de integración usan Testcontainers con `neo4j:5.26-community`, Kafka
(`apache/kafka:4.1.0`) y Docker. Son **41 clases y 263 pruebas**, todas en verde. La suite cubre:

- scoring bayesiano, proximidad, novedad y normalización;
- topes de diversidad por sitio/categoría;
- filtros de candidatos (`categoryId`, `itemType`, `minRating`) y paginación posterior a la
  diversificación (`page`, `size`, `total`, `totalPages`, `hasNext`, páginas fuera de rango);
- reemplazo masivo de preferencias explícitas (validación de categorías en una transacción,
  rollback completo ante ids desconocidos y conservación de las preferencias inferidas);
- deduplicación afinidad/fallback;
- preferencias y vistas;
- seguridad RS256, roles, consulta admin y errores;
- consultas Neo4j y ausencia total de aristas `RESERVO`;
- contexto completo con seed;
- validación de todos los ejemplos contra los JSON Schema de `contracts/`;
- `ArchitectureTest` (reglas del hexágono, con los puertos reactivos como única excepción) y pruebas
  de contrato del consumidor que mutan el ejemplo real de cada tópico y exigen `InvalidEventException`
  cuando falta un campo obligatorio;
- cache en memoria + Redis, invalidación por versión del grafo y degradación sin Redis;
- consumidores Kafka, idempotencia, eventos viejos, DLQ y agregados de reseñas;
- purga de marcadores `ProcessedEvent` por lotes y con corte por antigüedad;
- reindexado del catálogo: paginación, `updatedSince`, corte por `max-pages`, traducción de
  errores de ms-04 y endpoint administrativo (incluido el 403 para roles que no son `ADMIN`);
- idioma desde `Accept-Language` y JWT sin claim `locale`;
- métricas de recomendaciones servidas, cache y diversidad;
- regresiones de la fase 2 de seguridad: `producer`/`occurredAt`/`updatedAt` en el decoder,
  cuota por turista con `429` + `Retry-After`, y allowlist de hosts, tope de eventos e intervalo
  mínimo del reindexado;

## Métricas

`GET /actuator/prometheus` publica, además de las métricas de JVM y HTTP:

| Métrica | Tipo | Etiquetas | Qué mide |
| --- | --- | --- | --- |
| `recommendations.served` | counter | `source` (`MIXED`, `AFFINITY`, `NEARBY`, `FALLBACK`, ...) | respuestas servidas por origen |
| `recommendations.items` | summary | - | elementos devueltos por respuesta |
| `recommendations.cache` | counter | `result` (`hit`, `miss`) | consultas resueltas por la cache y consultas calculadas |
| `recommendations.diversity.categories` | summary | - | categorías distintas por respuesta |
| `recommendations.diversity.sites` | summary | - | sitios distintos por respuesta |

La proporción `recommendations.cache` con `result=hit` frente a `result=miss` es el indicador de
salud de la cache: si cae a cero con Redis arriba, revisar `ms05:graph:version` y el TTL local.

## Documentación

- `docs/ESTADO-Y-PENDIENTES.md`: estado actual, decisiones y pendientes.
- `docs/fase-2-consumidores.md`: alcance de la fase 2, contratos asumidos y pendientes de ms-03/ms-04.
- `docs/neo4j-logical-model.md`: modelo lógico actualizado, constraints, queries y fairness.
- `docs/adr/0001-event-driven-broker.md`: Kafka + outbox para fase 2.
- `docs/adr/0002-no-reservation-signal.md`: decisión de excluir `RESERVO`.
- `docs/runbook.md`: operación y diagnóstico.
- `loadtest/recommendations-flow.js`: prueba de carga con k6 (`BASE_URL`, `TOKEN`, `LAT`/`LNG`, `LIMIT`).
- `../../contracts/README.md`: contratos de eventos.
- `../../infrastructure/README.md`: Kafka/Redis compartidos.

## Decisiones de datos (P3-3)

- ms-05 no consulta las coordenadas del catálogo de ms-04 en vivo: el punto (`latitude`/`longitude`)
  se proyecta como **snapshot** del evento `catalog.site.changed` v2. Si un sitio mueve su punto, la
  recomendación por proximidad usa el punto del último evento proyectado.
- La proximidad solo se calcula para ítems con punto en el payload; los que no lo tienen caen al
  ranking por afinidad/calidad. Decisión firmada (P3-3, opción A) para no acoplar ms-05 a la base
  de ms-04.

## Fuera de alcance en esta fase

- Circuit breaker explícito y alertas de DLQ/lag (fases 3-4); la cache ya degrada sin Redis.
- Recomendaciones para guías (US10/US17).
- Consumo de `security.report.changed`: decidido sin consumidor (P3-9, opción A).

## Actualizado el 2026-11-06 (ya no está fuera de alcance)

- Outbox y publicación real de eventos en ms-03/ms-04: implementados en P0 (ms-03 con relay
  arrendado y DLT, ms-04 con sus dos outboxes).
- Reindexado completo: `POST /internal/admin/reindex` acepta `CATEGORIES`, `SITES`, `SERVICES`
  (feed de ms-04) y `ACTIVITIES`, `EVENTS` (feed de ms-03), con paginación y `updatedSince`.
