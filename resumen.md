# SM-Xplore — Estado actual y pendientes

**Fecha de revisión:** 2026-11-04  
**Rama revisada:** `microservicios` (`a645b62`)  
**Alcance:** ms-01 a ms-05, contratos de eventos e infraestructura local.

> **Conclusión corta:** ms-01, ms-04 y ms-05 ya tienen implementación real; ms-02 funciona y ya está alineado con el JWT de ms-01; ms-03 existe en dos ramas remotas y todavía no habla el contrato de eventos del resto. El bloqueo JWT quedó resuelto en código (ms-02, ms-03, ms-04 y ms-05); los bloqueos que quedan son **eventos de ms-03** y **contrato de datos de actividades/eventos**.

---

## 1. Resumen ejecutivo

El repositorio ya no es solo documentación. El estado real es:

- **ms-01** (usuarios, autenticación, anomalías, reseñas de guías/prestadoras): implementado y con CI.
- **ms-02** (reportes de seguridad): implementado, con pruebas y JWT alineado a ms-01 (RS256, roles en inglés).
- **ms-03** (actividades, eventos, reservas y reseñas): **no está integrado en `microservicios`**. Solo está el README placeholder. La implementación vive en dos ramas remotas:
  - `origin/ms-03-activities-and-events` (`e59886b`): variante con reservas multi-ítem.
  - `origin/ms-03-symbolic-reservations` (`847e832`): variante más nueva, con reservas simbólicas de un ítem, idempotencia y cierre automático. **Es la rama recomendada como base.**
- **ms-04** (sitios, servicios, reservas, reseñas y notificaciones): implementado, con outbox, Kafka, feed de reindexado y CI.
- **ms-05** (recomendaciones): consumidores Kafka, Neo4j, caché, métricas y reindexado del catálogo implementados.

Los bloqueos de integración más importantes son:

1. **JWT (resuelto en código).** ms-01 emite RS256 con roles en inglés (`TOURIST`, `GUIDE`, `SERVICE_PROVIDER`, `DESTINATION_MANAGER`). ms-02, ms-03, ms-04 y ms-05 ya usan RS256/JWKS, issuer `sm-xplore-ms-01`, audiencia `sm-xplore-services`, roles en inglés y rechazo de `scope=mfa`. Los cambios de ms-01/02/04/05 quedaron en el commit `693ca40`; queda mergear la rama `fix/ms-03-jwt` de ms-03.
2. **ms-03 no publica eventos.** No tiene outbox ni publica `activities.*`, `reviews.review.changed` ni `bookings.reservation.changed`. ms-05 no puede proyectar actividades/eventos y ms-04 no puede notificar sus reservas.
3. **El modelo de ms-03 no coincide con los contratos.** Usa `category` como texto, no tiene `categoryId` UUID, coordenadas ni `updatedAt`, y sus estados (`PAUSED`, `POSTPONED`, `NO_SHOW`) no están en los esquemas de eventos.

Además, la documentación está desactualizada: `AGENTS.md`, el README de ms-04 y los documentos de ms-05 describen estados anteriores del repositorio.

---

## 2. Estado verificado

| Servicio | Rama / commit | Estado funcional | Verificación en esta revisión |
|---|---|---|---|
| ms-01 | `microservicios` | Implementado | **126 pruebas ejecutadas, 0 fallos** (2026-11-04). |
| ms-02 | `microservicios` | Implementado; JWT alineado | **38 pruebas ejecutadas, 0 fallos** (2026-11-04). |
| ms-03 | `origin/ms-03-symbolic-reservations` + rama local `fix/ms-03-jwt` | Funcional, no integrado | **172 pruebas ejecutadas, 0 fallos** (2026-11-04, Java 25). |
| ms-03 | `origin/ms-03-activities-and-events` `e59886b` | Variante multi-ítem | 157 pruebas declaradas; no re-ejecutada. |
| ms-04 | `microservicios` | Implementado; JWT alineado | **71 pruebas ejecutadas, 0 fallos** (2026-11-04). |
| ms-05 | `microservicios` | Implementado; JWT alineado | **116 pruebas ejecutadas, 0 fallos** (2026-11-04). |

Notas de la verificación:

- Las pruebas de ms-03 se ejecutaron en un worktree separado con `JAVA_HOME=/usr/lib/jvm/java-25-openjdk-amd64`. El `gradlew` de esa rama está en modo `100755` en la rama local.
- ms-03 solo tiene una prueba con Testcontainers (adaptador PostgreSQL). No hay prueba de contexto completo con Kafka/Mongo ni flujo E2E con ms-01/ms-04/ms-05.
- ms-02 y ms-03 usan RS256; sus pruebas validan el contrato de ms-01 con llaves de prueba.
- **Bloqueo 1 resuelto:** ms-01 migró sus códigos de rol a inglés; ms-02 pasó de HS256 a RS256/JWKS, roles en inglés y `scope=mfa`; ms-05 exige el issuer/audiencia de ms-01; ms-04 agregó `GUIDE` y rechazo de `scope=mfa`; ms-03 tiene los mismos cambios en la rama local `fix/ms-03-jwt` (commits `e3eb783` y `c992ecc`, 172 pruebas). Los cambios de ms-01/02/04/05 quedaron en el commit `693ca40` de `microservicios`.

