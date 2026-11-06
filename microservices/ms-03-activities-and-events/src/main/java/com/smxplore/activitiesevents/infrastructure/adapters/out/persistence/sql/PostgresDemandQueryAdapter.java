package com.smxplore.activitiesevents.infrastructure.adapters.out.persistence.sql;

import com.smxplore.activitiesevents.domain.model.indicator.DemandQuery;
import com.smxplore.activitiesevents.domain.model.indicator.DemandRecord;
import com.smxplore.activitiesevents.domain.model.reservation.ReservationItemType;
import com.smxplore.activitiesevents.domain.model.reservation.ReservationStatus;
import com.smxplore.activitiesevents.domain.ports.repository.DemandQueryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Reads reservation rows joined with their activity or event. The join stays in PostgreSQL because
 * both tables live there; site/zone metadata is completed later from ms-04.
 */
@Component
@RequiredArgsConstructor
public class PostgresDemandQueryAdapter implements DemandQueryPort {

    private final DatabaseClient databaseClient;

    @Override
    public Flux<DemandRecord> find(DemandQuery query) {
        if (query.hasNoItems()) {
            return Flux.empty();
        }
        var sql = new StringBuilder("""
                SELECT r.id AS reservation_id, r.tourist_id, r.status, r.booked_for,
                       r.item_type, r.item_id, r.number_of_people, r.total,
                       COALESCE(r.guide_id, a.guia_id) AS guide_id,
                       COALESCE(a.name, e.name) AS item_name,
                       COALESCE(a.sitio_id, e.sitio_id) AS site_id,
                       COALESCE(a.category, e.category) AS category,
                       e.sitio_nombre AS site_name,
                       e.manager_id AS manager_id,
                       COALESCE(e.expected_attendance, 0) AS expected_attendance,
                       COALESCE(a.max_capacity, e.max_capacity, 0) AS max_capacity
                FROM reservations r
                LEFT JOIN actividad a ON r.item_type = 'ACTIVITY' AND a.id = r.item_id
                LEFT JOIN evento e ON r.item_type = 'EVENT' AND e.id = r.item_id
                WHERE r.booked_for >= :from
                  AND r.booked_for < :to
                """);
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("from", offset(query.from()));
        params.put("to", offset(query.to()));
        if (query.guideId() != null) {
            sql.append(" AND (r.guide_id = :guideId OR a.guia_id = :guideId)");
            params.put("guideId", query.guideId());
        }
        appendItemFilter(sql, params, query.activityIds(), query.eventIds());
        sql.append(" ORDER BY r.booked_for ASC, r.id ASC");

        DatabaseClient.GenericExecuteSpec spec = databaseClient.sql(sql.toString());
        for (Map.Entry<String, Object> entry : params.entrySet()) {
            spec = spec.bind(entry.getKey(), entry.getValue());
        }
        return spec.map((row, metadata) -> new DemandRecord(
                        row.get("reservation_id", UUID.class),
                        row.get("tourist_id", UUID.class),
                        ReservationStatus.valueOf(Objects.requireNonNull(row.get("status", String.class))),
                        Objects.requireNonNull(row.get("booked_for", OffsetDateTime.class)).toInstant(),
                        ReservationItemType.valueOf(Objects.requireNonNull(row.get("item_type", String.class))),
                        row.get("item_id", UUID.class),
                        row.get("item_name", String.class),
                        row.get("site_id", UUID.class),
                        row.get("site_name", String.class),
                        row.get("guide_id", UUID.class),
                        row.get("manager_id", UUID.class),
                        Objects.requireNonNullElse(row.get("expected_attendance", Integer.class), 0),
                        Objects.requireNonNullElse(row.get("max_capacity", Integer.class), 0),
                        row.get("category", String.class),
                        Objects.requireNonNull(row.get("number_of_people", Integer.class)),
                        row.get("total", BigDecimal.class)))
                .all();
    }

    private static void appendItemFilter(
            StringBuilder sql,
            Map<String, Object> params,
            Collection<UUID> activityIds,
            Collection<UUID> eventIds
    ) {
        var conditions = new ArrayList<String>();
        if (activityIds != null && !activityIds.isEmpty()) {
            conditions.add(itemCondition("ACTIVITY", activityIds, "activity", params));
        }
        if (eventIds != null && !eventIds.isEmpty()) {
            conditions.add(itemCondition("EVENT", eventIds, "event", params));
        }
        if (!conditions.isEmpty()) {
            sql.append(" AND (").append(String.join(" OR ", conditions)).append(')');
        }
    }

    private static String itemCondition(
            String itemType,
            Collection<UUID> itemIds,
            String parameterPrefix,
            Map<String, Object> params
    ) {
        List<UUID> ids = new ArrayList<>(itemIds);
        var condition = new StringBuilder("(r.item_type = '").append(itemType).append("' AND r.item_id IN (");
        for (int index = 0; index < ids.size(); index++) {
            if (index > 0) {
                condition.append(", ");
            }
            String name = parameterPrefix + "Id" + index;
            condition.append(':').append(name);
            params.put(name, ids.get(index));
        }
        return condition.append("))").toString();
    }

    private static OffsetDateTime offset(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }
}
