# Contratos de eventos

Este directorio es la interfaz versionada entre productores y consumidores de eventos. No
pertenece a un microservicio en particular: ms-03, ms-04, ms-05, notificaciones y analítica
deben validar contra los mismos esquemas.

## Estructura

```text
contracts/
├── README.md
└── events/
    ├── envelope.v1.json
    ├── envelope.v2.json
    ├── activities.activity.changed.v1.json
    ├── activities.activity.changed.v2.json
    ├── activities.event.changed.v1.json
    ├── activities.event.changed.v2.json
    ├── bookings.reservation.changed.v1.json
    ├── bookings.reservation.changed.v2.json
    ├── catalog.category.changed.v1.json
    ├── catalog.service.changed.v1.json
    ├── catalog.site.changed.v1.json
    ├── catalog.site.changed.v2.json
    ├── catalog.zone.changed.v1.json
    ├── reviews.review.changed.v1.json
    ├── security.report.changed.v1.json
    └── examples/
```

El archivo se llama igual que el `eventType` y termina en `.v1.json`. Cada schema tiene un
fixture en `examples/` con el mismo nombre y sufijo `.v1.example.json`.

## Sobre del evento

Todos los eventos comparten el sobre de `envelope.v1.json` y, desde la versión 2, el de
`envelope.v2.json`: mismo formato, pero `envelope.v1.json` fija `eventVersion = 1` y por eso un
evento `v2` no puede validarse contra él.

- `eventId`: UUID único. Sirve para deduplicar.
- `eventType`: nombre del evento, igual al topic.
- `eventVersion`: entero. Empieza en 1.
- `occurredAt`: instante UTC en ISO-8601.
- `aggregateId`: UUID de la entidad que cambió. Es la key de partición.
- `correlationId`: identificador de la petición que originó el cambio.
- `producer`: servicio que publica.
- `payload`: datos del cambio.

## Reglas de versionado

- Cambios aditivos y opcionales: misma versión.
- Renombrar, eliminar o cambiar el tipo de un campo: nueva versión `v2`.
- Un evento `v2` se valida contra `envelope.v2.json`, no contra `envelope.v1.json`.
- Nunca reutilizar un nombre de campo con otro significado.
- Los consumidores ignoran campos desconocidos; los campos requeridos no se renombran.
- Un productor valida el evento serializado contra el esquema antes de publicar.
- Un consumidor valida el fixture antes de deserializar en sus tests.
- Un cambio de esquema que rompa un consumidor bloquea el merge.

## Topics y consumidores

| Topic / eventType | Productor | ms-05 |
|---|---|---|
| `catalog.site.changed` | ms-04 (v2) | sí |
| `catalog.zone.changed` | ms-04 (v1) | sí |
| `catalog.service.changed` | ms-04 | sí |
| `catalog.category.changed` | ms-04 | sí |
| `activities.event.changed` | ms-03 (v2) | sí |
| `activities.activity.changed` | ms-03 (v2) | sí |
| `reviews.review.changed` | ms-03 / ms-04 | sí |
| `security.report.changed` | ms-02 | **no** |
| `bookings.reservation.changed` | ms-03 (v2) / ms-04 (v1) | **no** |

`reviews.review.changed` lo publica ms-03 para actividades/eventos y ms-04 para sitios y
servicios. El campo `payload.itemType` indica el origen; ms-05 consume ambos sin distinguir el
productor. El sobre conserva `producer` para trazabilidad. `payload.status` puede ser
`PUBLISHED`, `PENDING_REVIEW`, `HIDDEN` o `DELETED`; `PENDING_REVIEW` es una reseña retenida por
el filtro de IA (ms-03 o ms-04) y no debe contarse como publicada. ms-03 emite el evento al
crear, responder y moderar; ms-04 al crear y moderar. Solo `PUBLISHED` alimenta la reputación.

`catalog.site.changed` v2 agrega `payload.managerId`, obligatorio: ms-03 lo usa para saber qué gestora manda en el sitio y refrescar el snapshot que guarda en sus eventos. La v1 sigue en el repositorio para los consumidores que aún validan contra ella; ms-04 publica v2.

`catalog.zone.changed` (v1) lo publica ms-04 cuando se crea, actualiza o cambia de estado una zona y lo consume ms-05 para construir el vértice `:Zona` y enlazar `(:Sitio)-[:PERTENECE_A]->(:Zona)`. El payload lleva `zoneId`, `managerId` (gestora dueña de la zona), `name`, `status` (`ACTIVE`, `CONGESTED`, `INACTIVE`) y `updatedAt`; `type` y `description` son opcionales.

