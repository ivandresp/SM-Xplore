# Runbook — ms-05 recomendaciones

## Arranque local completo

1. Infraestructura compartida (Kafka/Redis; no se usa en fase 0-1, pero deja la red creada):

   ```bash
   cd infrastructure
   cp .env.example .env
   docker compose up -d
   ```

2. Neo4j + API:

   ```bash
   cd microservices/ms-05-recommendations
   cp .env.example .env
   # Edita .env: JWT_JWK_SET_URI o JWT_PUBLIC_KEY_LOCATION, JWT_ISSUER y NEO4J_PASSWORD
   docker compose up --build
   ```

3. Verificar:

   ```bash
   curl -fsS http://localhost:8085/actuator/health
   open http://localhost:8085/swagger-ui.html
   ```

## Arranque sin Docker

```bash
export JAVA_HOME=/usr/lib/jvm/java-25-openjdk-amd64
export NEO4J_URI=bolt://localhost:7687
export NEO4J_USER=neo4j
export NEO4J_PASSWORD=change-me
export JWT_PUBLIC_KEY_LOCATION=file:/ruta/ms-01-public.pem
export JWT_ISSUER=https://ms-01.local
export SEED_ENABLED=true
export SPRING_PROFILES_ACTIVE=local
./gradlew bootRun
```

`JWT_JWK_SET_URI` tiene prioridad sobre `JWT_PUBLIC_KEY_LOCATION`. Si no configuras ninguna de
las dos, la aplicación falla al arrancar: no se acepta HS256 en ms-05.

## Si Neo4j está caído

- `/actuator/health` baja a `DOWN`.
- La API responde `503` con `ProblemDetail` (`type: about:blank`, título
  “Recommendations temporarily unavailable”).
- No se recomienda reintentar en bucle desde el front; mostrar caché o un mensaje.
- Al volver Neo4j, el driver reconecta. Verificar constraints e índices con
  `docs/neo4j-logical-model.md`.

## Si Kafka está caído (fase 2)

- La fase 0-1 no usa Kafka; este punto aplica cuando se implementen consumidores.
- La API debe seguir funcionando con el grafo existente.
- El consumidor reintenta con backoff; no bloquear el event loop.
- Revisar lag en `kafka-ui` (`http://localhost:8090`).
- Si el lag supera el umbral, alertar y evaluar reindex.

## Si la DLQ crece (fase 2)

1. Inspeccionar el mensaje en `kafka-ui`.
2. Verificar si es un error de contrato, un dato inválido o una caída de Neo4j.
3. Corregir el consumidor o el dato.
4. Reprocesar desde la DLQ al topic original con una herramienta controlada.
5. Nunca borrar la DLQ sin registrar la causa.

## Reindex (fase 3)

El endpoint `POST /internal/admin/reindex` se añadirá en fase 3. Reconstruirá la proyección
desde ms-03/ms-04 con paginación por cursor y `updatedSince`. No se implementa en fase 0-1 para
no simular una operación que aún no tiene fuentes.

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
