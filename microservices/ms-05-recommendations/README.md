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
| GET | `/api/v1/recommendations?siteId=&lat=&lng=&limit=20` | `TOURIST` | Recomendaciones del turista autenticado |
| GET | `/api/v1/recommendations?touristId=&...` | `ADMIN` | Consulta para soporte |
| POST | `/api/v1/preferences` | `TOURIST` | Gusto explícito por categoría |
| DELETE | `/api/v1/preferences/{categoryId}` | `TOURIST` | Quitar gusto |
| POST | `/api/v1/interactions/view/{itemType}/{itemId}` | `TOURIST` | Registrar vista |
| GET | `/actuator/health` | público | Liveness/readiness |
| GET | `/swagger-ui.html` | público | Documentación OpenAPI |

El `touristId` sale de `sub` o, si no viene, del claim configurado `userId`. Un `TOURIST` no
puede consultar a otro turista; solo `ADMIN` puede usar el query param `touristId`.

El idioma preferido del turista se envía en `Accept-Language` (`es`, `en-US`, etc.). Se usa al
crear o refrescar su perfil en Neo4j y no se lee de ningún claim del JWT.

### Eventos Kafka

Topics consumidos:

| Topic | Productor esperado | Efecto en ms-05 |
|---|---|---|
| `catalog.site.changed` | ms-04 | Upsert de `Sitio` y enlaces con categoría/ítems hijos |
| `catalog.service.changed` | ms-04 | Upsert de `Servicio` |
| `catalog.category.changed` | ms-04 | Upsert de `Categoria` y jerarquía |
| `activities.event.changed` | ms-03 | Upsert de `Evento` |
| `activities.activity.changed` | ms-03 | Upsert de `Actividad` |
| `reviews.review.changed` | ms-03 | Arista `RESEÑO` y agregados de calidad |

`bookings.reservation.changed` **no se consume**. Grupo de consumo:
`ms-05-recommendations`. La key debe ser el `aggregateId`.

Cada mensaje se procesa en una transacción de Neo4j que, en la misma operación:

1. comprueba `ProcessedEvent.eventId` para deduplicar;
2. compara `updatedAt` contra el nodo o la arista existente y descarta eventos viejos;
3. aplica el cambio;
4. registra el `eventId` con el resultado (`APPLIED`, `DUPLICATE` o `STALE`).

Los fallos de contrato van directo a `<topic>.dlq`. Los fallos transitorios se reintentan con
backoff exponencial y después van a la DLQ. El offset se confirma solo después de aplicar el
cambio o de publicar en la DLQ.

### Ejemplo de respuesta

```json
{
  "touristId": "66666666-6666-6666-6666-666666666601",
  "generatedAt": "2026-10-26T12:00:00Z",
  "source": "MIXED",
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

- Solo RS256. No se acepta HS256.
- Producción / ms-01: `JWK_SET_URI=https://ms-01/.../.well-known/jwks.json`.
- Local / tests: `JWT_PUBLIC_KEY_LOCATION=file:/ruta/public.pem` o `classpath:...`.
- `JWT_ISSUER` es obligatorio si se quiere validar `iss`.
- El rol se lee del claim configurado (`role` por defecto) y se prefija con `ROLE_`.
- El UUID del turista se lee de `sub`; si no existe, se usa el claim configurado
  `JWT_USER_ID_CLAIM` (`userId` por defecto). Los claims esperados son `userId`/`sub`, `role`
  y `name`.
- El idioma **no** es un claim del token; se toma de `Accept-Language`.
- Errores en RFC 9457 (`ProblemDetail`), incluidos 401/403.

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
| `NEO4J_PASSWORD` | `change-me` | Contraseña |
| `NEO4J_INITIALIZE_SCHEMA` | `true` | Crea constraints/índices al arrancar |
| `SEED_ENABLED` | `false` | Carga datos de demo; usar solo en local/test |
| `JWK_SET_URI` | vacío | JWKS de ms-01 |
| `JWT_PUBLIC_KEY_LOCATION` | vacío | Llave pública PEM para local/test |
| `JWT_ISSUER` | vacío | Issuer esperado |
| `JWT_AUDIENCE` | vacío | Audiencia esperada, opcional |
| `JWT_USER_ID_CLAIM` | `userId` | Claim de identidad si no viene `sub` |
| `JWT_ROLE_CLAIM` | `role` | Claim de rol |
| `JWT_NAME_CLAIM` | `name` | Claim de nombre |
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
| `CORS_ALLOWED_ORIGINS` | `http://localhost:3000,http://localhost:4200` | Orígenes |
| `RECOMMENDATIONS_*` | ver `application.yaml` | Pesos, límites y diversidad |

## Ejecución con Docker

La red `smxplore-net` debe existir. Levántala una vez desde la raíz:

```bash
cd infrastructure
cp .env.example .env
docker compose up -d

cd ../microservices/ms-05-recommendations
cp .env.example .env
# Configura NEO4J_PASSWORD, JWK_SET_URI o JWT_PUBLIC_KEY_LOCATION, JWT_ISSUER
docker compose up --build
```

Servicios:

- API: `http://localhost:8085`
- Swagger: `http://localhost:8085/swagger-ui.html`
- Health: `http://localhost:8085/actuator/health`
- Neo4j HTTP: `http://localhost:7474`
- Neo4j Bolt: `localhost:7687`

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
export JWT_ISSUER=https://ms-01.local
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
(`apache/kafka:4.1.0`) y Docker. La suite cubre:

- scoring bayesiano, proximidad, novedad y normalización;
- topes de diversidad por sitio/categoría;
- deduplicación afinidad/fallback;
- preferencias y vistas;
- seguridad RS256, roles, consulta admin y errores;
- consultas Neo4j y ausencia total de aristas `RESERVO`;
- contexto completo con seed;
- validación de todos los ejemplos contra los JSON Schema de `contracts/`;
- consumidores Kafka, idempotencia, eventos viejos, DLQ y agregados de reseñas;
- idioma desde `Accept-Language` y JWT sin claim `locale`.

## Documentación

- `docs/ESTADO-Y-PENDIENTES.md`: estado actual, decisiones y pendientes.
- `docs/fase-2-consumidores.md`: alcance de la fase 2, contratos asumidos y pendientes de ms-03/ms-04.
- `docs/neo4j-logical-model.md`: modelo lógico actualizado, constraints, queries y fairness.
- `docs/adr/0001-event-driven-broker.md`: Kafka + outbox para fase 2.
- `docs/adr/0002-no-reservation-signal.md`: decisión de excluir `RESERVO`.
- `docs/runbook.md`: operación y diagnóstico.
- `../../contracts/README.md`: contratos de eventos.
- `../../infrastructure/README.md`: Kafka/Redis compartidos.

## Fuera de alcance en esta fase

- Outbox y publicación real de eventos en ms-03/ms-04. ms-05 ya consume; los productores se
  implementan en sus ramas.
- Reindex desde ms-03/ms-04 (fase 3).
- Caché Redis/Caffeine (fase 3).
- Circuit breaker y pruebas de carga (fases 3-4).
- Recomendaciones para guías (US10/US17).