### Ramas de ms-03

| Rama | Commit | Archivos | Pruebas | Reservas |
|---|---|---:|---:|---|
| `origin/ms-03-activities-and-events` | `e59886b` | 253 | 157 | Multi-ítem, estados `PENDING`/`CONFIRMED`/`REJECTED`/`CANCELLATION_REQUESTED`, confirmación manual. |
| `origin/ms-03-symbolic-reservations` | `847e832` | 227 | 168 (+4 de JWT en `fix/ms-03-jwt`) | Un ítem, nace `CONFIRMED`, idempotencia, cancelación con ventana de 24 h, cierre automático. |

Comparación:

- `ms-03-symbolic-reservations` está **5 commits por delante** de `ms-03-activities-and-events`.
- Ambas parten de `develop` (`8a90f6f`) y están **83 commits por detrás** de `microservicios`.
- Por tanto, cualquiera de las dos debe rebasarse/mergearse con la rama actual antes de integrarse.
- La variante recomendada es `ms-03-symbolic-reservations` porque es más reciente, tiene idempotencia y su modelo de reservas es coherente con el de ms-04 (reserva simbólica, sin pago). Si el producto exige reservas multi-ítem, habría que decidirlo antes de rebasar.

---

## 3. Estado por servicio

### 3.1 ms-01 — Usuarios, autenticación y anomalías

**Implementado:**

- Registro de turistas y prestadoras, login, JWT RS256/JWKS, MFA TOTP, recuperación de contraseña.
- Perfil propio, cambio de contraseña, logout con `auth_version`.
- Administración de usuarios: crear, listar, buscar, cambiar rol, aprobar/rechazar prestadoras, bloquear, suspender, eliminar con anonimización, reset administrativo, auditoría.
- Reseñas de guías y prestadoras en MongoDB, con respuestas, reportes, moderación, reputación y filtro de IA.
- Anomalías (RF99–RF102), monitoreo administrativo, disponibilidad PostgreSQL/MongoDB, exportación CSV/PDF.
- Retención de datos, rate limit por IP, bloqueo por cuenta, cabeceras de seguridad, perfil `prod`.
- Códigos de rol migrados a inglés (`TOURIST`, `GUIDE`, `SERVICE_PROVIDER`, `DESTINATION_MANAGER`, `ADMIN`) con migración Flyway `V10` y alias de lectura para datos históricos.
- Los `targetType` de reseñas (`GUIA`, `PRESTADORA_SERVICIOS`) se mantienen como concepto separado; no son códigos de rol.
- Docker, Compose con perfil `full`, CI con Trivy y smoke test, Dependabot.
- Pruebas con Testcontainers, ArchUnit y Spotless.

**Pendiente o follow-up:**

- Publicar la imagen en un registry cuando se elija destino de despliegue.
- Indicadores de monitoreo cross-service (reservas, sitios, actividades, eventos) cuando ms-03/ms-04 expongan sus contratos.
- Rate limiter distribuido solo si ms-01 escala horizontalmente (hoy es en memoria por instancia).
- Rotación de llaves JWT con `kid`/keyring; hoy las llaves son estáticas.
- ms-01 valida `ver` al instante, pero ms-02–ms-05 no. Los demás servicios aceptan tokens revocados hasta su `exp`.
- ms-01 no expone un endpoint interno para que ms-04 obtenga correos/contactos; ms-04 mantiene `notification_contacts` manualmente.
- El filtro de IA de reseñas no está benchmarkeado y corre síncrono; el propio README pide confirmar el contrato real de littleLLM.
- `AGENTS.md` está desactualizado: dice que solo existe ms-01 y que ms-03 es un placeholder.

### 3.2 ms-02 — Reportes de seguridad

**Implementado:**

- Reportes de seguridad en MongoDB con título, descripción, ubicación, fotos y estado.
- Comentarios por reporte.
- Flujo `ONGOING → REPORTED → SOLVED`.
- Paginación por cursor, migración de datos legados, índices.
- Seguridad JWT RS256/JWKS alineada con ms-01, roles en inglés, rechazo de `scope=mfa`.
- Outbox transaccional en MongoDB y publicación de `security.report.changed` por Kafka.
- Consulta geolocalizada `GET /api/v1/security-reports/nearby` con índice `2dsphere`.
- Errores RFC 9457 (`application/problem+json`) y cabeceras de seguridad.
- Compose con MongoDB en replica set, Kafka y red `smxplore-net`.
- CI con verificación, Trivy y smoke test de imagen.
- Swagger, health y pruebas de integración con Testcontainers.

**Pendientes:**

