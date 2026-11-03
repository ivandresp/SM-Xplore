# Runbook operativo — ms-04 sites-and-services

Guía para operar `microservices/ms-04-sites-and-services` (catálogo de sitios, zonas,
servicios, reseñas, reservas y notificaciones). Está escrita para quien tiene que
diagnosticar el servicio en un entorno compartido, no para quien lo desarrolla.

## 1. Topología y puertos

| Componente | Puerto por defecto | Notas |
|---|---|---|
| API | `8080` (`APP_PORT` al publicar con Compose, por defecto `8084`) | WebFlux, Springdoc en `/swagger-ui.html` |
| PostgreSQL | `5432` | Esquema `smxplore`; solo vía R2DBC y Flyway (JDBC) |
| MongoDB | `27017` | Requiere replica set (`?replicaSet=rs0`) |
| Kafka | `29092` | Lo levanta `infrastructure/compose.yaml` en la red `smxplore-net` |

El `docker-compose.yml` del servicio solo define `mongo`, `mongo-init`, `postgres` y
`app`. Kafka, kafka-ui y Redis viven en el Compose raíz (`infrastructure/compose.yaml`),
que crea la red externa `smxplore-net`; sin ella el arranque de `app` falla con
`network smxplore-net declared as external, but could not be found`.

## 2. Arranque

```bash
# 1) Red y dependencias de infraestructura (Kafka, kafka-ui, Redis)
docker compose -f infrastructure/compose.yaml up -d

# 2) Almacenes del servicio (Mongo con replica set + PostgreSQL)
cd microservices/ms-04-sites-and-services
docker compose up -d mongo mongo-init postgres

# 3) Aplicación
./gradlew bootRun --args='--spring.profiles.active=local'
# o con imagen:
docker compose up -d --build app
```

Antes del primer arranque copie `.env.example` a `.env` y complete `JWT_ISSUER`,
`JWK_SET_URI` o `JWT_PUBLIC_KEY_LOCATION`; sin una de las dos fuentes de clave el
servicio no puede validar tokens.

## 3. Migraciones (Flyway)

- Las migraciones están en `src/main/resources/db/migration/` y se aplican al
  arrancar (`FLYWAY_ENABLED=true`).
- `V1__baseline_schema.sql` crea el esquema completo y es idempotente.
- `V2__normalize_legacy_reservations.sql` normaliza reservas heredadas
  (`PENDING` → `CONFIRMED`) y publica un evento `bookings.reservation.changed` por
  cada reserva migrada usando el mismo outbox; si Kafka está caído, el relay lo
  drena después.
- Flyway usa su propia conexión JDBC porque el servicio solo tiene un
  `ConnectionFactory` R2DBC: `POSTGRES_JDBC_URL` (`jdbc:postgresql://…`).
- **Nunca edite una migración ya aplicada.** Flyway valida checksums
  (`validate-on-migrate`); un cambio produce `Migration checksum mismatch`. El
  histórico es solo hacia adelante: para corregir algo, cree `V3__…sql`.
- Una base creada con el antiguo `schema.sql` se baselinea en la versión `0`
  (`FLYWAY_BASELINE_ON_MIGRATE=true`) y recibe `V1`/`V2` sin perder datos.

Comprobaciones útiles:

```sql
SELECT installed_rank, version, description, success, execution_time
FROM flyway_schema_history ORDER BY installed_rank;

SELECT version, description, success FROM flyway_schema_history WHERE success = false;
```

Si una migración falló, PostgreSQL deja la transacción revertida; corrija el script y
reinicie. No borre filas de `flyway_schema_history`.

## 4. Salud y métricas

- `GET /actuator/health` (usado por el healthcheck de Compose),
  `/actuator/health/liveness`, `/actuator/health/readiness`.
