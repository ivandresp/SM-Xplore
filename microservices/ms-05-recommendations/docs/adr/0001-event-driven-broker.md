# ADR 0001 — Broker de eventos y transactional outbox

- Estado: aceptado para fase 2
- Fecha: 2026-10-26
- Contexto: ms-05 necesita una proyección actualizada de ms-03 y ms-04.

## Decisión

Usar **Kafka** (modo KRaft en desarrollo) como broker de eventos de dominio, con patrón
**transactional outbox** en cada servicio productor.

- Kafka se levanta una sola vez en `infrastructure/compose.yaml` y se comparte por la red
  `smxplore-net`.
- Cada productor escribe la entidad y la fila de `outbox_event` en la misma transacción de su
  base.
- Un relay publica las filas pendientes y marca `published_at`.
- La semántica es **at-least-once**; los consumidores son idempotentes.
- La key de partición es `aggregateId` para conservar el orden por entidad.

## Alternativas

| Alternativa | Por qué no |
|---|---|
| RabbitMQ | No tiene replay por retención; varios consumidores requieren exchange/colas; el orden por entidad es más difícil. |
| Kafka sin outbox | Dual write: se puede perder el evento o publicar uno que no existe. Inaceptable. |
| Debezium CDC | Duplica infraestructura (Kafka Connect) para esta entrega. Queda como evolución. |
| Llamadas HTTP síncronas | Acoplan disponibilidad y latencia; no hay replay ni desacoplamiento. |

## Consecuencias

- Se necesita operar un broker y un relay por productor.
- La consistencia es eventual (1-5 s esperados).
- Los contratos viven en `contracts/events/` y se validan en tests.
- La fase 0-1 de ms-05 no implementa Kafka: usa seed y consultas directas a Neo4j.
- La fase 2 ya implementa los consumidores en ms-05; el outbox y la publicación siguen
  pendientes en ms-03/ms-04.

## Producción

- 3 brokers, `replication.factor=3`, `min.insync.replicas=2`.
- TLS + SASL/SCRAM o mTLS.
- Topics creados de forma controlada; `auto.create.topics.enable=false`.
- Monitoreo de lag por consumer group y alerta de DLQ.
