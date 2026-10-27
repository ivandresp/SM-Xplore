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
    ├── activities.activity.changed.v1.json
    ├── activities.event.changed.v1.json
    ├── bookings.reservation.changed.v1.json
    ├── catalog.category.changed.v1.json
    ├── catalog.service.changed.v1.json
    ├── catalog.site.changed.v1.json
    ├── reviews.review.changed.v1.json
    └── examples/
```

El archivo se llama igual que el `eventType` y termina en `.v1.json`. Cada schema tiene un
fixture en `examples/` con el mismo nombre y sufijo `.v1.example.json`.

## Sobre del evento

Todos los eventos comparten el sobre de `envelope.v1.json`:

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
- Nunca reutilizar un nombre de campo con otro significado.
- Los consumidores ignoran campos desconocidos; los campos requeridos no se renombran.
- Un productor valida el evento serializado contra el esquema antes de publicar.
- Un consumidor valida el fixture antes de deserializar en sus tests.
- Un cambio de esquema que rompa un consumidor bloquea el merge.

## Topics y consumidores

| Topic / eventType | Productor | ms-05 |
|---|---|---|
| `catalog.site.changed` | ms-04 | sí |
| `catalog.service.changed` | ms-04 | sí |
| `catalog.category.changed` | ms-04 | sí |
| `activities.event.changed` | ms-03 | sí |
| `activities.activity.changed` | ms-03 | sí |
| `reviews.review.changed` | ms-03 / ms-04 | sí |
| `bookings.reservation.changed` | ms-03 | **no** |

`reviews.review.changed` lo publica ms-03 para actividades/eventos y ms-04 para sitios y
servicios. El campo `payload.itemType` indica el origen; ms-05 consume ambos sin distinguir el
productor. El sobre conserva `producer` para trazabilidad.

`bookings.reservation.changed` se mantiene porque notificaciones y analítica lo usan, pero
**ms-05 no lo consume**. La recomendación no usa reservas: ver
`microservices/ms-05-recommendations/docs/adr/0002-no-reservation-signal.md`.

Estado de fase 2:

- ms-05 ya consume los seis topics marcados con “sí”.
- ms-03 y ms-04 todavía no tienen outbox ni publican estos eventos en sus ramas. Los tests de
  ms-05 publican los fixtures directamente en Kafka para validar el contrato y la proyección.
- La key de cada mensaje debe ser el `aggregateId`. ms-05 rechaza una key distinta si viene
  informada.
- Los errores de contrato van a `<topic>.dlq`.

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

En ms-05, `EventContractTest` valida todos los ejemplos contra su schema y
`ProjectionEventDecoderTest` valida que el consumidor pueda deserializarlos. El esquema no se
valida en runtime para no penalizar la latencia del consumidor; un mensaje inválido se detecta
al construir el evento de dominio y termina en la DLQ.

Cuando ms-03/ms-04 implementen el outbox, cada productor debe incluir la misma validación en su
suite de tests.

## Schema Registry

No se usa un registry en esta entrega. La carpeta versionada + tests de contrato es suficiente.
Si el número de eventos crece, se puede migrar a Confluent Schema Registry o Apicurio sin
cambiar los nombres de los eventos.
