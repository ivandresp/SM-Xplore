package com.smxplore.users.identity.infrastructure.adapter.out.persistence.jpa;

import com.smxplore.users.identity.application.dto.PageResult;
import com.smxplore.users.identity.application.dto.UserSearchCriteria;
import com.smxplore.users.identity.application.dto.UserSummaryView;
import com.smxplore.users.identity.domain.model.ProviderRegistrationStatus;
import com.smxplore.users.identity.domain.model.Role;
import com.smxplore.users.identity.domain.model.UserStatus;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import jakarta.persistence.Tuple;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Consulta de lectura del catálogo administrativo de usuarios.
 *
 * <p>Se usa SQL nativo porque el filtro por identificación (DNI/NIT) requiere unir las cinco tablas de perfil y
 * el modelo de dominio no expone esas relaciones como asociaciones JPA. La proyección es plana para evitar
 * cargar cada perfil por separado.
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

    private final EntityManager entityManager;

    UserSummaryQuery(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    PageResult<UserSummaryView> search(UserSearchCriteria criteria) {
        long totalElements = count(criteria);
        if (totalElements == 0) {
            return PageResult.of(List.of(), criteria.page(), criteria.size(), 0);
        }

        String sql = SELECT
                + FROM_AND_JOINS
                + FILTERS
                + " ORDER BY "
                + SORT_COLUMNS.get(criteria.sortBy())
                + " "
                + criteria.sortOrder().name()
                + " LIMIT :limit OFFSET :offset";

        Query query = entityManager.createNativeQuery(sql, Tuple.class);
        bindFilters(query, criteria);
        query.setParameter("limit", criteria.size());
        query.setParameter("offset", (long) criteria.page() * criteria.size());

        @SuppressWarnings("unchecked")
        List<Tuple> rows = query.getResultList();
        List<UserSummaryView> content =
                rows.stream().map(UserSummaryQuery::toView).toList();
        return PageResult.of(content, criteria.page(), criteria.size(), totalElements);
    }

    private long count(UserSearchCriteria criteria) {
        Query query = entityManager.createNativeQuery("SELECT count(*) " + FROM_AND_JOINS + FILTERS);
        bindFilters(query, criteria);
        return ((Number) query.getSingleResult()).longValue();
    }

    private static void bindFilters(Query query, UserSearchCriteria criteria) {
        query.setParameter(
                "q", criteria.q() == null ? null : "%" + escapeLike(criteria.q().toLowerCase(Locale.ROOT)) + "%");
        query.setParameter("rawLike", criteria.q() == null ? null : "%" + escapeLike(criteria.q()) + "%");
        query.setParameter(
                "role", criteria.role() == null ? null : criteria.role().name());
        query.setParameter(
                "status", criteria.status() == null ? null : criteria.status().name());
        query.setParameter(
                "providerStatus",
                criteria.providerStatus() == null
                        ? null
                        : criteria.providerStatus().name());
    }

    private static UserSummaryView toView(Tuple row) {
        String providerStatus = row.get("provider_status", String.class);
        return new UserSummaryView(
                row.get("id", UUID.class),
                row.get("full_name", String.class),
                row.get("email", String.class),
                Role.valueOf(row.get("role", String.class)),
                UserStatus.valueOf(row.get("status", String.class)),
                providerStatus == null ? null : ProviderRegistrationStatus.valueOf(providerStatus),
                toInstant(row.get("signed_up_at")));
    }

    private static Instant toInstant(Object value) {
        return switch (value) {
            case null -> null;
            case Instant instant -> instant;
            case OffsetDateTime offsetDateTime -> offsetDateTime.toInstant();
            case java.sql.Timestamp timestamp -> timestamp.toInstant();
            default ->
                throw new IllegalStateException("Tipo de fecha no soportado en la consulta de usuarios: "
                        + value.getClass().getName());
        };
    }

    private static String escapeLike(String value) {
        return value.replace("!", "!!").replace("%", "!%").replace("_", "!_");
    }
}
