# Infraestructura compartida

Este directorio contiene la infraestructura de **desarrollo local** que comparten los
microservicios: Kafka en modo KRaft, `kafka-ui` y Redis. **No es una unidad de despliegue de
producción.**

## Por qué está fuera de los microservicios

Kafka y Redis no pertenecen a ms-05. Los usan ms-03, ms-04 y, más adelante, notificaciones y
analítica. Si cada servicio levantara su propio broker, no compartirían topics. Si el broker
viviera dentro del compose de ms-05, al caer ms-05 se llevaría la mensajería de todos.

Neo4j, en cambio, sí es propiedad de ms-05 y vive en su propio compose. La regla es:

- Recurso propio del servicio (Postgres, MongoDB, Neo4j): compose del servicio.
- Recurso compartido (Kafka, Redis): `infrastructure/`.
- Contratos de eventos: `contracts/`.

## Arranque

```bash
cd infrastructure
cp .env.example .env
docker compose up -d
```

Servicios y puertos:

| Servicio | URL / puerto |
|---|---|
| Kafka (host) | `localhost:29092` |
| Kafka (contenedores) | `kafka:9092` |
| Kafka UI | `http://localhost:8090` |
| Redis | `localhost:6379` |

La red se llama `smxplore-net` y se declara con nombre explícito para que los compose de los
servicios puedan unirse como red externa.

## Apagado

```bash
docker compose down
```

Para borrar también los datos:

```bash
docker compose down -v
```

## Kafka en desarrollo

- Un solo broker en modo KRaft (sin ZooKeeper).
- `KAFKA_AUTO_CREATE_TOPICS_ENABLE=true` solo para desarrollo.
- Dos listeners: `INTERNAL://kafka:9092` para contenedores y `EXTERNAL://localhost:29092` para
  herramientas del host.
- `kafka-ui` permite ver topics, consumer groups, lag y DLQ.

Topics de fase 2 (ms-05 ya implementa los consumidores; ms-03/ms-04 aún deben publicarlos):

| Topic | Productor | Consumidor ms-05 |
|---|---|---|
| `catalog.site.changed` | ms-04 | sí |
| `catalog.service.changed` | ms-04 | sí |
| `catalog.category.changed` | ms-04 | sí |
| `activities.event.changed` | ms-03 | sí |
| `activities.activity.changed` | ms-03 | sí |
| `reviews.review.changed` | ms-03 / ms-04 | sí |
| `bookings.reservation.changed` | ms-03 | **no** (ms-05 no usa reservas) |

ms-05 crea `<topic>.dlq` para los mensajes que no puede procesar. En desarrollo los topics se
autocreatan; en producción deben crearse de forma controlada. La decisión de excluir `RESERVO`
está en `microservices/ms-05-recommendations/docs/adr/0002-no-reservation-signal.md`.

## Producción

Este compose no se despliega tal cual. En producción:

- Kafka administrado o clúster de 3 brokers, `replication.factor=3`, `min.insync.replicas=2`.
- TLS y SASL/SCRAM o mTLS; secretos en un gestor, no en `.env`.
- `KAFKA_AUTO_CREATE_TOPICS_ENABLE=false`; los topics se crean con un proceso controlado.
- Redis con autenticación/TLS si se comparte.
- Backups y monitoreo a cargo del equipo de plataforma.

## Pruebas

Los tests de integración **no** usan este compose. Cada suite levanta sus propios contenedores
con Testcontainers para que CI sea reproducible y no dependa de un `docker compose up` previo.
