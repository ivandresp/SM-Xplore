# ms-05 — Estado actual y pendientes

Fecha: 2026-10-26  
Rama de trabajo: `ms-recommendations`  
Servicio: `microservices/ms-05-recommendations/`

Este documento resume el estado de las fases 0-1 y 2, las decisiones tomadas y lo que falta
para cerrar la integración con ms-03/ms-04.

---

## 1. Estado actual

### Fase 0-1 (cerrada)

- API reactiva con WebFlux.
- Seguridad RS256/JWKS o llave pública PEM.
- Neo4j reactivo con constraints, índices y seed.
- Ranking por afinidad, calidad de reseñas, proximidad y novedad.
- Diversidad por sitio y categoría.
- Arquitectura hexagonal.
- Lombok y MapStruct.
- Tests con Testcontainers.
- Dockerfile y compose de ms-05.
- `infrastructure/` con Kafka, Redis y kafka-ui.
- `contracts/` con esquemas de eventos versionados.

### Fase 2 (lado consumidor, implementada)

- Consumidores Kafka para los seis topics de ms-05.
- `bookings.reservation.changed` **no** se consume.
- Idempotencia por `eventId` con nodos `ProcessedEvent`.
- Descarte de eventos viejos por `updatedAt`.
- Proyección de categorías, sitios, servicios, actividades, eventos y reseñas.
- Agregados de reseñas: `ratingAvg`, `ratingCount`, `lastReviewAt`.
- Reintentos con backoff exponencial y DLQ `<topic>.dlq`.
- Métricas de procesados, fallos, DLQ y métricas de cliente Kafka (incluida lag).
- Tests de contrato contra `contracts/events/`.
- Tests de integración con Kafka y Neo4j en Testcontainers.
- Fixtures para simular a ms-03/ms-04 mientras sus outbox no existen.

Verificación:

```text
./gradlew clean test  -> 88 tests, 0 fallos
./gradlew bootJar     -> BUILD SUCCESSFUL
```

Decisiones aplicadas:

- **No se usa `RESERVO`**: ni ranking, ni popularidad, ni exclusión.
- La calidad se calcula con promedio bayesiano de reseñas.
- Las vistas crean una preferencia inferida de peso bajo; una preferencia explícita tiene
  prioridad.
- JWT con RS256/JWKS de ms-01, o llave pública PEM en local/tests.
- El idioma **no** viaja en el JWT: se toma de `Accept-Language`.
- El outbox y la publicación real de eventos se implementan en ms-03/ms-04, no en ms-05.

No hay commit hecho. Los cambios están en el working tree.

---

## 2. JWKS y contrato JWT

### Qué es JWKS

Un **JWKS** (*JSON Web Key Set*, RFC 7517) es un documento JSON con las llaves públicas de un
emisor de JWT. Se publica en una URL estable, típicamente
`https://ms-01/.well-known/jwks.json`.

Con RS256:

1. ms-01 firma el JWT con su llave privada.
2. ms-01 publica las llaves públicas en el JWKS, cada una con un `kid`.
3. ms-05 descarga el JWKS, lee el `kid` del header del JWT y verifica la firma con la llave
   pública correspondiente.
4. ms-05 cachea las llaves; ms-01 puede rotarlas sin reiniciar a los consumidores.

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

ms-05 se configura con `JWK_SET_URI`. En local/tests se puede usar
`JWT_PUBLIC_KEY_LOCATION` con un PEM. Si no hay ninguna de las dos, el servicio falla al
arrancar: no se acepta un arranque sin validación de tokens ni HS256.

### Claims asumidos

| Claim | Uso |
|---|---|
| `sub` | UUID del turista (`touristId`); estándar |
| `userId` | Alternativa si no viene `sub`; configurable |
| `role` | `TOURIST` o `ADMIN` |
| `name` | Nombre para mostrar |

Si vienen ambos, `sub` tiene prioridad. Si no viene ninguno, se rechaza la petición.

No se requiere `locale`. Si en el futuro ms-01 guarda la preferencia de idioma en el perfil,
se podrá añadir, pero no es parte de este contrato.

