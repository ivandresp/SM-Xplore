# Runbook — ms-05 recomendaciones

## Arranque local completo

1. Infraestructura compartida (Kafka, Redis y kafka-ui):

   ```bash
   cd infrastructure
   cp .env.example .env
   docker compose up -d
   ```

2. Neo4j + API:

   ```bash
   cd microservices/ms-05-recommendations
   cp .env.example .env
   # Edita .env: JWK_SET_URI o JWT_PUBLIC_KEY_LOCATION, JWT_ISSUER y NEO4J_PASSWORD
   # Para consumir eventos: KAFKA_BOOTSTRAP_SERVERS=localhost:29092 y EVENTS_CONSUMER_ENABLED=true
   docker compose up --build
   ```

3. Verificar:

   ```bash
   curl -fsS http://localhost:8085/actuator/health
   curl -fsS http://localhost:8085/actuator/prometheus | grep recommendations_events
   open http://localhost:8085/swagger-ui.html
   ```

4. Ver topics, consumer group y lag en `http://localhost:8090`.

## Arranque sin Docker

```bash
export JAVA_HOME=/usr/lib/jvm/java-25-openjdk-amd64
export NEO4J_URI=bolt://localhost:7687
export NEO4J_USER=neo4j
export NEO4J_PASSWORD=change-me
export JWT_PUBLIC_KEY_LOCATION=file:/ruta/ms-01-public.pem
export JWT_ISSUER=https://ms-01.local
export KAFKA_BOOTSTRAP_SERVERS=localhost:29092
export EVENTS_CONSUMER_ENABLED=true
export SEED_ENABLED=true
export SPRING_PROFILES_ACTIVE=local
./gradlew bootRun
```

`JWK_SET_URI` tiene prioridad sobre `JWT_PUBLIC_KEY_LOCATION`. Si no configuras ninguna de
las dos, la aplicación falla al arrancar: no se acepta HS256 en ms-05.

## Kafka y consumidores

- Grupo de consumo: `ms-05-recommendations`.
- Topics: `catalog.site.changed`, `catalog.service.changed`, `catalog.category.changed`,
  `activities.event.changed`, `activities.activity.changed`, `reviews.review.changed`.
- `bookings.reservation.changed` no se consume.
- `enable.auto.commit=false`; el offset se confirma después de aplicar la proyección o de
  publicar en la DLQ.
- Reintentos: `EVENTS_CONSUMER_MAX_RETRIES` con backoff exponencial.
- DLQ: `<topic>.dlq`.
- La API sigue funcionando si Kafka está caído; el consumidor reintenta en segundo plano.

Para desactivar los consumidores sin tocar el broker:

```text
EVENTS_CONSUMER_ENABLED=false
```

## Si Neo4j está caído

- `/actuator/health` baja a `DOWN`.
- La API responde `503` con `ProblemDetail` (`type: about:blank`, título
  “Recommendations temporarily unavailable”).
- El consumidor reintenta la proyección; si se agotan los reintentos, el evento va a la DLQ.
- Al volver Neo4j, el driver reconecta. Verificar constraints e índices con
  `docs/neo4j-logical-model.md`.
- Si la DLQ creció por la caída, corregir y reprocesar los mensajes.

## Si Kafka está caído

- La API sigue respondiendo con el grafo existente.
- El listener de Spring Kafka reintenta conectarse; no bloquea el event loop de WebFlux.
- Revisar lag en `kafka-ui` (`http://localhost:8090`).
- Si el lag supera el umbral, alertar y evaluar un reindex cuando esté disponible.

## Si la DLQ crece

1. Inspeccionar el mensaje en `kafka-ui` y sus headers `x-error-type`, `x-error-message`,
   `x-original-topic`, `x-original-partition` y `x-original-offset`.
2. Verificar si es un error de contrato, un dato inválido o una caída de Neo4j.
3. Corregir el productor, el dato o el consumidor.
4. Reprocesar desde la DLQ al topic original con una herramienta controlada.
5. Nunca borrar la DLQ sin registrar la causa.

Los errores de contrato (`InvalidEventException`) no se reintentan; van directo a la DLQ.

## Métricas

En `/actuator/prometheus`:

- `recommendations_events_processed_total{event_type,result}` con `result` =
  `applied|duplicate|stale`.
- `recommendations_events_failures_total{topic}`.
- `recommendations_events_dlq_total{topic}`.
- `kafka_consumer_*`, incluida la lag por partición.

## Reindex (fase 3)

El endpoint `POST /internal/admin/reindex` se añadirá en fase 3. Reconstruirá la proyección
desde ms-03/ms-04 con paginación por cursor y `updatedSince`. Mientras no exista, la
recuperación se hace con replay de Kafka o borrando y reconstruyendo el grafo con las fuentes.

## Retención de `ProcessedEvent`

Hoy los nodos `ProcessedEvent` se conservan para deduplicar. En producción conviene una tarea
periódica que borre los nodos con `processedAt` mayor a 30 días. La ventana debe ser mayor que
la retención de Kafka para cubrir replays.

## Reset del seed local

```bash
docker compose down -v
docker compose up --build
```

El seed es idempotente (`MERGE`); no crea `RESERVO`.

## Verificar que no hay reservas en el grafo

```cypher
MATCH ()-[r:RESERVO]->()
RETURN count(r) AS reservationEdges;
```

Debe devolver `0`. También hay un test de integración que lo comprueba.
