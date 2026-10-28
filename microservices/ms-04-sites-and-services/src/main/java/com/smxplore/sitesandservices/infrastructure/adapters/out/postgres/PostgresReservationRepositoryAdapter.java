package com.smxplore.sitesandservices.infrastructure.adapters.out.postgres;

import com.smxplore.sitesandservices.domain.model.common.PageResult;
import com.smxplore.sitesandservices.domain.model.reservation.Reservation;
import com.smxplore.sitesandservices.domain.model.reservation.ReservationFilter;
import com.smxplore.sitesandservices.domain.model.reservation.ReservationItem;
import com.smxplore.sitesandservices.domain.model.reservation.ReservationStatus;
import com.smxplore.sitesandservices.domain.ports.out.ReservationRepositoryPort;
import io.r2dbc.spi.Row;
import io.r2dbc.spi.RowMetadata;
import lombok.RequiredArgsConstructor;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class PostgresReservationRepositoryAdapter implements ReservationRepositoryPort {

    private static final String RESERVATION_COLUMNS = """
            r.reservation_id, r.tourist_id, r.tourist_name, r.status, r.total,
            r.number_of_people, r.booked_for, r.notes, r.cancellation_reason,
            r.idempotency_key, r.request_fingerprint,
            r.created_at, r.updated_at, r.confirmed_at, r.cancelled_at
            """;

    private static final String ITEM_COLUMNS = """
            reservation_item_id, reservation_id, service_id, service_name, provider_id,
            provider_name, service_type, unit_price, people, total, position
            """;

    private final DatabaseClient databaseClient;

    @Override
    public Mono<Reservation> save(Reservation reservation) {
        String sql = """
                INSERT INTO reservations (
                    reservation_id, tourist_id, tourist_name, status, total,
                    number_of_people, booked_for, notes, cancellation_reason,
                    idempotency_key, request_fingerprint,
                    created_at, updated_at, confirmed_at, cancelled_at
                )
                VALUES (
                    :reservationId, :touristId, :touristName, :status, :total,
                    :numberOfPeople, :bookedFor, :notes, :cancellationReason,
                    :idempotencyKey, :requestFingerprint,
                    :createdAt, :updatedAt, :confirmedAt, :cancelledAt
                )
                ON CONFLICT (reservation_id) DO UPDATE SET
                    status = EXCLUDED.status,
                    total = EXCLUDED.total,
                    number_of_people = EXCLUDED.number_of_people,
                    notes = EXCLUDED.notes,
                    cancellation_reason = EXCLUDED.cancellation_reason,
                    idempotency_key = EXCLUDED.idempotency_key,
                    request_fingerprint = EXCLUDED.request_fingerprint,
                    updated_at = EXCLUDED.updated_at,
                    confirmed_at = EXCLUDED.confirmed_at,
                    cancelled_at = EXCLUDED.cancelled_at
                """;
        DatabaseClient.GenericExecuteSpec spec = databaseClient.sql(sql)
                .bind("reservationId", reservation.reservationId())
                .bind("touristId", reservation.touristId())
                .bind("touristName", reservation.touristName())
                .bind("status", reservation.status().name())
                .bind("total", reservation.total())
                .bind("numberOfPeople", reservation.numberOfPeople())
                .bind("bookedFor", offset(reservation.bookedFor()))
                .bind("createdAt", offset(reservation.createdAt()))
                .bind("updatedAt", offset(reservation.updatedAt()));
        spec = bindNullableString(spec, "notes", reservation.notes());
        spec = bindNullableString(spec, "cancellationReason", reservation.cancellationReason());
        spec = bindNullableString(spec, "idempotencyKey", reservation.idempotencyKey());
        spec = bindNullableString(spec, "requestFingerprint", reservation.requestFingerprint());
        spec = bindNullableInstant(spec, "confirmedAt", reservation.confirmedAt());
        spec = bindNullableInstant(spec, "cancelledAt", reservation.cancelledAt());

        return spec.fetch().rowsUpdated()
                .thenMany(Flux.fromIterable(reservation.items())
                        .concatMap(item -> upsertItem(reservation.reservationId(), item)))
                .then(Mono.just(reservation));
    }

    private Mono<Void> upsertItem(UUID reservationId, ReservationItem item) {
        String sql = """
                INSERT INTO reservation_items (
                    reservation_item_id, reservation_id, service_id, service_name,
                    provider_id, provider_name, service_type, unit_price, people,
                    total, position
                )
                VALUES (
                    :reservationItemId, :reservationId, :serviceId, :serviceName,
                    :providerId, :providerName, :serviceType, :unitPrice, :people,
                    :total, :position
                )
                ON CONFLICT (reservation_id, service_id) DO UPDATE SET
                    service_name = EXCLUDED.service_name,
                    provider_id = EXCLUDED.provider_id,
                    provider_name = EXCLUDED.provider_name,
                    service_type = EXCLUDED.service_type,
                    unit_price = EXCLUDED.unit_price,
                    people = EXCLUDED.people,
                    total = EXCLUDED.total,
                    position = EXCLUDED.position
                """;
        return databaseClient.sql(sql)
                .bind("reservationItemId", item.reservationItemId())
                .bind("reservationId", reservationId)
                .bind("serviceId", item.serviceId())
                .bind("serviceName", item.serviceName())
                .bind("providerId", item.providerId())
                .bind("providerName", item.providerName())
                .bind("serviceType", item.serviceType())
                .bind("unitPrice", item.unitPrice())
                .bind("people", item.people())
                .bind("total", item.total())
                .bind("position", item.position())
                .fetch().rowsUpdated()
                .then();
    }

    @Override
    public Mono<Reservation> findById(UUID reservationId) {
        String sql = "SELECT " + RESERVATION_COLUMNS
                + " FROM reservations r WHERE r.reservation_id = :reservationId";
        return databaseClient.sql(sql)
                .bind("reservationId", reservationId)
                .map(mapReservation())
                .one()
                .flatMap(this::loadItems)
                .map(this::validate);
    }

    @Override
    public Mono<Reservation> findByIdempotencyKey(UUID touristId, String idempotencyKey) {
        String sql = "SELECT " + RESERVATION_COLUMNS
                + " FROM reservations r"
                + " WHERE r.tourist_id = :touristId AND r.idempotency_key = :idempotencyKey";
        return databaseClient.sql(sql)
                .bind("touristId", touristId)
                .bind("idempotencyKey", idempotencyKey)
                .map(mapReservation())
                .one()
                .flatMap(this::loadItems)
                .map(this::validate);
    }

    @Override
    public Mono<PageResult<Reservation>> findAll(ReservationFilter filter) {
        QueryParts parts = queryParts(filter);
        String countSql = "SELECT COUNT(*) AS total FROM reservations r" + parts.where();
        String pageSql = "SELECT " + RESERVATION_COLUMNS + " FROM reservations r" + parts.where()
                + " ORDER BY r.booked_for DESC, r.created_at DESC"
                + " LIMIT :limit OFFSET :offset";

        Map<String, Object> params = new LinkedHashMap<>(parts.params());
        params.put("limit", filter.size());
        params.put("offset", (long) filter.page() * filter.size());

        Mono<Long> total = bind(databaseClient.sql(countSql), parts.params())
                .map(row -> Objects.requireNonNull(row.get("total", Long.class)))
                .one()
                .defaultIfEmpty(0L);

        Mono<List<Reservation>> content = bind(databaseClient.sql(pageSql), params)
                .map(mapReservation())
                .all()
                .collectList()
                .flatMap(this::loadItemsForPage);

        return Mono.zip(total, content)
                .map(tuple -> new PageResult<>(
                        tuple.getT2(), filter.page(), filter.size(), tuple.getT1()));
    }

    @Override
    public Mono<Integer> reservedPeople(UUID serviceId, Instant from, Instant to) {
        String sql = """
                SELECT COALESCE(SUM(ri.people), 0) AS reserved
                FROM reservation_items ri
                JOIN reservations r ON r.reservation_id = ri.reservation_id
                WHERE ri.service_id = :serviceId
                  AND r.status IN ('PENDING', 'CONFIRMED')
                  AND r.booked_for >= :from
                  AND r.booked_for < :to
                """;
        return databaseClient.sql(sql)
                .bind("serviceId", serviceId)
                .bind("from", offset(from))
                .bind("to", offset(to))
                .map(row -> Objects.requireNonNull(row.get("reserved", Long.class)).intValue())
                .one()
                .defaultIfEmpty(0);
    }

    @Override
    public Mono<Void> lockService(UUID serviceId) {
        String sql = """
                SELECT pg_advisory_xact_lock(hashtextextended(:serviceId, 0))
                """;
        return databaseClient.sql(sql)
                .bind("serviceId", serviceId.toString())
                .then();
    }

    @Override
    public Mono<Void> lockIdempotencyKey(UUID touristId, String idempotencyKey) {
        String sql = """
                SELECT pg_advisory_xact_lock(hashtextextended(:lockKey, 0))
                """;
        return databaseClient.sql(sql)
                .bind("lockKey", "reservation-idempotency:" + touristId + ":" + idempotencyKey)
                .then();
    }

    @Override
    public Mono<Void> lockTourist(UUID touristId) {
        String sql = """
                SELECT pg_advisory_xact_lock(hashtextextended(:lockKey, 0))
                """;
        return databaseClient.sql(sql)
                .bind("lockKey", "reservation-tourist:" + touristId)
                .then();
    }

    @Override
    public Mono<Long> countActiveByTourist(UUID touristId, Instant now) {
        String sql = """
                SELECT COUNT(*) AS total
                FROM reservations
                WHERE tourist_id = :touristId
                  AND status = 'CONFIRMED'
                  AND booked_for >= :now
                """;
        return databaseClient.sql(sql)
                .bind("touristId", touristId)
                .bind("now", offset(now))
                .map(row -> Objects.requireNonNull(row.get("total", Long.class)))
                .one()
                .defaultIfEmpty(0L);
    }

    @Override
    public Mono<Long> countCreatedSince(UUID touristId, Instant since) {
        String sql = """
                SELECT COUNT(*) AS total
                FROM reservations
                WHERE tourist_id = :touristId
                  AND created_at >= :since
                """;
        return databaseClient.sql(sql)
                .bind("touristId", touristId)
                .bind("since", offset(since))
                .map(row -> Objects.requireNonNull(row.get("total", Long.class)))
                .one()
                .defaultIfEmpty(0L);
    }

    @Override
    public Flux<UUID> findCompletable(Instant cutoff, int limit) {
        String sql = """
                SELECT reservation_id
                FROM reservations
                WHERE status = 'CONFIRMED'
                  AND booked_for < :cutoff
                ORDER BY booked_for ASC, created_at ASC
                LIMIT :limit
                """;
        return databaseClient.sql(sql)
                .bind("cutoff", offset(cutoff))
                .bind("limit", limit)
                .map((row, metadata) -> row.get("reservation_id", UUID.class))
                .all();
    }

    @Override
    public Mono<Boolean> updateStatus(
            Reservation reservation,
            ReservationStatus expectedStatus
    ) {
        String sql = """
                UPDATE reservations
                SET status = :status,
                    cancellation_reason = :cancellationReason,
                    updated_at = :updatedAt,
                    confirmed_at = :confirmedAt,
                    cancelled_at = :cancelledAt
                WHERE reservation_id = :reservationId
                  AND status = :expectedStatus
                """;
        DatabaseClient.GenericExecuteSpec spec = databaseClient.sql(sql)
                .bind("reservationId", reservation.reservationId())
                .bind("status", reservation.status().name())
                .bind("expectedStatus", expectedStatus.name())
                .bind("updatedAt", offset(reservation.updatedAt()));
        spec = bindNullableString(spec, "cancellationReason", reservation.cancellationReason());
        spec = bindNullableInstant(spec, "confirmedAt", reservation.confirmedAt());
        spec = bindNullableInstant(spec, "cancelledAt", reservation.cancelledAt());
        return spec.fetch().rowsUpdated().map(updated -> updated == 1L);
    }

    private Mono<Reservation> loadItems(Reservation reservation) {
        String sql = "SELECT " + ITEM_COLUMNS
                + " FROM reservation_items WHERE reservation_id = :reservationId"
                + " ORDER BY position ASC";
        return databaseClient.sql(sql)
                .bind("reservationId", reservation.reservationId())
                .map(mapItem())
                .all()
                .collectList()
                .map(items -> reservation.toBuilder().items(items).build());
    }

    private Mono<List<Reservation>> loadItemsForPage(List<Reservation> reservations) {
        if (reservations.isEmpty()) {
            return Mono.just(List.of());
        }

        List<UUID> reservationIds = reservations.stream()
                .map(Reservation::reservationId)
                .toList();
        StringBuilder placeholders = new StringBuilder();
        for (int index = 0; index < reservationIds.size(); index++) {
            if (index > 0) {
                placeholders.append(", ");
            }
            placeholders.append(":reservationId").append(index);
        }
        String sql = "SELECT " + ITEM_COLUMNS
                + " FROM reservation_items"
                + " WHERE reservation_id IN (" + placeholders + ")"
                + " ORDER BY reservation_id ASC, position ASC";

        DatabaseClient.GenericExecuteSpec spec = databaseClient.sql(sql);
        for (int index = 0; index < reservationIds.size(); index++) {
            spec = spec.bind("reservationId" + index, reservationIds.get(index));
        }

        return spec.map(mapItemWithReservation())
                .all()
                .collectList()
                .map(rows -> {
                    Map<UUID, List<ReservationItem>> itemsByReservation = rows.stream()
                            .collect(Collectors.groupingBy(
                                    ReservationItemRow::reservationId,
                                    Collectors.mapping(
                                            ReservationItemRow::item,
                                            Collectors.toList()
                                    )
                            ));
                    return reservations.stream()
                            .map(reservation -> reservation.toBuilder()
                                    .items(itemsByReservation.getOrDefault(
                                            reservation.reservationId(), List.of()))
                                    .build())
                            .map(this::validate)
                            .toList();
                });
    }

    private Reservation validate(Reservation reservation) {
        reservation.validate();
        return reservation;
    }

    private QueryParts queryParts(ReservationFilter filter) {
        StringBuilder where = new StringBuilder(" WHERE 1 = 1");
        Map<String, Object> params = new LinkedHashMap<>();
        if (filter.touristId() != null) {
            where.append(" AND r.tourist_id = :touristId");
            params.put("touristId", filter.touristId());
        }
        if (filter.providerId() != null) {
            where.append(" AND EXISTS (SELECT 1 FROM reservation_items ri"
                    + " WHERE ri.reservation_id = r.reservation_id"
                    + " AND ri.provider_id = :providerId)");
            params.put("providerId", filter.providerId());
        }
        if (filter.serviceId() != null) {
            where.append(" AND EXISTS (SELECT 1 FROM reservation_items ri"
                    + " WHERE ri.reservation_id = r.reservation_id"
                    + " AND ri.service_id = :serviceId)");
            params.put("serviceId", filter.serviceId());
        }
        if (filter.status() != null) {
            where.append(" AND r.status = :status");
            params.put("status", filter.status().name());
        }
        if (filter.from() != null) {
            where.append(" AND r.booked_for >= :from");
            params.put("from", offset(filter.from()));
        }
        if (filter.to() != null) {
            where.append(" AND r.booked_for < :to");
            params.put("to", offset(filter.to()));
        }
        return new QueryParts(where.toString(), params);
    }

    private DatabaseClient.GenericExecuteSpec bind(
            DatabaseClient.GenericExecuteSpec spec,
            Map<String, Object> params
    ) {
        DatabaseClient.GenericExecuteSpec bound = spec;
        for (Map.Entry<String, Object> entry : params.entrySet()) {
            bound = bound.bind(entry.getKey(), entry.getValue());
        }
        return bound;
    }

    private DatabaseClient.GenericExecuteSpec bindNullableString(
            DatabaseClient.GenericExecuteSpec spec,
            String name,
            String value
    ) {
        return value == null ? spec.bindNull(name, String.class) : spec.bind(name, value);
    }

    private DatabaseClient.GenericExecuteSpec bindNullableInstant(
            DatabaseClient.GenericExecuteSpec spec,
            String name,
            Instant value
    ) {
        return value == null
                ? spec.bindNull(name, OffsetDateTime.class)
                : spec.bind(name, offset(value));
    }

    private BiFunction<Row, RowMetadata, Reservation> mapReservation() {
        return (row, metadata) -> Reservation.builder()
                .reservationId(row.get("reservation_id", UUID.class))
                .touristId(row.get("tourist_id", UUID.class))
                .touristName(row.get("tourist_name", String.class))
                .status(ReservationStatus.valueOf(
                        Objects.requireNonNull(row.get("status", String.class))))
                .total(row.get("total", BigDecimal.class))
                .bookedFor(instant(row, "booked_for"))
                .notes(row.get("notes", String.class))
                .cancellationReason(row.get("cancellation_reason", String.class))
                .idempotencyKey(row.get("idempotency_key", String.class))
                .requestFingerprint(row.get("request_fingerprint", String.class))
                .createdAt(instant(row, "created_at"))
                .updatedAt(instant(row, "updated_at"))
                .confirmedAt(nullableInstant(row, "confirmed_at"))
                .cancelledAt(nullableInstant(row, "cancelled_at"))
                .items(List.of())
                .build();
    }

    private BiFunction<Row, RowMetadata, ReservationItem> mapItem() {
        return (row, metadata) -> reservationItem(row);
    }

    private BiFunction<Row, RowMetadata, ReservationItemRow> mapItemWithReservation() {
        return (row, metadata) -> new ReservationItemRow(
                row.get("reservation_id", UUID.class),
                reservationItem(row)
        );
    }

    private ReservationItem reservationItem(Row row) {
        return ReservationItem.builder()
                .reservationItemId(row.get("reservation_item_id", UUID.class))
                .serviceId(row.get("service_id", UUID.class))
                .serviceName(row.get("service_name", String.class))
                .providerId(row.get("provider_id", UUID.class))
                .providerName(row.get("provider_name", String.class))
                .serviceType(row.get("service_type", String.class))
                .unitPrice(row.get("unit_price", BigDecimal.class))
                .people(Objects.requireNonNull(row.get("people", Integer.class)))
                .total(row.get("total", BigDecimal.class))
                .position(Objects.requireNonNull(row.get("position", Integer.class)))
                .build();
    }

    private static OffsetDateTime offset(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    private static Instant instant(Row row, String column) {
        return Objects.requireNonNull(row.get(column, OffsetDateTime.class)).toInstant();
    }

    private static Instant nullableInstant(Row row, String column) {
        OffsetDateTime value = row.get(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }

    private record ReservationItemRow(UUID reservationId, ReservationItem item) {
    }

    private record QueryParts(String where, Map<String, Object> params) {
    }
}
