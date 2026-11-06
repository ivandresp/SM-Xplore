package com.smxplore.users.infrastructure.adapter.out.persistence.r2dbc;

import com.smxplore.users.application.dto.PageResult;
import com.smxplore.users.application.dto.UserSearchCriteria;
import com.smxplore.users.application.dto.UserSummaryView;
import com.smxplore.users.domain.model.ProviderRegistrationStatus;
import com.smxplore.users.domain.model.UserStatus;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.r2dbc.core.DatabaseClient.GenericExecuteSpec;
import reactor.core.publisher.Mono;

/**
 * Consulta de lectura del catálogo administrativo de usuarios.
 *
 * <p>Se usa SQL porque el filtro por identificación (DNI/NIT) requiere unir las cinco tablas de perfil y la
 * proyección es plana para evitar cargar cada perfil por separado.
 */
final class UserSummaryQuery {

    private static final String SELECT = """
            SELECT u.id AS id,
                   u.full_name AS full_name,
                   u.email AS email,
                   u.role AS role,
                   u.status AS status,
                   p.registration_status AS provider_status,
                   u.signed_up_at AS signed_up_at
            """;

    private static final String FROM_AND_JOINS = """
            FROM users u
            LEFT JOIN guide_profiles g ON g.user_id = u.id
            LEFT JOIN tourist_profiles t ON t.user_id = u.id
            LEFT JOIN provider_profiles p ON p.user_id = u.id
            LEFT JOIN destination_manager_profiles d ON d.user_id = u.id
            LEFT JOIN admin_profiles a ON a.user_id = u.id
            """;

    private static final String FILTERS = """
            WHERE (CAST(:q AS text) IS NULL
                   OR LOWER(u.full_name) LIKE :q ESCAPE '!'
                   OR LOWER(u.email) LIKE :q ESCAPE '!'
                   OR g.dni_number LIKE :rawLike ESCAPE '!'
                   OR t.dni_number LIKE :rawLike ESCAPE '!'
                   OR a.dni_number LIKE :rawLike ESCAPE '!'
                   OR p.nit LIKE :rawLike ESCAPE '!'
                   OR d.nit LIKE :rawLike ESCAPE '!')
              AND (CAST(:role AS text) IS NULL OR u.role = :role)
              AND (CAST(:status AS text) IS NULL OR u.status = :status)
              AND (CAST(:providerStatus AS text) IS NULL OR p.registration_status = :providerStatus)
            """;

    private static final Map<String, String> SORT_COLUMNS = Map.of(
            "fullName", "LOWER(u.full_name)",
            "email", "LOWER(u.email)",
            "signedUpAt", "u.signed_up_at",
            "updatedAt", "u.updated_at",
            "role", "u.role",
            "status", "u.status");

    private final DatabaseClient databaseClient;

    UserSummaryQuery(DatabaseClient databaseClient) {
        this.databaseClient = databaseClient;
    }

    Mono<PageResult<UserSummaryView>> search(UserSearchCriteria criteria) {
        Mono<Long> total = bindFilters(
                        databaseClient.sql("SELECT count(*) AS total " + FROM_AND_JOINS + FILTERS), criteria)
                .map((row, metadata) -> row.get("total", Long.class))
                .one();

        String sql = SELECT
                + FROM_AND_JOINS
                + FILTERS
                + " ORDER BY "
                + SORT_COLUMNS.get(criteria.sortBy())
                + " "
                + criteria.sortOrder().name()
                + " LIMIT :limit OFFSET :offset";

        Mono<List<UserSummaryView>> content = bindFilters(databaseClient.sql(sql), criteria)
                .bind("limit", criteria.size())
                .bind("offset", (long) criteria.page() * criteria.size())
                .map((row, metadata) -> toView(row))
                .all()
                .collectList();

        return total.zipWith(content)
                .map(tuple -> PageResult.of(tuple.getT2(), criteria.page(), criteria.size(), tuple.getT1()));
    }

    private static GenericExecuteSpec bindFilters(GenericExecuteSpec spec, UserSearchCriteria criteria) {
        String q = criteria.q() == null ? null : "%" + escapeLike(criteria.q().toLowerCase(Locale.ROOT)) + "%";
        String rawLike = criteria.q() == null ? null : "%" + escapeLike(criteria.q()) + "%";
        spec = bindNullable(spec, "q", q, String.class);
        spec = bindNullable(spec, "rawLike", rawLike, String.class);
        spec = bindNullable(
                spec, "role", criteria.role() == null ? null : criteria.role().code(), String.class);
        spec = bindNullable(
                spec,
                "status",
                criteria.status() == null ? null : criteria.status().name(),
                String.class);
        return bindNullable(
                spec,
                "providerStatus",
                criteria.providerStatus() == null
                        ? null
                        : criteria.providerStatus().name(),
                String.class);
    }

    private static UserSummaryView toView(io.r2dbc.spi.Row row) {
        String providerStatus = row.get("provider_status", String.class);
        return new UserSummaryView(
                row.get("id", UUID.class),
                row.get("full_name", String.class),
                row.get("email", String.class),
                row.get("role", String.class),
                UserStatus.valueOf(row.get("status", String.class)),
                providerStatus == null ? null : ProviderRegistrationStatus.valueOf(providerStatus),
                UserPersistenceMapper.toInstant(row.get("signed_up_at", java.time.OffsetDateTime.class)));
    }

    private static GenericExecuteSpec bindNullable(GenericExecuteSpec spec, String name, Object value, Class<?> type) {
        return value == null ? spec.bindNull(name, type) : spec.bind(name, value);
    }

    private static String escapeLike(String value) {
        return value.replace("!", "!!").replace("%", "!%").replace("_", "!_");
    }
}