### Campos opcionales aditivos (sin subir de versión)

Siguiendo la regla de versionado, un campo nuevo y opcional no abre una versión nueva. Los dos
casos vigentes son:

- `catalog.site.changed` **v2**: `payload.zoneId` (UUID, opcional). ms-04 la emite siempre que el
  sitio tenga zona; ms-05 la usa para enlazar el sitio con su zona y lo ignora si falta.
- `reviews.review.changed` **v1**: `payload.response` (objeto opcional con `text` 1..2000 y
  `respondedAt` date-time). Lo incluye ms-04 cuando la reseña ya tiene respuesta; ms-03 no lo
  incluye. Un consumidor que no lo conozca simplemente lo ignora.

`security.report.changed` lo publica ms-02 para alertas, notificaciones y analítica; ms-05 no lo consume.

### `security.report.changed`: producido sin consumidor a propósito (P2-10 / P3-9)

- **Productor**: ms-02 (outbox transaccional, `producer=ms-02`, key = `aggregateId`). **Consumidores: ninguno en
  esta entrega**, y la columna de ms-05 en la tabla de arriba lo dice explícitamente: no es un olvido.
- **Motivo**: el reporte de seguridad ya se opera por la API de ms-02 (crear, listar por estado, cerrar, resolver,
  comentar). No hay un caso de producto aprobado para un consumidor; agregarlo sin ese caso sumaría acoplamiento,
  otro tópico que operar y riesgo de notificaciones duplicadas. Se publica igual para conservar la señal y para que
  el contrato v1 quede cubierto por el test de contrato del productor.
- **Decisión firmada (P3-9, 2026-11-06): opción A**, se mantiene sin consumidor. El evento existe
  como señal para una analítica futura; agregar un consumidor exige un caso de producto aprobado,
  su proyección propia y un contract test de consumidor sobre `security.report.changed.v1.json`.
- **Si P3-9 elige consumidor**: el nuevo consumidor debe validar el fixture
  `security.report.changed.v1.example.json` contra el schema y tratar el evento como at-least-once (deduplicar por
  `eventId`), igual que el resto de consumidores de este directorio.

`bookings.reservation.changed` se mantiene porque notificaciones y analítica lo usan, pero
**ms-05 no lo consume**. La recomendación no usa reservas: ver
`microservices/ms-05-recommendations/docs/adr/0002-no-reservation-signal.md`.
ms-04 publica la **v1** para reservas de servicios (`payload.items[].itemType=SERVICE`) y la
consume con el grupo `ms-04-notifications` para alimentar la bandeja. ms-03 publica la **v2** para
actividades/eventos: agrega `payload.items[].itemName`, `guideId`, `managerId` y el estado
`NO_SHOW`. ms-04 acepta ambas versiones y notifica al turista, al guía de la actividad y a la
gestora del evento. `bookedFor`, `providerId`, `guideId`, `managerId`, `itemName` y `serviceName`
son opcionales y aditivos para ese consumidor.

### Estados de reserva: mapeo entre v1 y v2

Tomado de los enums reales de cada esquema (no hay traducción de nombres: el estado se publica con
el mismo literal en ambas versiones).

| Estado | `bookings.reservation.changed.v1` (ms-04, reservas de servicio) | `bookings.reservation.changed.v2` (ms-03, actividades/eventos) |
|---|---|---|
| `PENDING` | sí | sí |
| `CONFIRMED` | sí | sí |
| `CANCELLED` | sí | sí |
| `COMPLETED` | sí | sí |
| `NO_SHOW` | **no** (no representable en v1) | sí |

Consecuencias prácticas:

- La v1 es un subconjunto estricto de estados de la v2: un `NO_SHOW` de ms-03 nunca viaja en v1, y
  un consumidor que solo entienda v1 no debe esperar ese literal.
- El payload v1 **no** valida contra el esquema v2 (`items[].itemName` es obligatorio en v2 y
  opcional en v1), así que cada versión se valida contra su propio esquema y el consumidor
  (ms-04, grupo `ms-04-notifications`) despacha por `eventVersion`.
- Las reservas de servicio de ms-04 no emiten `NO_SHOW`; ese estado solo existe hoy en el camino de
  actividades/eventos de ms-03.

Estado de fase 2:

