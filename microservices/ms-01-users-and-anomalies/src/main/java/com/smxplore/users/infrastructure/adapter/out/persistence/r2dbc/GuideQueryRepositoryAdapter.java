package com.smxplore.users.infrastructure.adapter.out.persistence.r2dbc;

import com.smxplore.users.application.dto.GuideSearchCriteria;
import com.smxplore.users.application.dto.GuideSummaryView;
import com.smxplore.users.application.dto.PageResult;
import com.smxplore.users.application.port.out.GuideQueryRepository;
import com.smxplore.users.domain.model.RegistrationStatus;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

/**
 * Consulta de lectura del listado de guías para revisión. Se excluyen las cuentas eliminadas y se ordena por antigüedad
 * de registro para que las solicitudes pendientes más antiguas se atiendan primero.
 */
@Repository
public class GuideQueryRepositoryAdapter implements GuideQueryRepository {

    private static final String FROM = """
            FROM guide_profiles g
            JOIN users u ON u.id = g.user_id
            WHERE g.registration_status = :registrationStatus
              AND u.status <> 'ELIMINADO'
            """;

    private static final String SELECT = """
            SELECT u.id AS id,
                   u.full_name AS full_name,
                   u.email AS email,
                   u.phone AS phone,
                   u.signed_up_at AS signed_up_at,
                   g.dni_type AS dni_type,
                   g.dni_number AS dni_number,
                   g.license AS license,
                   g.start_operation_date AS start_operation_date,
                   g.registration_status AS registration_status
            """ + FROM;

    private final DatabaseClient databaseClient;

    public GuideQueryRepositoryAdapter(DatabaseClient databaseClient) {
        this.databaseClient = databaseClient;
    }

    @Override
    public Mono<PageResult<GuideSummaryView>> search(GuideSearchCriteria criteria) {
        Mono<Long> total = databaseClient
                .sql("SELECT count(*) AS total " + FROM)
                .bind("registrationStatus", criteria.registrationStatus().name())
                .map((row, metadata) -> {
                    Long value = row.get("total", Long.class);
                    return value == null ? 0L : value;
                })
                .one();

        Mono<List<GuideSummaryView>> content = databaseClient
                .sql(SELECT + " ORDER BY u.signed_up_at ASC, u.id ASC LIMIT :limit OFFSET :offset")
                .bind("registrationStatus", criteria.registrationStatus().name())
                .bind("limit", criteria.size())
                .bind("offset", (long) criteria.page() * criteria.size())
                .map((row, metadata) -> toView(row))
                .all()
                .collectList();

        return total.zipWith(content)
                .map(tuple -> PageResult.of(tuple.getT2(), criteria.page(), criteria.size(), tuple.getT1()));
    }

    private static GuideSummaryView toView(io.r2dbc.spi.Row row) {
        return new GuideSummaryView(
                row.get("id", java.util.UUID.class),
                row.get("full_name", String.class),
                row.get("email", String.class),
                row.get("phone", String.class),
                row.get("dni_type", String.class),
                row.get("dni_number", String.class),
                row.get("license", String.class),
                row.get("start_operation_date", java.time.LocalDate.class),
                RegistrationStatus.valueOf(row.get("registration_status", String.class)),
                UserPersistenceMapper.toInstant(row.get("signed_up_at", OffsetDateTime.class)));
    }
}
