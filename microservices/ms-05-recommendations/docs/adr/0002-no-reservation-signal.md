# ADR 0002 — Sin `RESERVO` en las recomendaciones

- Estado: aceptado
- Fecha: 2026-10-26
- Contexto: el modelo inicial proponía usar el número de reservas como señal de popularidad y
  como filtro de “ya reservado”.

## Decisión

ms-05 **no usa la relación `RESERVO`**:

- No se crea la arista en Neo4j.
- No se consume `bookings.reservation.changed`.
- No se usa el número de reservas para popularidad, ranking ni desempate.
- No se guardan `reservationId`, `people`, `total` ni `payment_method`.
- El filtro de “ya reservado” no se implementa en fase 0-1. En su lugar se excluyen los ítems
  que el turista ya reseñó y los eventos vencidos.

La popularidad se reemplaza por **calidad de reseñas con promedio bayesiano + diversidad**:

- `quality = (C * globalAvg + ratingSum) / (C + ratingCount)`.
- Topes por sitio/categoría.
- Sin `price` ni `total` como proxy de calidad.

## Motivos

1. **Sesgo de popularidad**: las reservas son consecuencia de la exposición, el marketing, la
   capacidad y las recomendaciones anteriores, no solo de la calidad. Usarlas crea un ciclo
   “lo más mostrado se reserva más y se muestra más”.
2. **Ventaja estructural**: favorece a proveedores grandes o con más capacidad; los nuevos
   nunca alcanzan.
3. **Datos comerciales**: `people` y `total` son información sensible que no necesita el grafo.
4. **Explicabilidad**: es más fácil justificar “4.7/5 con 120 reseñas” que “tiene 300 reservas”.
5. **Privacidad**: menos PII y menos datos comerciales en Neo4j.

## Consecuencias

- Un ítem ya reservado puede volver a aparecer si el turista no lo reseñó. Es una decisión
  consciente de la fase 0-1. Si el producto exige excluirlo, se puede añadir una marca negativa
  `YA_RESERVO` sin datos comerciales y usarla solo en `NOT EXISTS`; no se hará sin aprobación.
- ms-05 no necesita el topic de reservas. ms-03 puede seguir publicándolo para notificaciones y
  analítica.
- La calidad depende de que ms-03 publique `reviews.review.changed` en fase 2 y de que las
  reseñas tengan un estado visible.
- Las reseñas también pueden manipularse; por eso se usa suavizado bayesiano, deduplicación por
  `reviewId`, y topes de exposición. En el futuro se puede añadir “compra verificada” sin volver
  a usar `RESERVO` para el ranking.

## Alternativas descartadas

| Alternativa | Por qué no |
|---|---|
| Mantener `RESERVO` solo para popularidad | Es exactamente el sesgo que se quiere evitar. |
| `RESERVO` solo para excluir ya reservados | Aceptable si el producto lo exige, pero no es necesario en fase 0-1 y añade datos comerciales. |
| Vistas como única señal | También están sesgadas por exposición; se usan solo como señal débil, no como ranking principal. |
| Popularidad por precio | El precio no mide calidad y perjudica a opciones económicas. |
