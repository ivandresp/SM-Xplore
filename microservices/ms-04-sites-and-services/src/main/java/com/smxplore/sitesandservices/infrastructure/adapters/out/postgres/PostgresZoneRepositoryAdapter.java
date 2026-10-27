package com.smxplore.sitesandservices.infrastructure.adapters.out.postgres;

import com.smxplore.sitesandservices.domain.model.common.PageResult;
import com.smxplore.sitesandservices.domain.model.zone.Zone;
import com.smxplore.sitesandservices.domain.model.zone.ZoneFilter;
import com.smxplore.sitesandservices.domain.model.zone.ZoneStatus;
import com.smxplore.sitesandservices.domain.ports.out.ZoneRepositoryPort;
import io.r2dbc.spi.Row;
import io.r2dbc.spi.RowMetadata;
import lombok.RequiredArgsConstructor;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.OffsetDateTime;
import java.util.*;
import java.util.function.BiFunction;

@Component
@RequiredArgsConstructor
public class PostgresZoneRepositoryAdapter implements ZoneRepositoryPort {

    private static final String SELECT_COLUMNS = """
            zone_id, manager_id, name, type, status, description, created_at, updated_at
            """;

    private final DatabaseClient databaseClient;

    @Override
    public Mono<Zone> save(Zone zone) {
        String sql = """
                INSERT INTO zones (zone_id, manager_id, name, type, status, description, created_at, updated_at)
                VALUES (:zoneId, :managerId, :name, :type, :status, :description, :createdAt, :updatedAt)
                ON CONFLICT (zone_id) DO UPDATE SET
                    manager_id = EXCLUDED.manager_id,
                    name = EXCLUDED.name,
                    type = EXCLUDED.type,
                    status = EXCLUDED.status,
                    description = EXCLUDED.description,
                    updated_at = EXCLUDED.updated_at
                """;
        DatabaseClient.GenericExecuteSpec spec = databaseClient.sql(sql)
                .bind("zoneId", zone.zoneId())
                .bind("managerId", zone.managerId())
                .bind("name", zone.name())
                .bind("status", zone.status().name())
                .bind("createdAt", OffsetDateTime.ofInstant(zone.createdAt(), java.time.ZoneOffset.UTC))
                .bind("updatedAt", OffsetDateTime.ofInstant(zone.updatedAt(), java.time.ZoneOffset.UTC));
        spec = bindNullable(spec, "type", zone.type());
        spec = bindNullable(spec, "description", zone.description());
        return spec.fetch().rowsUpdated().thenReturn(zone);
    }

    @Override
    public Mono<Zone> findById(UUID zoneId) {
        String sql = "SELECT " + SELECT_COLUMNS + " FROM zones WHERE zone_id = :zoneId";
        return databaseClient.sql(sql)
                .bind("zoneId", zoneId)
                .map(rowMapper())
                .one();
    }

    @Override
    public Mono<PageResult<Zone>> findAll(ZoneFilter filter) {
        QueryParts parts = queryParts(filter);
        String countSql = "SELECT COUNT(*) AS total FROM zones" + parts.where();
        String pageSql = "SELECT " + SELECT_COLUMNS + " FROM zones" + parts.where()
                + " ORDER BY name ASC LIMIT :limit OFFSET :offset";

        Map<String, Object> params = new LinkedHashMap<>(parts.params());
        params.put("limit", filter.size());
        params.put("offset", (long) filter.page() * filter.size());

        Mono<Long> total = bind(databaseClient.sql(countSql), parts.params())
                .map(row -> Objects.requireNonNull(row.get("total", Long.class)))
                .one()
                .defaultIfEmpty(0L);

        Mono<List<Zone>> content = bind(databaseClient.sql(pageSql), params)
                .map(rowMapper())
                .all()
                .collectList();

        return Mono.zip(total, content)
                .map(tuple -> new PageResult<>(tuple.getT2(), filter.page(), filter.size(), tuple.getT1()));
    }

    private QueryParts queryParts(ZoneFilter filter) {
        StringBuilder where = new StringBuilder(" WHERE 1 = 1");
        Map<String, Object> params = new LinkedHashMap<>();
        if (filter.status() != null) {
            where.append(" AND status = :status");
            params.put("status", filter.status().name());
        }
        if (filter.type() != null && !filter.type().isBlank()) {
            where.append(" AND type = :type");
            params.put("type", filter.type());
        }
        if (filter.search() != null && !filter.search().isBlank()) {
            where.append(" AND LOWER(name) LIKE :search");
            params.put("search", "%" + filter.search().trim().toLowerCase(Locale.ROOT) + "%");
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

    private DatabaseClient.GenericExecuteSpec bindNullable(
            DatabaseClient.GenericExecuteSpec spec,
            String name,
            String value
    ) {
        return value == null ? spec.bindNull(name, String.class) : spec.bind(name, value);
    }

    private BiFunction<Row, RowMetadata, Zone> rowMapper() {
        return (row, metadata) -> {
            Zone zone = Zone.builder()
                    .zoneId(row.get("zone_id", UUID.class))
                    .managerId(row.get("manager_id", UUID.class))
                    .name(row.get("name", String.class))
                    .type(row.get("type", String.class))
                    .description(row.get("description", String.class))
                    .status(ZoneStatus.valueOf(row.get("status", String.class)))
                    .createdAt(Objects.requireNonNull(row.get("created_at", OffsetDateTime.class)).toInstant())
                    .updatedAt(Objects.requireNonNull(row.get("updated_at", OffsetDateTime.class)).toInstant())
                    .build();
            zone.validate();
            return zone;
        };
    }

    private record QueryParts(String where, Map<String, Object> params) {
    }
}