- Consumidor real de `security.report.changed` para notificaciones/alertas (analítica o bandeja).
- Rate limiting de creación de reportes si se expone fuera del aula.
- Entrega push/correo de alertas geolocalizadas.

### 3.3 ms-03 — Actividades, eventos, reservas y reseñas

**Qué existe en las ramas remotas:**

- CRUD y consulta de actividades y eventos.
- Disponibilidad, cupos, capacidad, cambio de estado, cancelación.
- Búsqueda de eventos, validación de existencia y estimación de afluencia.
- Reservas simbólicas de actividades/eventos (según la rama).
- Reseñas en MongoDB con respuestas, reportes y métricas de reputación.
- PostgreSQL para actividades/eventos/reservas, MongoDB para reseñas.
- Dockerfile y Compose propios; puerto 8082.
- Pruebas unitarias/de slice y una prueba Testcontainers de PostgreSQL.

El esquema se aplica con `SQL_INIT_MODE=always` y `schema.sql`; incluye una migración destructiva y se re-ejecuta en cada arranque. No es apto para producción: debe migrarse a Flyway.

**Qué le falta para integrarse:**

- Rebase sobre `microservicios`; hoy está 83 commits atrás.
- Integrar la rama local `fix/ms-03-jwt` (RS256, roles en inglés, `scope=mfa`); el resto de la seguridad ya está resuelto en esa rama.
- `categoryId` UUID en lugar de `category` string.
- `latitude`/`longitude` para actividades y eventos (ms-05 las exige).
- `updatedAt` en actividades, eventos, reservas y reseñas.
- Outbox transaccional y publicación de los cuatro topics.
- Feed de exportación para el reindexado de ms-05.
- Flyway en lugar de `spring.sql.init`/`schema.sql`.
- CI, pruebas de contrato y pruebas E2E.
- Extensión de notificaciones en ms-04 (o bandeja propia) para reservas de actividades/eventos.
- Runbook y documentación de contrato con el frontend.

### 3.4 ms-04 — Sitios, servicios, reservas y notificaciones

**Implementado:**

- Zonas en PostgreSQL; sitios, servicios, categorías, reseñas, reportes y notificaciones en MongoDB.
- Reservas simbólicas de servicios en PostgreSQL, con cupos, idempotencia, cancelación y auto-completado.
- Outbox transaccional MongoDB y PostgreSQL, relay con lease, reintentos, retención y reproceso.
- Publicación de `catalog.*`, `reviews.review.changed` y `bookings.reservation.changed` (solo `SERVICE`).
- Consumidor de `bookings.reservation.changed` para la bandeja de notificaciones.
- Feed `GET /api/v1/admin/catalog/export` para reindexar ms-05.
- Seguridad RS256/JWKS o PEM, roles en inglés.
- Tests de contrato, Testcontainers y CI con Trivy/smoke test.

**Pendientes / bloqueos:**

- El consumidor de reservas **ignora los ítems `ACTIVITY` y `EVENT`**. Si ms-03 publica sus reservas, ms-04 no genera notificaciones para ellas.
- `UserRole` ya incluye `GUIDE`, pero el consumidor de notificaciones todavía no lo usa como destinatario.
- No valida `ver` ni rechaza tokens revocados por ms-01; sí rechaza `scope=mfa`.
- La entrega real de notificaciones es solo correo best-effort; no hay push.
- El README dice que ms-05 todavía no consume el feed de catálogo, pero ms-05 ya lo consume. Documentación desactualizada.
- No hay alerta de baja disponibilidad de cupos (RF66) ni notificaciones de eventos.
- No hay endpoint interno para que ms-04 obtenga contactos desde ms-01; el alta de `notification_contacts` es manual por ADMIN.

### 3.5 ms-05 — Recomendaciones

**Implementado:**

- API de recomendaciones, preferencias y vistas.
- Ranking por afinidad, calidad bayesiana, proximidad y novedad; diversidad por sitio/categoría.
- Neo4j como proyección, constraints, seed y tests.
- Consumidores Kafka para los seis topics de ms-05; idempotencia, descarte de eventos viejos, reintentos y DLQ.
- Agregados de reseñas (`ratingAvg`, `ratingCount`, `lastReviewAt`).
- Caché Caffeine + Redis, métricas Prometheus y retención de `ProcessedEvent`.
- Reindexado del catálogo desde el feed de ms-04.

**Pendientes / bloqueos:**

- **Rol alineado:** la seguridad ya exige `TOURIST` y `ADMIN`, igual que ms-01.
- El reindexado solo entiende `CATEGORIES`, `SITES` y `SERVICES` de ms-04. No hay `ACTIVITIES`/`EVENTS` ni una base URL para ms-03.
- No valida `ver` ni rechaza tokens revocados por ms-01; sí rechaza `scope=mfa`.
- El issuer y la audiencia tienen defaults de ms-01, pero conviene fijarlos explícitamente en producción.
- Los documentos `ESTADO-Y-PENDIENTES.md` y `fase-2-consumidores.md` están desactualizados: dicen que ms-04 no publica eventos reales y que ms-05 no consume su feed. También hay discrepancia en el número de pruebas (88 vs 116).
- No hay feed de reseñas para reconstruir el grafo si se pierde la proyección y Kafka ya purgó los eventos. Solo se reindexa catálogo.

