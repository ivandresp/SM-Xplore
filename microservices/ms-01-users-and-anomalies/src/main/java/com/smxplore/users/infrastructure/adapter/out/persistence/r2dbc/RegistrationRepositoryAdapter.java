package com.smxplore.users.infrastructure.adapter.out.persistence.r2dbc;

import com.smxplore.users.application.dto.PageResult;
import com.smxplore.users.application.dto.RegistrationSearchCriteria;
import com.smxplore.users.application.dto.RegistrationSummaryView;
import com.smxplore.users.application.port.out.RegistrationRepository;
import com.smxplore.users.domain.model.RegistrationStatus;
import io.r2dbc.spi.Row;
import java.util.List;
import java.util.UUID;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.r2dbc.core.DatabaseClient.GenericExecuteSpec;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

/**
 * Cola de solicitudes de alta: guías y prestadoras de servicios en una sola consulta.
 *
 * <p>Cada usuario tiene exactamente uno de los dos perfiles, así que {@code COALESCE} deja el estado del alta en una
 * sola columna sin depender de qué tabla lo aporta.
 */
@Repository
public class RegistrationRepositoryAdapter implements RegistrationRepository {

    private static final String FROM_AND_JOINS = """
            FROM users u
            LEFT JOIN guide_profiles g ON g.user_id = u.id
            LEFT JOIN provider_profiles p ON p.user_id = u.id
            """;

    private static final String FILTERS = """
            WHERE u.role IN ('GUIDE', 'SERVICE_PROVIDER')
              AND (CAST(:role AS text) IS NULL OR u.role = :role)
              AND (CAST(:status AS text) IS NULL
                   OR COALESCE(g.registration_status, p.registration_status) = :status)
            """;

    private static final String SELECT_PAGE =
            """
            SELECT u.id AS id,
                   u.role AS role,
                   u.full_name AS full_name,
                   u.email AS email,
                   g.license AS license,
                   p.nit AS nit,
                   u.signed_up_at AS signed_up_at,
                   COALESCE(g.registration_status, p.registration_status) AS registration_status
            """ + FROM_AND_JOINS + FILTERS + " ORDER BY u.signed_up_at ASC, u.id ASC LIMIT :limit OFFSET :offset";

    private static final String COUNT = "SELECT count(*) AS total " + FROM_AND_JOINS + FILTERS;

    private final DatabaseClient databaseClient;

    public RegistrationRepositoryAdapter(DatabaseClient databaseClient) {
        this.databaseClient = databaseClient;
    }

    @Override
    public Mono<PageResult<RegistrationSummaryView>> search(RegistrationSearchCriteria criteria) {
        Mono<Long> total = bindFilters(databaseClient.sql(COUNT), criteria)
                .map((row, metadata) -> row.get("total", Long.class))
                .one()
                .defaultIfEmpty(0L);

        Mono<List<RegistrationSummaryView>> content = bindFilters(databaseClient.sql(SELECT_PAGE), criteria)
                .bind("limit", criteria.size())
                .bind("offset", (long) criteria.page() * criteria.size())
                .map((row, metadata) -> toView(row))
                .all()
                .collectList();

        return total.zipWith(content)
                .map(tuple -> PageResult.of(tuple.getT2(), criteria.page(), criteria.size(), tuple.getT1()));
    }

    private static GenericExecuteSpec bindFilters(GenericExecuteSpec spec, RegistrationSearchCriteria criteria) {
        spec = bindNullable(
                spec, "role", criteria.role() == null ? null : criteria.role().code(), String.class);
        spec = bindNullable(
                spec,
                "status",
                criteria.status() == null ? null : criteria.status().name(),
                String.class);
        return spec;
    }

    private static RegistrationSummaryView toView(Row row) {
        String status = row.get("registration_status", String.class);
        return new RegistrationSummaryView(
                row.get("id", UUID.class),
                row.get("role", String.class),
                row.get("full_name", String.class),
                row.get("email", String.class),
                row.get("license", String.class),
                row.get("nit", String.class),
                UserPersistenceMapper.toInstant(row.get("signed_up_at", java.time.OffsetDateTime.class)),
                status == null ? null : RegistrationStatus.valueOf(status));
    }

    private static GenericExecuteSpec bindNullable(GenericExecuteSpec spec, String name, Object value, Class<?> type) {
        return value == null ? spec.bindNull(name, type) : spec.bind(name, value);
    }
}