- `GET /actuator/metrics`, `GET /actuator/prometheus` (expuestos en modo solo lectura).
- En incidentes, arranque con `HEALTH_SHOW_DETAILS=always` para ver el estado de
  PostgreSQL, MongoDB y el disco.
- Señales de alerta: `outbox` con pendientes crecientes, errores 409 de capacidad,
  latencia de `/api/v1/reservations` y tasa de 5xx en el actuator.

## 5. Outbox

Hay dos outboxes con el mismo contrato de evento (`eventId`, `eventType`,
`eventVersion`, `occurredAt`, `aggregateId`, `correlationId`, `producer`, `payload`):

| Outbox | Almacén | Productor |
|---|---|---|
| Catálogo (`catalog.*`, `reviews.*`) | MongoDB, colección del outbox de catálogo | ms-04 |
| Reservas (`bookings.reservation.changed`) | PostgreSQL, tabla `reservation_outbox` | ms-04 |

Configuración (`app.outbox.*`): sondeo cada `1s`, lotes de `50`, `20` intentos,
backoff `1s` → `1m`, lease de `3m`, retención de `30` días.

Diagnóstico de atraso:

```sql
SELECT status, count(*) FROM reservation_outbox GROUP BY status ORDER BY 2 DESC;
SELECT event_id, event_type, attempts, next_attempt_at, last_error
FROM reservation_outbox WHERE status = 'FAILED' ORDER BY occurred_at DESC LIMIT 20;
```

- Un evento en `PENDING` con `next_attempt_at` en el futuro está esperando reintento;
  no lo toque salvo que el mensaje sea veneno.
- Un lease (`locked_by`/`locked_until`) mayor que `3m` se reclama solo; si se acumulan
  leases huérfanos, revise que no haya dos instancias del relay compitiendo.
- Para reintentar los `FAILED` sin tocar la base:
  `POST /api/v1/admin/outbox/reprocess` con token de rol `ADMIN`. Reinicia `attempts`,
  `next_attempt_at` y el error, y devuelve ambos outboxes a `PENDING`.
- Los eventos publicados se purgan a los `30` días (`retention-days`).

## 6. Kafka caído o consumidor detenido

- Las escrituras de la API **no dependen de Kafka**: la entidad y el evento se guardan
  en la misma transacción y el relay publica cuando el broker vuelve.
- `KAFKA_MISSING_TOPICS_FATAL=false` permite arrancar si los topics aún no existen.
- El consumidor de notificaciones usa el grupo `ms-04-notifications` y
  `auto-offset-reset=latest`: si el grupo es nuevo, no reprocesa el histórico. Para
  reprocesar, cambie `KAFKA_CONSUMER_GROUP_ID` a un grupo nuevo.
- La bandeja interna (`/api/v1/notifications`) es la fuente de verdad para el usuario;
  un evento de Kafka perdido no rompe la API pero deja al usuario sin aviso.

## 7. Capacidad, cupo por turno y overbooking

Reglas vigentes (validadas en `ReservationCreateService`):

- Capacidad **diaria** por servicio (`maxCapacity`) en el día de `bookedFor`.
- Capacidad **por turno** opcional en cada franja (`availability[].capacity`). Si
  existe, el cupo efectivo es el menor entre la diaria y la del turno.
- Zona horaria de negocio: `BOOKINGS_ZONE_ID` (`America/Bogota` por defecto). La
  ventana del turno y del día se calculan en esa zona.
- Límites por turista: `5` reservas activas y `10` creaciones por minuto.
- Antelación máxima: `365` días.
- Auto-completado: cada `60s` pasa a `COMPLETED` las reservas confirmadas cuyo turno
  terminó (`completion-batch-size=100`).

Los `409` de reserva llevan el motivo en `detail`:

