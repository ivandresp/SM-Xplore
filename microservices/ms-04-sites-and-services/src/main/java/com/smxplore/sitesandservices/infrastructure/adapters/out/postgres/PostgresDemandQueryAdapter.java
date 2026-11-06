package com.smxplore.sitesandservices.infrastructure.adapters.out.postgres;

import com.smxplore.sitesandservices.domain.model.indicator.DemandQuery;
import com.smxplore.sitesandservices.domain.model.indicator.DemandRecord;
import com.smxplore.sitesandservices.domain.model.reservation.ReservationStatus;
import com.smxplore.sitesandservices.domain.ports.out.DemandQueryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class PostgresDemandQueryAdapter implements DemandQueryPort {

    private final DatabaseClient databaseClient;

    @Override
    public Flux<DemandRecord> find(DemandQuery query) {
        if (query.hasNoServices()) {
            return Flux.empty();
        }
        StringBuilder sql = new StringBuilder("""
                SELECT r.reservation_id, r.tourist_id, r.status, r.booked_for,
                       ri.service_id, ri.provider_id, ri.service_name, ri.service_type,
                       ri.people, ri.total AS item_total
                FROM reservations r
                JOIN reservation_items ri ON ri.reservation_id = r.reservation_id
                WHERE r.booked_for >= :from
                  AND r.booked_for < :to
                """);
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("from", offset(query.from()));
        params.put("to", offset(query.to()));
        if (query.providerId() != null) {
            sql.append(" AND ri.provider_id = :providerId");
            params.put("providerId", query.providerId());
        }
        List<UUID> serviceIds = query.serviceIds();
        if (serviceIds != null) {
            if (serviceIds.isEmpty()) {
                return Flux.empty();
            }
            sql.append(" AND ri.service_id IN (");
            for (int index = 0; index < serviceIds.size(); index++) {
                if (index > 0) {
                    sql.append(", ");
                }
                String name = "serviceId" + index;
                sql.append(':').append(name);
                params.put(name, serviceIds.get(index));
            }
            sql.append(')');
        }
        sql.append(" ORDER BY r.booked_for ASC, r.reservation_id ASC, ri.position ASC");

        DatabaseClient.GenericExecuteSpec spec = databaseClient.sql(sql.toString());
        for (Map.Entry<String, Object> entry : params.entrySet()) {
            spec = spec.bind(entry.getKey(), entry.getValue());
        }
        return spec.map((row, metadata) -> new DemandRecord(
                        row.get("reservation_id", UUID.class),
                        row.get("tourist_id", UUID.class),
                        ReservationStatus.valueOf(Objects.requireNonNull(row.get("status", String.class))),
                        Objects.requireNonNull(row.get("booked_for", OffsetDateTime.class)).toInstant(),
                        row.get("service_id", UUID.class),
                        row.get("provider_id", UUID.class),
                        row.get("service_name", String.class),
                        row.get("service_type", String.class),
                        Objects.requireNonNull(row.get("people", Integer.class)),
                        row.get("item_total", BigDecimal.class)))
                .all();
    }

    private static OffsetDateTime offset(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }
}
