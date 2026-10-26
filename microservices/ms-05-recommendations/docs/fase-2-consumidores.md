# Fase 2 — consumidores de eventos de ms-05

Fecha: 2026-10-26  
Alcance: **lado consumidor de ms-05**. Los productores reales (outbox de ms-03 y ms-04)
todavía no existen en sus ramas; se simulan con fixtures de `contracts/` y `KafkaTemplate` en
los tests.

## 1. Decisiones de alcance

- ms-05 consume seis topics: `catalog.site.changed`, `catalog.service.changed`,
  `catalog.category.changed`, `activities.event.changed`, `activities.activity.changed` y
  `reviews.review.changed`.
- **No** consume `bookings.reservation.changed`. La decisión de no usar reservas sigue vigente
  (`docs/adr/0002-no-reservation-signal.md`).
- Los eventos se validan contra los JSON Schema de `contracts/events/` en tests, no en runtime.
  En runtime se valida lo mínimo para construir el evento de dominio; un mensaje inválido va a
  la DLQ.
- La semántica es **at-least-once**. El consumidor es idempotente.
- El grafo sigue siendo una proyección: no se escribe en las bases de ms-03/ms-04.

## 2. JWT: qué es JWKS y qué claims usa ms-05

Un **JWKS** (*JSON Web Key Set*) es un documento JSON estandarizado (RFC 7517) que publica las
llaves públicas de un emisor de JWT. Con RS256:

1. ms-01 firma el token con su llave privada.
2. ms-01 expone las llaves públicas en un endpoint, normalmente
   `https://ms-01/.well-known/jwks.json`.
3. ms-05 descarga ese JWKS, lee el `kid` del header del JWT, selecciona la llave pública
   correspondiente y verifica la firma.
4. ms-05 cachea las llaves. ms-01 puede rotarlas sin reiniciar a los consumidores.

Ejemplo simplificado:

```json
{
  "keys": [
    {
      "kty": "RSA",
      "kid": "ms-01-2026-10",
      "use": "sig",
      "alg": "RS256",
      "n": "...",
      "e": "AQAB"
    }
  ]
}
```

Configuración de ms-05:

```text
JWK_SET_URI=https://ms-01/.well-known/jwks.json
JWT_ISSUER=https://ms-01
JWT_AUDIENCE=smxplore            # opcional
```

En local/tests se puede usar una llave pública PEM con `JWT_PUBLIC_KEY_LOCATION`; es un atajo
para no levantar ms-01.

Contrato de claims asumido:

| Claim | Uso |
|---|---|
| `sub` | UUID del turista (`touristId`); es el estándar |
| `userId` | Alternativa si ms-01 no emite `sub`; configurable con `JWT_USER_ID_CLAIM` |
| `role` | `TOURIST` o `ADMIN` |
| `name` | Nombre para mostrar |
| `locale` | **No se usa**. El idioma lo elige el usuario. |

Si el token trae `sub` y `userId`, gana `sub`. Si no trae ninguno, la petición se rechaza.

El idioma se envía en `Accept-Language` en las peticiones que crean o refrescan el perfil
(`POST /preferences`, `POST /interactions/view/...`). Se normaliza a minúsculas (por ejemplo
`en-US` → `en-us`) y, si no viene o no es válido, se usa `es`. En el futuro ms-01 podría
guardar la preferencia de idioma en el perfil, pero no es necesaria para esta fase.

## 3. Topics y proyección

| Topic | Productor esperado | Nodo/arista |
|---|---|---|
| `catalog.category.changed` | ms-04 | `Categoria`, `PERTENECE_A`, `ES_DE_TIPO` |
| `catalog.site.changed` | ms-04 | `Sitio`/`CatalogItem`, `ES_DE_TIPO`, enlaces de hijos |
| `catalog.service.changed` | ms-04 | `Servicio`, `ES_DE_TIPO`, `UBICADO_EN` |
| `activities.event.changed` | ms-03 | `Evento`, `ES_DE_TIPO`, `OCURRE_EN` |
| `activities.activity.changed` | ms-03 | `Actividad`, `ES_DE_TIPO`, `OCURRE_EN` |
| `reviews.review.changed` | ms-03 | arista `RESEÑO` y agregados del ítem |