| Motivo | Causa | Acción |
|---|---|---|
| `SERVICE_NOT_PUBLISHED` | el servicio está en `DRAFT` | publicar el servicio |
| `ZONE_INACTIVE` | la zona está inactiva | reactivar la zona |
| `SITE_NOT_AVAILABLE` | el `siteId` del servicio ya no existe | corregir el `siteId` del servicio |
| `SITE_NOT_PUBLISHED` | el sitio se despublicó después de crear el servicio | republicar el sitio o despublicar sus servicios |
| `OUTSIDE_SERVICE_AVAILABILITY` | la hora no cae en ninguna franja | revisar `availability` del servicio |
| `INSUFFICIENT_CAPACITY` | no quedan plazas (día o turno) | ampliar cupo o cambiar de fecha |
| `BOOKED_FOR_IN_PAST` | la fecha ya pasó | elegir otra fecha |

Los snapshots (`siteName`, `zoneName`, `zoneStatus`, `categoryName`) se refrescan al
renombrar el sitio, la zona o la categoría; el sitio se revalida en cada reserva, así
que despublicarlo bloquea reservas nuevas de inmediato.

## 8. Incidencias frecuentes

| Síntoma | Causa probable | Acción |
|---|---|---|
| `401` con token válido | `JWT_ISSUER`/`JWT_AUDIENCE` no coinciden o la clave pública/JWKS no carga | comparar con ms-01 (`/.well-known/jwks.json`) |
| `403` en rutas de catálogo | rol sin permiso (se esperan `GESTORA_DESTINOS`/`ADMIN`) | revisar el claim de rol |
| `500` al arrancar | Flyway o Mongo sin replica set | ver `flyway_schema_history` y `rs.status()` |
| `Migration checksum mismatch` | se editó una migración aplicada | restaurar el archivo desde git y crear `V3__…sql` |
| Outbox crece sin DRENAR | broker caído o `OUTBOX_ENABLED=false` | revisar Kafka y la propiedad |
| Reservas duplicadas | se reutilizó `Idempotency-Key` con otro cuerpo | es un `409` esperado; documentar la llave por operación |
| Notificaciones no llegan | consumidor detenido o grupo con offset viejo | revisar logs del grupo y el inbox del usuario |

## 9. Copias y restauración

```bash
# PostgreSQL
pg_dump -Fc -h localhost -U smxplore smxplore > smxplore-$(date +%F).dump
pg_restore -h localhost -U smxplore -d smxplore --clean --if-exists smxplore-$(date +%F).dump

# MongoDB
mongodump --uri "mongodb://localhost:27017/smxplore?replicaSet=rs0" --out ./backup
mongorestore --uri "mongodb://localhost:27017/smxplore?replicaSet=rs0" ./backup
```

Tras restaurar, arranque la aplicación: Flyway valida el esquema y no vuelve a aplicar
migraciones ya registradas. Restaure siempre PostgreSQL y MongoDB del mismo corte;
los eventos del outbox referencia ambos almacenes.

## 10. Pruebas de carga

El escenario de humo está en `microservices/ms-04-sites-and-services/loadtest/booking-flow.js`
(k6). Mide listado de catálogo, consulta de disponibilidad y creación de reservas con
`Idempotency-Key` única por iteración.

```bash
k6 run -e BASE_URL=http://localhost:8084 -e TOKEN="$TOKEN" -e SERVICE_ID="$SERVICE_ID" \
  microservices/ms-04-sites-and-services/loadtest/booking-flow.js
```

Umbrales por defecto: `p(95) < 800ms` y errores `< 1%`. Durante la prueba vigile:

- `reservation_outbox` en `PENDING`: si crece, el cuello está en Kafka o en el relay.
- Conexiones del pool R2DBC (`max-size=20`) y latencia de `reservedPeople`.
- `409` de capacidad: son esperados si agota el cupo del turno; ajústelo con
  `maxCapacity`/`capacity` antes de la corrida.
- CPU y memoria del contenedor (`docker stats`), recordando que el límite real del
  servicio son las escrituras transaccionales, no las lecturas del catálogo.