- ms-05 ya consume los seis topics marcados con “sí”.
- ms-04 ya publica catálogo (los sitios en v2), reseñas y reservas de servicios desde su outbox. ms-03 publica
  reservas en v2, reseñas y `activities.activity.changed` v2 y `activities.event.changed` v2.
  Los tests de ms-05 publican los fixtures directamente en Kafka para validar el contrato y la
  proyección.
- La key de cada mensaje debe ser el `aggregateId`. ms-05 rechaza una key distinta si viene
  informada.
- Los errores de contrato van al topic de descarte del consumidor. ms-05 usa `<topic>.dlq` y ms-03
  `catalog.site.changed.DLT` (el topic se declara al arrancar); ambos consumidores reintentan con
  backoff exponencial (~1 min) antes de descartar, y el offset avanza después del descarte.
- El feed de reindexado de ms-04 (`/api/v1/admin/catalog/export`) incluye `eventVersion` en cada
  ítem, así que ms-05 valida la versión del payload igual que en el camino de Kafka.

### Reservas: ítem único en ms-03, multi-ítem en ms-04 (P3-1, opción C firmada)

- Una reserva de **actividad o evento** (ms-03) es siempre de **un solo ítem**: `itemType`
  (`ACTIVITY`|`EVENT`) + `itemId`. No existe forma de reservar una actividad y un evento juntos.
- El **multi-ítem es exclusivo de los servicios** (ms-04), que agrupa varios ítems del mismo
  proveedor. ms-03 envuelve su único ítem en `items[1]` al publicar `bookings.reservation.changed`
  v2; el schema ya es multi-ítem aunque el dominio de ms-03 no lo sea.
- Si un turista quiere una actividad y un evento, se crean **dos reservas**. Cambiar esta regla
  implica migrar el dominio de ms-03 (decisión B, descartada por el PL el 2026-11-06).

### Estados de contenido (actividad y evento) y su efecto (P3-2)

Los literales son los de los schemas v2 y los enums de ms-03 (`ActivityStatus`, `EventStatus`); el
test `CatalogStateContractTest` de ms-03 falla si dejan de coincidir.

| Estado | Actividad | Evento | Listado público y reserva | Reservas existentes | Proyección en ms-05 |
|---|---|---|---|---|---|
| `DRAFT` | sí | sí | no aparece y no se puede reservar | no aplica | se proyecta el estado; no se recomienda |
| `PUBLISHED` | sí | sí | **sí** | sí | recomendable |
| `PAUSED` | sí | — | no admite reservas nuevas | se conservan y se pueden cancelar | no recomendable |
| `POSTPONED` | — | sí | no admite reservas nuevas | se conservan | no recomendable |
| `CANCELLED` | sí | sí | no | se cancelan | no recomendable |

Reglas verificadas en ms-03: solo `PUBLISHED` se puede reservar (`CreateReservationUseCase` rechaza
el resto) y solo `PUBLISHED` aparece en consultas y búsquedas públicas. `PAUSED` y `POSTPONED` son
pausas temporales: no borran ni invalidan reservas ya confirmadas, únicamente cierran la venta.

### Reseñas: propiedad, estados y moderación con IA (P3-2, P3-5 y P3-6)

**Quién es dueño de cada reseña** (una colección por servicio, no una unificada):

| Tipo de ítem reseñado | Servicio dueño | Colección |
|---|---|---|
| Usuarios y cola de moderación | ms-01 | Mongo `smxplore_reviews` |
| Actividades y eventos | ms-03 | Mongo `reviews` (`activity_reviews`, `event_reviews`, `review_reports`) |
| Sitios y servicios | ms-04 | Mongo `smxplore` (`reviews`) |

El evento `reviews.review.changed` es la unión: `payload.itemType` indica el origen y `producer`
conserva quién lo publicó. Un servicio nunca escribe reseñas de ítems de otro; ms-01 modera y
mantiene la reputación de usuarios, no la de los ítems.

**Estados de reseña** (idénticos en los tres servicios y en el schema v1):

| Estado | Significado | ¿Cuenta para la reputación? |
|---|---|---|
| `PUBLISHED` | visible y aprobada | **sí** |
| `PENDING_REVIEW` | retenida por el filtro de IA | no |
| `HIDDEN` | ocultada por moderación | no |
| `DELETED` | eliminada (baja lógica) | no |