---

## 4. Integración transversal: matriz de bloqueos

### 4.1 JWT

| Servicio | Algoritmo | Issuer por defecto | Roles que espera | Valida `ver`/`mfa` | Estado |
|---|---|---|---|---|---|
| ms-01 | RS256 | `sm-xplore-ms-01` | `TOURIST`, `GUIDE`, `SERVICE_PROVIDER`, `DESTINATION_MANAGER`, `ADMIN` | Sí / parcial (solo admin) | Correcto |
| ms-02 | RS256 | `sm-xplore-ms-01` | `TOURIST`, `GUIDE`, `SERVICE_PROVIDER`, `DESTINATION_MANAGER`, `ADMIN` | No / rechaza `scope=mfa` | **Alineado** |
| ms-03 | RS256 | `sm-xplore-ms-01` | `TOURIST`, `GUIDE`, `SERVICE_PROVIDER`, `DESTINATION_MANAGER`, `ADMIN` | No / rechaza `scope=mfa` | **Alineado en `fix/ms-03-jwt`** |
| ms-04 | RS256 | `sm-xplore-ms-01` | `TOURIST`, `GUIDE`, `SERVICE_PROVIDER`, `DESTINATION_MANAGER`, `ADMIN` | No / rechaza `scope=mfa` | **Alineado** |
| ms-05 | RS256 | `sm-xplore-ms-01` | `TOURIST`, `ADMIN` | No / rechaza `scope=mfa` | **Alineado** |

Contrato que deben cumplir todos:

- Solo RS256; JWKS en producción, PEM en local/test.
- `iss=sm-xplore-ms-01`, `aud=sm-xplore-services`.
- Identidad desde `sub` (con fallback a `id`/`userId` si se decide).
- `role` con los códigos en inglés.
- Rechazar tokens con `scope=mfa`.
- Si se necesita revocación inmediata, validar `ver` contra ms-01; hoy solo ms-01 lo hace.
- Los administradores con MFA pendiente (`mfa=false`) no deberían poder operar en otros servicios si se exige MFA global.

### 4.2 Eventos

| Topic | Productor actual | Consumidor | Estado |
|---|---|---|---|
| `catalog.category.changed` | ms-04 | ms-05 | OK |
| `catalog.site.changed` | ms-04 | ms-05 | OK |
| `catalog.service.changed` | ms-04 | ms-05 | OK |
| `activities.activity.changed` | **nadie** | ms-05 | **Falta ms-03** |
| `activities.event.changed` | **nadie** | ms-05 | **Falta ms-03** |
| `reviews.review.changed` | ms-04 (sitios/servicios) | ms-05 | Falta ms-03 para actividades/eventos |
| `bookings.reservation.changed` | ms-04 (solo `SERVICE`) | ms-04 (notificaciones), analítica | Falta ms-03 para actividades/eventos; ms-04 ignora esos ítems |

Reglas del contrato que ms-03 debe respetar:

- La key de Kafka es el `aggregateId`.
- `eventVersion=1`, `producer=ms-03`, `eventType` igual al topic.
- `eventId` UUID único para deduplicar; `correlationId` obligatorio.
- Los payloads tienen `additionalProperties: false`: no se pueden agregar campos fuera del esquema.
- La entrega es at-least-once; los consumidores deduplican por `eventId`.

---

## 5. Qué debe tener ms-03 para que los demás servicios funcionen

Esta sección es el contrato mínimo de integración. No es una lista de deseos: si falta algo de aquí, ms-04/ms-05 no pueden operar con datos reales.

### 5.1 Base técnica y entrega

- Rebase/merge de `ms-03-symbolic-reservations` sobre `microservicios`; conservar Java 25, Spring Boot 4.1.2, WebFlux, R2DBC y MongoDB reactivo.
- Gradle wrapper 9.8.2 dentro del servicio, con `gradlew` ejecutable (`100755`).
- Dockerfile multi-etapa, usuario no privilegiado, healthcheck y Compose con PostgreSQL, MongoDB en **replica set** y red externa `smxplore-net` para Kafka.
- Flyway para migraciones versionadas; dejar `schema.sql` solo como baseline local, no como mecanismo de producción.
- CI `.github/workflows/ms-03-ci.yml`: tests, build de imagen, Trivy y smoke test.
- OpenAPI/Swagger, actuator health y métricas Prometheus.
- Errores RFC 9457 (`ProblemDetail`) para mantener el mismo contrato que ms-01/ms-04/ms-05.

### 5.2 Seguridad JWT

