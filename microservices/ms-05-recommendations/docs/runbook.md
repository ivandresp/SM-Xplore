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
export JWT_ISSUER=sm-xplore-ms-01
export JWT_AUDIENCE=sm-xplore-services
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
- Si el lag supera el umbral, alertar y evaluar un reindex del catálogo.

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
- `recommendations_served_total{source}`: respuestas por origen (`MIXED`, `AFFINITY`, `NEARBY`,
  `FALLBACK`). Un `FALLBACK` alto indica poca afinidad registrada o proyección incompleta.
- `recommendations_items`: elementos devueltos por respuesta.
- `recommendations_cache_total{result}`: `hit` o `miss`; la proporción de `hit` es la salud de la
  cache. Si cae a cero con Redis arriba, revisar `ms05:graph:version` y el TTL local de 10 s.
- `recommendations_diversity_categories` y `recommendations_diversity_sites`: categorías y sitios
  distintos por respuesta.

```promql
sum(rate(recommendations_cache_total{result="hit"}[5m]))
  / sum(rate(recommendations_cache_total[5m]))
sum by (source) (rate(recommendations_served_total[5m]))
```
## Reindex del catálogo

`POST /internal/admin/reindex?entity=SITES|SERVICES|CATEGORIES` recorre el feed
`GET /api/v1/admin/catalog/export` de ms-04 y aplica el mismo camino de proyección que Kafka.

```bash
TOKEN=<jwt de un ADMIN>
curl -fsS -X POST "http://localhost:8085/internal/admin/reindex?entity=SITES" \
  -H "Authorization: Bearer $TOKEN"
```

Respuesta: `{"entity":"SITES","pages":1,"events":12,"applied":12,"duplicate":0,"stale":0}`.

- `CATALOG_EXPORT_BASE_URL` debe apuntar a ms-04 (`http://ms-04:8080` dentro de Compose,
  `http://localhost:8084` en local).
- El token se reenvía tal cual: ms-05 no tiene credenciales propias y ms-04 exige `ADMIN`.
- Repetir el reindexado es seguro: el `eventId` se deriva de `eventType + aggregateId +
  updatedAt`, así que lo ya proyectado sale como `duplicate`.
- Si el recorrido se corta, reanudar con `?updatedSince=<última fecha vista>`.
- `REINDEX_MAX_PAGES` (500 por defecto) evita bucles si el feed no termina; si se alcanza, el
  endpoint devuelve `500` con “catalog export did not finish after N pages”.
- ms-03 todavía no expone un feed equivalente: actividades, eventos y reseñas solo se recuperan
  con replay de Kafka.

## Retención de `ProcessedEvent`

`ProcessedEventRetentionJob` borra los marcadores más viejos que `EVENTS_RETENTION_DAYS` (30 por
defecto) en lotes de `EVENTS_RETENTION_BATCH_SIZE`, hasta `EVENTS_RETENTION_MAX_BATCHES` por
corrida y cada `EVENTS_RETENTION_INTERVAL`. La ventana debe ser mayor que la retención de Kafka
para cubrir replays.

```cypher
MATCH (p:ProcessedEvent)
RETURN count(p) AS markers, min(p.processedAt) AS oldest;
```

Para forzar una corrida se puede reiniciar el servicio; el job también se ejecuta al arrancar
tras `EVENTS_RETENTION_INITIAL_DELAY`. Borrar marcadores no borra la proyección: solo permite
que un evento repetido se vuelva a aplicar.

## Pruebas de carga

`loadtest/recommendations-flow.js` (k6) mide el camino completo: versión del grafo en Redis, cache
Caffeine + Redis y ranking en Neo4j.

```bash
k6 run loadtest/recommendations-flow.js \
  -e BASE_URL=http://localhost:8085 -e TOKEN="$TOKEN" -e LAT=10.39 -e LNG=-75.47
```

Umbrales: p95 < 600 ms, errores < 1 % y `checks` > 98 %. Durante la corrida vigilar:

- `recommendations_cache_total{result=hit}` debe subir durante la meseta; si se queda en `miss`,
  revisar el TTL local (10 s) y que `ms05:graph:version` no esté cambiando por una ingesta activa.
- `neo4j_driver_*` y la latencia de Neo4j; una p95 alta con cache caliente apunta a la base.
- `recommendations_diversity_sites` no debe caer a 1 con el catálogo lleno.
## Cache de recomendaciones

La respuesta de `GET /api/v1/recommendations` se guarda en dos capas: Caffeine en memoria
(`RECOMMENDATIONS_CACHE_LOCAL_TTL`, 10 s) y Redis compartido (`RECOMMENDATIONS_CACHE_REDIS_TTL`,
1 min). La llave incluye la versión del grafo (`ms05:graph:version`), que se incrementa cada vez
que se aplica una proyección, así que una ingesta nueva invalida las respuestas cacheadas sin
recorrer la cache.

- Redis es best-effort: si no responde, la API calcula la recomendación contra Neo4j y no cachea.
  Por eso `management.health.redis.enabled` está en `false` y una caída de Redis no marca el
  servicio como `DOWN`.
- `RECOMMENDATIONS_CACHE_ENABLED=false` desactiva ambas capas.
- Para invalidar todo a mano: `redis-cli SET ms05:graph:version 0` (o `INCR`) y esperar el TTL
  local.
- La cache no sustituye al ranking: solo evita recalcular la misma consulta del mismo turista.
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