**Moderación con IA:** los tres servicios usan el mismo contrato de configuración
(`app.reviews.ai-filter.*`): umbral `REVIEW_AI_THRESHOLD=0.7`, `fail-open=true` (si el modelo no
responde la reseña queda publicada) y acción `PENDING_REVIEW` cuando el modelo la marca como
sospechosa. El filtro está **apagado por defecto** (`REVIEW_AI_ENABLED=false`) y al encenderlo exige
`LITTLELLM_URL`. Un `PENDING_REVIEW` no alimenta la reputación hasta que se aprueba: es intención
del contrato, no un accidente.

### Indicadores de demanda: un dueño por dominio (P3-10)

Los dos endpoints `GET /api/v1/demand-indicators/report` no son duplicados accidentales: cada
servicio reporta los datos que posee.

| Dominio | Dueño | Ruta |
|---|---|---|
| Actividades y eventos | ms-03 | `/api/v1/demand-indicators/report` en ms-03 (`/svc/ms-03/...` por el gateway) |
| Sitios, servicios y zonas | ms-04 | `/api/v1/demand-indicators/report` en ms-04 |

Un indicador nuevo se agrega en el servicio dueño del dato; no se replican métricas entre
servicios.

### Catálogo semilla y taxonomía (P3-10)

- Categorías base de una instalación nueva (`app.catalog.seed-enabled` /
  `CATALOG_SEED_ENABLED`, apagado por defecto; encendido en el stack local): `AVENTURA`,
  `CULTURA`, `GASTRONOMIA`, `NATURALEZA`, `BIENESTAR`, `RECREACION`, todas con alcance `GENERAL`.
  El sembrado es idempotente y no borra ni renombra nada.
- Jerarquía: un nivel (`parentId`). El administrador crea las categorías hijas y las de alcance
  específico.
- Zonas: `type` es texto libre y el estado (`ACTIVE|CONGESTED|INACTIVE`) se cambia **a mano**. No
  hay fuente automática de congestión; si algún día la hay, entra por `catalog.zone.changed`.

### Pagos: fuera de alcance (P3-7 firmado el 2026-11-06)

- **No se procesa ningún pago en esta entrega**: no hay PSE, pasarela, webhooks, conciliación ni
  estado `PENDING_PAYMENT`. Las reservas nacen `CONFIRMED` sin cobro.
- El campo `paymentMethod` (`CARD`|`CASH`|`TRANSFER` en ms-03) es una **preferencia declarativa**
  que se guarda para reportes; ningún servicio cobra ni valida contra una pasarela.
- RF60 y RF61 (PSE) del SRS quedan **fuera de alcance** por decisión del PL. RF55 (itinerario)
  queda diferido con firma.
- Si en el futuro se agrega un pago real, debe entrar como un estado/evento nuevo y no cambiar el
  significado de `paymentMethod`.

## Validación local

Con Node.js:

```bash
npx ajv-cli@5 validate -s contracts/events/reviews.review.changed.v1.json \
  -d contracts/events/examples/reviews.review.changed.v1.example.json \
  --spec=draft2020 --strict=false -r contracts/events/envelope.v1.json
```

`--strict=false` evita que AJV falle por no tener `ajv-formats` instalado: valida la estructura
y emite warnings por los formatos desconocidos. La validación estricta de `uuid` y `date-time`
la hace la suite Java con Draft 2020-12 y `formatAssertionsEnabled(true)`.

El workflow `.github/workflows/contracts-ci.yml` repite esa validación para **todos** los pares
schema/ejemplo en cada push a `main`/`microservicios` o PR que toque `contracts/`, y además exige
que todo `*.v2.json` fije `eventVersion` con `const 2`. No levanta el stack ni necesita Docker.

En ms-05, `EventContractTest` valida todos los ejemplos contra su schema y
`ProjectionEventDecoderTest` valida que el consumidor pueda deserializarlos. El esquema no se
valida en runtime para no penalizar la latencia del consumidor; un mensaje inválido se detecta
al construir el evento de dominio y termina en la DLQ.

Los outboxes de ms-03 y ms-04 ya están implementados; cada productor mantiene su propia suite de
contrato (`ActivityEventContractTest`, `ReservationEventContractTest`, `ReviewEventContractTest` en
ms-03 y `EventContractTest` en ms-04) y debe seguir haciéndolo al cambiar un payload.

## Schema Registry

No se usa un registry en esta entrega. La carpeta versionada + tests de contrato es suficiente.
Si el número de eventos crece, se puede migrar a Confluent Schema Registry o Apicurio sin
cambiar los nombres de los eventos.