- Decoder RS256 con JWKS (`JWK_SET_URI`) o PEM (`JWT_PUBLIC_KEY_LOCATION`).
- `JWT_ISSUER=sm-xplore-ms-01`, `JWT_AUDIENCE=sm-xplore-services`.
- Rechazar `scope=mfa`; no aceptar el challenge token como token de acceso.
- Roles en español y reglas por endpoint:
  - Eventos: `DESTINATION_MANAGER` y `ADMIN`.
  - Actividades: `GUIDE` dueño, `DESTINATION_MANAGER`/`ADMIN` como supervisión.
  - Reseñas: crear solo `TOURIST`; responder `GUIDE` dueño / `DESTINATION_MANAGER` del evento / `ADMIN`; moderar `ADMIN`/`DESTINATION_MANAGER`.
  - Reservas: crear/cancelar `TOURIST` dueño; consultar/gestionar `GUIDE`, `DESTINATION_MANAGER` y `ADMIN`.
- Identidad desde `sub`; nombre desde `name`; no confiar en IDs de usuario del body.
- Si se requiere MFA global, rechazar tokens `mfa=false` para operaciones administrativas.
- Pruebas con tokens RS256 reales, no con `mockJwt` sin roles.

### 5.3 Modelo de datos y contrato de catálogo

**Actividad**

- `activityId` UUID, `name`, `categoryId` UUID, `siteId` UUID, `guideId` UUID.
- `latitude`, `longitude` (obligatorias para ms-05).
- `price`, `status`, `updatedAt`.
- Estados internos actuales: `DRAFT`, `PUBLISHED`, `PAUSED`, `CANCELLED`.
- El contrato externo solo admite `DRAFT`, `PUBLISHED`, `INACTIVE`.

**Evento**

- `eventId` UUID, `name`, `categoryId` UUID, `siteId` UUID, `siteName`.
- `managerId` UUID, `latitude`, `longitude`.
- `price`, `startsAt`, `endsAt`, `status`, `updatedAt`.
- Estados internos actuales: `DRAFT`, `PUBLISHED`, `POSTPONED`, `CANCELLED`.
- El contrato externo solo admite `DRAFT`, `PUBLISHED`, `INACTIVE`.

**Reserva**

- `reservationId`, `touristId`, `itemType` (`ACTIVITY`/`EVENT`), `itemId`.
- `people`, `unitPrice`, `total`, `status`, `bookedFor`, `createdAt`, `updatedAt`.
- `providerId` (guía de la actividad o gestora del evento) y nombre del ítem para notificaciones.
- Si se mantiene la rama simbólica: una reserva = un ítem; el evento publica un array de un elemento.
- Estados internos: `CONFIRMED`, `CANCELLED`, `NO_SHOW`, `COMPLETED`.
- El contrato externo admite `PENDING`, `CONFIRMED`, `CANCELLED`, `COMPLETED`.

**Reseña**

- `reviewId`, `touristId`, `itemType` (`ACTIVITY`/`EVENT`), `itemId`, `rating`.
- `comment`, `status`, `createdAt`, `updatedAt`, respuesta de la entidad.
- Estados: `PUBLISHED`, `HIDDEN`, `DELETED`.
- Una reseña por turista e ítem; la respuesta solo la escribe la entidad a cargo.
- Métricas de reputación deben excluir `HIDDEN`/`DELETED`.

**Mapeo de estados recomendado** (mientras no se cree un contrato v2):

| Estado interno | Valor en evento | Motivo |
|---|---|---|
| `DRAFT` | `DRAFT` | Directo |
| `PUBLISHED` | `PUBLISHED` | Directo |
| `PAUSED` (actividad) | `INACTIVE` | No se recomienda mientras está pausada |
| `POSTPONED` (evento) | `INACTIVE` | No se recomienda mientras está postergado |
| `CANCELLED` | `INACTIVE` | No se recomienda |
| `NO_SHOW` (reserva) | `CANCELLED` | El contrato no tiene `NO_SHOW`; notifica liberación de cupo |

Si se prefiere conservar esos estados en el evento, hay que crear `v2` de los esquemas y actualizar ms-05/ms-04. Para la primera integración, el mapeo anterior es el camino de menor riesgo.

### 5.4 Outbox y eventos

ms-03 debe implementar el mismo patrón que ms-04:

- Outbox PostgreSQL para actividades, eventos y reservas.
- Outbox MongoDB (o PostgreSQL, si se unifica) para reseñas, dentro de una transacción Mongo; para eso MongoDB debe ser replica set.
- Relay con claim atómico, lease, reintentos con backoff, métricas, retención y reproceso administrativo.
- Escritura de entidad + evento en la misma transacción.
- Captura de `X-Correlation-Id`; si falta, generar uno.
- Publicación con key = `aggregateId`.

Eventos mínimos y campos exactos:

| Topic | Payload obligatorio |
|---|---|
| `activities.activity.changed` | `activityId`, `name`, `categoryId`, `siteId`, `latitude`, `longitude`, `price`, `status`, `updatedAt` |
| `activities.event.changed` | `eventId`, `name`, `categoryId`, `siteId`, `latitude`, `longitude`, `price`, `startsAt`, `endsAt`, `status`, `updatedAt` |
| `reviews.review.changed` | `reviewId`, `touristId`, `itemType`, `itemId`, `rating`, `status`, `updatedAt` |
| `bookings.reservation.changed` | `reservationId`, `touristId`, `bookedFor`, `status`, `items[]`, `updatedAt` |