---

## 3. Decisiones pendientes de confirmar con los otros servicios

| Tema | Decisión asumida en esta fase | Qué falta confirmar |
|---|---|---|
| JWT | `sub` o `userId` = UUID del turista, `role`, `name`; sin `locale` | Confirmar con ms-01 el JWKS, issuer, audience y el nombre final del claim de identidad |
| Idioma | `Accept-Language` por petición, fallback `es` | Confirmar si el frontend ya envía el header o si hace falta un selector explícito |
| Catálogo de categorías | ms-04 será el dueño y usará UUID | Confirmar que ms-03 migrará `category` a `categoryId` UUID |
| Kafka producción | Broker gestionado, TLS/SASL, topics controlados | Definir quién administra el broker y los secretos |
| Neo4j producción | Community en dev; Enterprise por decidir | Confirmar edición, backups y réplicas |
| Alcance | Fase 2 solo del lado consumidor de ms-05 | Confirmar que el outbox se implementará en las ramas de ms-03/ms-04 |

---

## 4. Pendiente en ms-03

- Cambiar `category` por `categoryId` UUID en schema y DTOs.
- Exponer `updatedAt`.
- Crear tabla `outbox_event` y relay.
- Publicar, con `aggregateId` como key y `eventVersion=1`:
  - `activities.event.changed`
  - `activities.activity.changed`
  - `reviews.review.changed`
  - `bookings.reservation.changed` (aunque ms-05 no lo consume)
- Endpoints paginados por cursor con `updatedSince` para el reindex.

## 5. Pendiente en ms-04

- Implementar catálogo de categorías con IDs UUID.
- CRUD de sitios y servicios con `categoryId`, `status`, `updatedAt` y coordenadas.
- Outbox y publicación de:
  - `catalog.site.changed`
  - `catalog.service.changed`
  - `catalog.category.changed`
- Endpoints de lectura para re-sync.

## 6. Pendiente en ms-05

- Retención/prune de nodos `ProcessedEvent` (por ejemplo, 30 días). Hoy crecen sin límite.
- Reindex desde ms-03/ms-04 (`POST /internal/admin/reindex`, fase 3).
- Caché Redis + Caffeine, circuit breaker y degradación (fase 3).
- Métricas de recomendación y diversidad (fase 3).
- Pruebas de carga, runbook final y alertas (fase 4).
- Decidir si se usa `RecommendationSource.QUALITY` para un fallback solo de calidad.

### Nota sobre el seed

El seed de demo contiene agregados de reseñas sintéticos (`ratingAvg`, `ratingCount`) que no
corresponden a todas las aristas `RESEÑO` sembradas. Cuando llega un evento de reseña, ms-05
recalcula el agregado a partir de las aristas proyectadas. En un entorno real, el reindex de la
fase 3 cargará el historial completo; mientras tanto, un ítem sembrado puede cambiar su
promedio al recibir su primera reseña real.

## 7. Pendiente de infraestructura

- Crear topics de forma controlada en dev y producción (`auto.create.topics.enable=false`).
- CI que valide contratos y levante Kafka/Neo4j.
- Configuración de producción: TLS, SASL, secretos y réplicas.
- Alertas de DLQ y lag.

## 8. Orden recomendado

1. Confirmar JWKS, claims y catálogo de categorías con ms-01/ms-03/ms-04.
2. Implementar outbox y eventos en ms-03/ms-04.
3. Probar el flujo real con los productores y validar lag/DLQ.
4. Fase 3: reindex, caché, circuit breaker y métricas de recomendación.
5. Fase 4: carga, alertas y runbook definitivo.

---

## 9. Documentos relacionados

- `README.md`
- `docs/fase-2-consumidores.md`
- `docs/neo4j-logical-model.md`
- `docs/adr/0001-event-driven-broker.md`
- `docs/adr/0002-no-reservation-signal.md`
- `docs/runbook.md`
- `../../contracts/README.md`
- `../../infrastructure/README.md`