Las entidades que pueden llegar antes que sus referencias se reconcilian después:

- un servicio puede llegar antes que su sitio o categoría; cuando llegan, se crean
  `UBICADO_EN` y `ES_DE_TIPO`;
- una reseña puede llegar antes que el ítem; se crea un `CatalogItem` mínimo y el evento de
  catálogo posterior completa sus propiedades sin pisar la reseña;
- una categoría puede llegar después de los ítems; el evento de categoría enlaza los ítems que
  ya tengan su `categoryId`.

## 4. Idempotencia y orden

Cada aplicación se ejecuta en una transacción de Neo4j:

1. `MATCH (p:ProcessedEvent {eventId})`. Si existe → `DUPLICATE`, no se aplica nada.
2. Se compara `updatedAt` del evento contra el `updatedAt` del nodo/arista. Si el evento es
   más viejo o igual → `STALE`, se registra el `eventId` y no se aplica.
3. Se aplica el `MERGE`/`SET` correspondiente.
4. Se registra `ProcessedEvent` con `eventId`, `eventType`, `aggregateId`, `occurredAt`,
   `processedAt` y `outcome`.

El topic no garantiza orden entre agregados distintos; sí lo garantiza por `aggregateId` si el
productor usa esa key, que es la regla del contrato. Las reconciliaciones descritas arriba
cubren el desorden entre topics.

`ProcessedEvent` crece con el tiempo. En producción conviene una tarea de retención (por
ejemplo, borrar nodos con `processedAt` mayor a 30 días). No se implementa en esta fase.

## 5. Reintentos y DLQ

- `enable.auto.commit=false`; el contenedor confirma el offset después de que el listener
  termina correctamente (`AckMode.RECORD`).
- Fallos transitorios (Neo4j caído, timeout): reintentos con backoff exponencial
  (`EVENTS_CONSUMER_MAX_RETRIES`, `INITIAL_BACKOFF`, `MAX_BACKOFF`).
- Fallos de contrato (`InvalidEventException`): no se reintentan; van directo a la DLQ.
- DLQ: `<topic>.dlq` con headers `x-original-topic`, `x-original-partition`,
  `x-original-offset`, `x-error-type` y `x-error-message`.
- Si la publicación en DLQ falla, el offset no se confirma.

## 6. Métricas

Se exponen en `/actuator/prometheus`:

- `recommendations.events.processed{event_type,result}` con `result` =
  `applied|duplicate|stale`.
- `recommendations.events.failures{topic}`.
- `recommendations.events.dlq{topic}`.
- Métricas de cliente Kafka (`kafka.consumer.*`), incluida la lag, enlazadas por Spring Boot
  al `ConsumerFactory` real.

## 7. Contratos y fixtures

- `contracts/events/*.v1.json` son la fuente de verdad.
- `contracts/events/examples/*.example.json` son fixtures válidos para tests.
- `EventContractTest` valida todos los ejemplos contra su schema con Draft 2020-12 y formatos
  estrictos (`uuid`, `date-time`).
- `ProjectionEventDecoderTest` valida que el decoder acepte todos los ejemplos consumidos y
  rechace eventos de reservas, versiones distintas y topics que no correspondan.

## 8. Lo que falta en ms-03 / ms-04

- Outbox transaccional y relay.
- Cambiar `category` por `categoryId` UUID en ms-03.
- Exponer `updatedAt` en las entidades de ms-03.
- Publicar los eventos con `aggregateId` como key y `eventVersion=1`.
- Endpoints paginados para reindex (fase 3).
- Producir `catalog.category.changed` desde ms-04 cuando el catálogo de categorías sea la
  fuente única.

Mientras eso no exista, los tests de ms-05 publican fixtures directamente en Kafka y validan
el contrato; no se simula el outbox de los otros servicios.