Para `bookings.reservation.changed`, cada ítem debe llevar:

- `itemType`: `ACTIVITY` o `EVENT`.
- `itemId`: UUID del ítem.
- `providerId`: guía de la actividad o gestora del evento.
- `serviceName`: nombre de la actividad/evento (el contrato lo llama `serviceName` por compatibilidad).
- `people`: entero >= 1.
- `total`: número >= 0.

Disparadores de publicación:

- Actividad: crear, actualizar, cambiar disponibilidad/cupos, cambiar estado, cancelar.
- Evento: crear, cambiar estado, postergar/cancelar, cambiar afluencia si afecta el payload (hoy no está en el contrato).
- Reseña: crear (`PUBLISHED`), moderar (`HIDDEN`/`DELETED`), aceptar reporte (pasar a `HIDDEN`).
- Reserva: crear (`CONFIRMED`), cancelar (`CANCELLED`), no-show (`CANCELLED` en el evento), completar (`COMPLETED`), cierre automático.

No se debe publicar un evento con campos extra: los esquemas tienen `additionalProperties: false`.

### 5.5 Feed de reindexado para ms-05

ms-03 debe exponer un endpoint equivalente al de ms-04:

```http
GET /api/v1/admin/catalog/export?entity=ACTIVITIES&updatedSince=...&page=0&size=200
GET /api/v1/admin/catalog/export?entity=EVENTS&updatedSince=...&page=0&size=200
```

Requisitos:

- Solo `ADMIN`; ms-05 reenvía el token del administrador que invoca el reindexado.
- Respuesta con `content[]`, `page`, `size`, `total`, `hasNext`, `updatedSince`.
- Cada ítem con `eventType`, `aggregateId`, `updatedAt` y `payload` idéntico al evento de Kafka.
- Orden estable por `updatedAt` e `id`; `size` máximo 200.
- `updatedSince` filtra por `updatedAt >= valor`.
- Esto permite reconstruir la proyección Neo4j sin depender de Kafka.

**Cambio necesario en ms-05:** hoy `CatalogEntity` solo tiene `CATEGORIES`, `SITES`, `SERVICES`, y `CatalogExportClient` apunta a una sola base URL (ms-04). Hay que:

- Agregar `ACTIVITIES` y `EVENTS` al enum.
- Configurar una base URL para ms-03 (por ejemplo `ACTIVITIES_EXPORT_BASE_URL`) o un gateway que enrute por entidad.
- Extender `expectedEventType` con `activities.activity.changed` y `activities.event.changed`.
- Añadir pruebas de reindexado contra ms-03.

Si se quiere reconstruir también el historial de reseñas, hace falta un feed `REVIEWS` o conservar los eventos de reseña en Kafka el tiempo suficiente. Hoy el reindexado de ms-05 solo cubre catálogo.

### 5.6 Reservas y notificaciones

- ms-03 publica `bookings.reservation.changed`; eso es lo que necesitan ms-04 (notificaciones), analítica y futuros servicios.
- **ms-04 hoy descarta los ítems que no sean `SERVICE`**. Para que las reservas de actividades/eventos generen notificaciones hay que extender `ReservationChangedEventDecoder`/`NotificationCreateService`:
  - Usar `GUIDE` como destinatario de las notificaciones de actividades en ms-04.
  - Notificar al turista y al guía (actividad) o a la gestora (evento).
  - Definir el rol del destinatario en `Notification`.
- Alternativa: que ms-03 tenga su propia bandeja de notificaciones y endpoint `/api/v1/notifications`. No se recomienda porque el frontend tendría dos bandejas y dos contratos.
- Las alertas de baja disponibilidad (RF66) siguen sin implementarse en ningún servicio; se pueden resolver en ms-03 con un job que revise cupos o en ms-04.

### 5.7 Relación con ms-04 (sitios, categorías, coordenadas)

ms-03 no puede inventar `siteId`, `categoryId`, `siteName`, `managerId` ni coordenadas. Opciones:

1. **Llamada REST a ms-04** al crear/actualizar actividades y eventos, reenviando el token del usuario o usando una credencial de servicio. Requiere que ms-04 exponga un endpoint interno o permita la consulta con el token actual.
2. **Proyección local por eventos**: ms-03 consume `catalog.site.changed` y `catalog.category.changed` y mantiene una copia de sitios/categorías. Es más resiliente y evita llamadas síncronas, pero agrega consumidores y tablas de lectura.

Recomendación para la primera entrega: snapshot validado por REST al crear/actualizar, y refresco posterior por eventos de catálogo. Como mínimo:

- Validar que `siteId` existe, está `PUBLISHED` y tiene categoría/coordenadas.
- Validar que `categoryId` existe y su `scope` es `ACTIVITY` o `EVENT`.
- Guardar `siteName`, `managerId`, `latitude`, `longitude` como snapshot.
- Refrescar el snapshot cuando cambie el sitio o la gestora (hoy hay un `TODO` explícito en ms-03).

### 5.8 Reseñas y moderación

- Restringir la creación a `TOURIST` (hoy cualquier autenticado puede crear).
- Agregar `status` y `updatedAt` a las reseñas.
- Al resolver un reporte como aceptado, pasar la reseña a `HIDDEN` y publicar `reviews.review.changed`; hoy el reporte se resuelve pero la reseña sigue visible.
- Excluir `HIDDEN`/`DELETED` de métricas y listados públicos.
- Alinear nombres de estados de reportes con ms-04 (`OPEN`/`ACCEPTED`/`DISMISSED`) o documentar la diferencia.
- Considerar el filtro de IA de ms-01 para reseñas de actividades/eventos; hoy ms-03 y ms-04 solo tienen moderación manual.

### 5.9 Pruebas y CI

Mínimo para aceptar ms-03:

- Unitarias de dominio y casos de uso.
- Web slice con RS256 y roles en inglés.
- Testcontainers con PostgreSQL, MongoDB replica set y Kafka.
- Tests de contrato que validen los fixtures de `contracts/events/` y los eventos serializados.
- Outbox: dos relays concurrentes, reintentos, lease, retención y reproceso.
- E2E:
  - Token real de ms-01 (o llave de prueba equivalente).
  - Crear categoría/sitio en ms-04.
  - Crear actividad/evento en ms-03.
  - Consumir `activities.*` en ms-05 y verificar recomendaciones.
  - Crear reserva en ms-03 y verificar notificación en ms-04.
  - Crear reseña en ms-03 y verificar agregados en ms-05.
- CI con `./gradlew test bootJar`, Trivy y smoke test.

### 5.10 Documentación y operación

- README completo con stack, endpoints, estados, variables y ejemplos.
- Runbook: Flyway, outbox, capacidad, reservas, DLQ/retry, backups, restauración.
- `CHANGELOG.md` actualizado.
- `.env.example` con todas las variables, sin secretos.
- Actualizar `contracts/README.md` (ms-03 como productor).
- Actualizar `AGENTS.md` y los README de ms-04/ms-05 que quedaron obsoletos.

---

## 6. Cambios necesarios en los otros servicios

| Servicio | Cambio | Prioridad |
|---|---|---|
| ms-02 | Implementar el consumidor de `security.report.changed` para alertas/notificaciones y rate limiting si se expone fuera del aula. | Media |
| ms-04 | Extender notificaciones a `ACTIVITY`/`EVENT` y usar `GUIDE` como destinatario cuando corresponda. | Alta |
| ms-04 | Actualizar README (ms-05 ya consume el feed) y documentar notificaciones. | Media |
| ms-05 | Extender `CatalogEntity`/`CatalogExportClient` para reindexar ms-03. El rol ya está alineado. | Alta |
| ms-05 | Actualizar docs y conteo de pruebas; validar `ver` si se requiere revocación inmediata. | Media |
| ms-01 | Opcional: endpoint interno de contactos para notificaciones; monitoreo cross-service; publicación de imagen. | Media |
| ms-01 | Ejecutar la migración Flyway `V10` en cada entorno y forzar re-login: los JWT emitidos antes del cambio conservan roles en español y expiran en 60 minutos. | Alta |
| Contratos | Decidir si `NO_SHOW`, `PAUSED` y `POSTPONED` se mapean o se crea `v2`; agregar ejemplo de reserva `ACTIVITY`. | Alta |
| Infraestructura | Compose integral o overrides de puertos; hoy ms-01/02/03/04 publican 5432/27017 y colisionan. | Alta |
| Infraestructura | Configurar `JWT_ISSUER`, `JWT_AUDIENCE` y `JWK_SET_URI` en todos los compose. | Alta |

---

## 7. Orden recomendado de trabajo

1. **Rebasar ms-03** (`ms-03-symbolic-reservations`) sobre `microservicios`, integrar `fix/ms-03-jwt`, corregir el modo de `gradlew` y dejar el build verde.
2. **Cerrar JWT**: revisar/commitear los cambios de ms-02/04/05 y mergear `fix/ms-03-jwt`.
3. **Alinear el modelo**: `categoryId`, coordenadas, `updatedAt`, estados externos y migraciones Flyway.
4. **Implementar outbox y eventos** de actividades, eventos, reseñas y reservas; agregar tests de contrato.
5. **Exponer el feed de exportación** y extender ms-05 para reindexar actividades/eventos.
6. **Extender ms-04** para notificar reservas de actividades/eventos.
7. **CI, Docker, runbook y documentación**.
8. **E2E completo** ms-01 → ms-04 → ms-03 → ms-05, más notificaciones en ms-04.
9. **Cerrar deuda de ms-02** (consumidor de eventos/rate limit) y actualizar documentación global.

### Primeras órdenes concretas sugeridas

- `ms-03`: rebase + integrar `fix/ms-03-jwt` + tests de seguridad.
- `ms-03`: migración de esquema a `categoryId`/coordenadas/`updatedAt` + Flyway.
- `ms-03`: outbox PostgreSQL/Mongo + publicación de los cuatro topics + tests de contrato.
- `ms-03`: endpoint `admin/catalog/export` para `ACTIVITIES`/`EVENTS` + pruebas de paginación.
- `ms-05`: reindexado de ms-03 (el rol ya está alineado).
- `ms-04`: notificaciones para `ACTIVITY`/`EVENT` + rol `GUIDE` como destinatario.
- `ms-02`: consumidor de `security.report.changed` y rate limiting si se expone fuera del aula.
- `docs/infra`: actualizar `AGENTS.md`, READMEs, contracts e compose integral.
- `ms-01`: ejecutar `V10`, forzar re-login y actualizar cualquier cliente que filtre por `?role=` o lea el claim `role`.

---

## 8. Decisiones pendientes antes de implementar

1. **Modelo de reservas de ms-03:** ¿se acepta la reserva simbólica de un ítem de `ms-03-symbolic-reservations` o se conserva el multi-ítem de `ms-03-activities-and-events`?
2. **Estados externos:** ¿se mapean `PAUSED`/`POSTPONED`/`NO_SHOW` a `INACTIVE`/`CANCELLED`, o se crea `v2` de los contratos?
3. **Coordenadas y categorías:** ¿snapshot validado por REST contra ms-04, o proyección local alimentada por eventos de catálogo?
4. **Notificaciones:** ¿se centralizan en ms-04 (recomendado) o ms-03 implementa su propia bandeja?
5. **Reseñas:** ¿se mantienen dos colecciones (`activity_reviews`, `event_reviews`) o se unifica en `reviews` con `itemType`?
6. **Moderación:** ¿se replica el filtro de IA de ms-01 en ms-03/ms-04 o la moderación sigue siendo manual?
7. **Pagos e itinerario:** el SRS pide PSE (RF60–RF61) e itinerario (RF55); el diseño actual de ms-03/ms-04 es simbólico y no los implementa. ¿Está aceptado como alcance del curso o falta implementarlo?
8. **Autenticación servicio-a-servicio:** ¿ms-03 llama a ms-04 con el token del usuario o se agrega una credencial interna?
9. **Alcance de ms-02:** el producto de eventos ya está definido (`security.report.changed`); falta decidir quién lo consume (notificaciones, analítica o una bandeja propia).
10. **`targetType` de reseñas de ms-01:** hoy sigue en `GUIA`/`PRESTADORA_SERVICIOS` porque no es un rol. ¿Se migra también a `GUIDE`/`SERVICE_PROVIDER` o se deja como contrato separado?

---

## 9. Criterios de aceptación para dar ms-03 por integrado

- [ ] `./gradlew test` y `./gradlew bootJar` en verde con Java 25.
- [ ] CI de ms-03 con tests, imagen, Trivy y smoke test.
- [ ] Un token real de ms-01 (RS256, rol `TOURIST`/`GUIDE`/`DESTINATION_MANAGER`) funciona en ms-03.
- [ ] ms-03 rechaza HS256, issuer incorrecto, audiencia incorrecta y `scope=mfa`.
- [ ] Actividades y eventos tienen `categoryId` UUID, coordenadas y `updatedAt`.
- [ ] Se publican los cuatro topics con el payload exacto de `contracts/events/`.
- [ ] ms-05 proyecta actividades, eventos y reseñas de ms-03; el reindexado las reconstruye.
- [ ] ms-04 crea notificaciones para reservas de actividades/eventos.
- [ ] Los tests de contrato validan todos los fixtures y los eventos publicados.
- [ ] Existe un E2E reproducible con ms-01, ms-04, ms-03 y ms-05.
- [ ] README, runbook, `.env.example` y `contracts/README.md` actualizados.

---

## 10. Referencias útiles

- `contracts/README.md` y `contracts/events/*.v1.json`: esquemas y reglas de versionado.
- `microservices/ms-04-sites-and-services`: mejor referencia de outbox, feed de exportación, notificaciones y pruebas de contrato.
- `microservices/ms-05-recommendations/docs/fase-2-consumidores.md`: contrato asumido por el consumidor.
- `microservices/ms-05-recommendations/docs/ESTADO-Y-PENDIENTES.md`: pendientes de ms-05 (actualizar antes de usarlo como fuente).
- `microservices/ms-03-activities-and-events/README.md` (rama `ms-03-symbolic-reservations`): funcionalidad actual de ms-03.
- `docs/models/uc-lvl-1/gestionar-actividades`, `gestionar-eventos`, `gestionar-reservas`, `gestionar-reseñas`: casos de uso de referencia.
- `docs/requirements/SM-Xplore_SRS.docx`: RF41–RF77 para actividades, eventos, reservas y reseñas.
