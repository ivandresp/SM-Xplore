package com.smxplore.users.infrastructure.adapter.out.persistence.r2dbc;

import com.smxplore.users.application.dto.PageResult;
import com.smxplore.users.application.dto.UserSearchCriteria;
import com.smxplore.users.application.dto.UserSummaryView;
import com.smxplore.users.application.port.out.UserRepository;
import com.smxplore.users.domain.model.Admin;
import com.smxplore.users.domain.model.Email;
import com.smxplore.users.domain.model.GestoraDestinos;
import com.smxplore.users.domain.model.Guia;
import com.smxplore.users.domain.model.PrestadoraServicios;
import com.smxplore.users.domain.model.Role;
import com.smxplore.users.domain.model.Turista;
import com.smxplore.users.domain.model.Usuario;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.r2dbc.core.DatabaseClient.GenericExecuteSpec;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

@Repository
public class UserRepositoryAdapter implements UserRepository {

    private static final String SELECT_USER = """
            SELECT id, full_name, email, phone, password_hash, role, status, auth_version,
                   signed_up_at, updated_at, deleted_at, version
            FROM users
            """;

    private static final String INSERT_USER = """
            INSERT INTO users (id, full_name, email, phone, password_hash, role, status, auth_version,
                               signed_up_at, updated_at, deleted_at, version)
            VALUES (:id, :fullName, :email, :phone, :passwordHash, :role, :status, :authVersion,
                    :signedUpAt, :updatedAt, :deletedAt, 0)
            """;

    private static final String UPDATE_USER = """
            UPDATE users
            SET full_name = :fullName,
                email = :email,
                phone = :phone,
                password_hash = :passwordHash,
                role = :role,
                status = :status,
                auth_version = :authVersion,
                updated_at = :updatedAt,
                deleted_at = :deletedAt,
                version = version + 1
            WHERE id = :id AND version = :version
            """;

    private static final String UPSERT_TOURIST = """
            INSERT INTO tourist_profiles (user_id, dni_type, dni_number, preferred_language, birth_date, nationality)
            VALUES (:userId, :dniType, :dniNumber, :preferredLanguage, :birthDate, :nationality)
            ON CONFLICT (user_id) DO UPDATE
            SET dni_type = EXCLUDED.dni_type,
                dni_number = EXCLUDED.dni_number,
                preferred_language = EXCLUDED.preferred_language,
                birth_date = EXCLUDED.birth_date,
                nationality = EXCLUDED.nationality
            """;

    private static final String UPSERT_GUIDE = """
            INSERT INTO guide_profiles (user_id, dni_type, dni_number, license, start_operation_date)
            VALUES (:userId, :dniType, :dniNumber, :license, :startOperationDate)
            ON CONFLICT (user_id) DO UPDATE
            SET dni_type = EXCLUDED.dni_type,
                dni_number = EXCLUDED.dni_number,
                license = EXCLUDED.license,
                start_operation_date = EXCLUDED.start_operation_date
            """;

    private static final String UPSERT_PROVIDER = """
            INSERT INTO provider_profiles (user_id, nit, business_type, registration_status, latitude,
                                           longitude, website_url, start_operation_date)
            VALUES (:userId, :nit, :businessType, :registrationStatus, :latitude, :longitude, :websiteUrl,
                    :startOperationDate)
            ON CONFLICT (user_id) DO UPDATE
            SET nit = EXCLUDED.nit,
                business_type = EXCLUDED.business_type,
                registration_status = EXCLUDED.registration_status,
                latitude = EXCLUDED.latitude,
                longitude = EXCLUDED.longitude,
                website_url = EXCLUDED.website_url,
                start_operation_date = EXCLUDED.start_operation_date
            """;

    private static final String UPSERT_DESTINATION_MANAGER = """
            INSERT INTO destination_manager_profiles (user_id, nit, start_operation_date)
            VALUES (:userId, :nit, :startOperationDate)
            ON CONFLICT (user_id) DO UPDATE
            SET nit = EXCLUDED.nit,
                start_operation_date = EXCLUDED.start_operation_date
            """;

    private static final String UPSERT_ADMIN = """
            INSERT INTO admin_profiles (user_id, dni_type, dni_number, last_access_date)
            VALUES (:userId, :dniType, :dniNumber, :lastAccessDate)
            ON CONFLICT (user_id) DO UPDATE
            SET dni_type = EXCLUDED.dni_type,
                dni_number = EXCLUDED.dni_number,
                last_access_date = EXCLUDED.last_access_date
            """;

    private final DatabaseClient databaseClient;
    private final UserSummaryQuery summaryQuery;
    private final UserPersistenceMapper mapper = new UserPersistenceMapper();

    public UserRepositoryAdapter(DatabaseClient databaseClient) {
        this.databaseClient = databaseClient;
        this.summaryQuery = new UserSummaryQuery(databaseClient);
    }

    @Override
    public Mono<Usuario> findById(UUID id) {
        return databaseClient
                .sql(SELECT_USER + " WHERE id = :id")
                .bind("id", id)
                .map((row, metadata) -> mapper.toRecord(row))
                .one()
                .flatMap(this::loadProfile);
    }

    @Override
    public Mono<Usuario> findByEmail(Email email) {
        return databaseClient
                .sql(SELECT_USER + " WHERE email = :email")
                .bind("email", email.value())
                .map((row, metadata) -> mapper.toRecord(row))
                .one()
                .flatMap(this::loadProfile);
    }

    @Override
    public Mono<Boolean> existsByEmail(Email email) {
        return databaseClient
                .sql("SELECT count(*) AS total FROM users WHERE email = :email")
                .bind("email", email.value())
                .map((row, metadata) -> row.get("total", Long.class))
                .one()
                .map(total -> total > 0);
    }

    @Override
    public Mono<PageResult<UserSummaryView>> search(UserSearchCriteria criteria) {
        return summaryQuery.search(criteria);
    }

    @Override
    public Mono<Long> countActiveByRole(Role role) {
        return databaseClient
                .sql("SELECT count(*) AS total FROM users WHERE role = :role AND status = 'ACTIVO'")
                .bind("role", role.code())
                .map((row, metadata) -> row.get("total", Long.class))
                .one();
    }

    @Override
    public Mono<Usuario> save(Usuario user) {
        return saveUser(user).flatMap(saved -> saveProfile(saved).thenReturn(saved));
    }

    private Mono<Usuario> saveUser(Usuario user) {
        return databaseClient
                .sql("SELECT version FROM users WHERE id = :id")
                .bind("id", user.id())
                .map((row, metadata) -> row.get("version", Long.class))
                .one()
                .flatMap(version -> updateUser(user, version))
                .switchIfEmpty(insertUser(user));
    }

    private Mono<Usuario> insertUser(Usuario user) {
        GenericExecuteSpec spec = databaseClient
                .sql(INSERT_USER)
                .bind("id", user.id())
                .bind("fullName", user.fullName())
                .bind("email", user.email().value())
                .bind("passwordHash", user.passwordHash().value())
                .bind("role", user.role().code())
                .bind("status", user.status().name())
                .bind("authVersion", user.authVersion())
                .bind("signedUpAt", toOffsetDateTime(user.signedUpAt()))
                .bind("updatedAt", toOffsetDateTime(user.updatedAt()));
        spec = bindNullable(spec, "phone", user.phone(), String.class);
        spec = bindNullable(spec, "deletedAt", toOffsetDateTime(user.deletedAt()), OffsetDateTime.class);
        return spec.fetch().rowsUpdated().thenReturn(user);
    }

    private Mono<Usuario> updateUser(Usuario user, long version) {
        GenericExecuteSpec spec = databaseClient
                .sql(UPDATE_USER)
                .bind("id", user.id())
                .bind("fullName", user.fullName())
                .bind("email", user.email().value())
                .bind("passwordHash", user.passwordHash().value())
                .bind("role", user.role().code())
                .bind("status", user.status().name())
                .bind("authVersion", user.authVersion())
                .bind("updatedAt", toOffsetDateTime(user.updatedAt()))
                .bind("version", version);
        spec = bindNullable(spec, "phone", user.phone(), String.class);
        spec = bindNullable(spec, "deletedAt", toOffsetDateTime(user.deletedAt()), OffsetDateTime.class);
        return spec.fetch()
                .rowsUpdated()
                .flatMap(updated -> updated == 0
                        ? Mono.error(
                                new OptimisticLockingFailureException("El usuario fue modificado por otra operación"))
                        : Mono.just(user));
    }

    private Mono<Void> saveProfile(Usuario user) {
        return switch (user) {
            case Turista tourist -> upsertTourist(tourist);
            case Guia guide -> upsertGuide(guide);
            case PrestadoraServicios provider -> upsertProvider(provider);
            case GestoraDestinos manager -> upsertDestinationManager(manager);
            case Admin admin -> upsertAdmin(admin);
        };
    }

    private Mono<Void> upsertTourist(Turista user) {
        GenericExecuteSpec spec = databaseClient
                .sql(UPSERT_TOURIST)
                .bind("userId", user.id())
                .bind("dniType", user.dniType())
                .bind("dniNumber", user.dniNumber());
        spec = bindNullable(spec, "preferredLanguage", user.preferredLanguage(), String.class);
        spec = bindNullable(spec, "birthDate", user.birthDate(), LocalDate.class);
        spec = bindNullable(spec, "nationality", user.nationality(), String.class);
        return spec.fetch().rowsUpdated().then();
    }

    private Mono<Void> upsertGuide(Guia user) {
        GenericExecuteSpec spec = databaseClient
                .sql(UPSERT_GUIDE)
                .bind("userId", user.id())
                .bind("dniType", user.dniType())
                .bind("dniNumber", user.dniNumber())
                .bind("license", user.license());
        spec = bindNullable(spec, "startOperationDate", user.startOperationDate(), LocalDate.class);
        return spec.fetch().rowsUpdated().then();
    }

    private Mono<Void> upsertProvider(PrestadoraServicios user) {
        GenericExecuteSpec spec = databaseClient
                .sql(UPSERT_PROVIDER)
                .bind("userId", user.id())
                .bind("nit", user.nit())
                .bind("registrationStatus", user.registrationStatus().name());
        spec = bindNullable(spec, "businessType", user.businessType(), String.class);
        spec = bindNullable(spec, "latitude", user.latitude(), BigDecimal.class);
        spec = bindNullable(spec, "longitude", user.longitude(), BigDecimal.class);
        spec = bindNullable(spec, "websiteUrl", user.websiteUrl(), String.class);
        spec = bindNullable(spec, "startOperationDate", user.startOperationDate(), LocalDate.class);
        return spec.fetch().rowsUpdated().then();
    }

    private Mono<Void> upsertDestinationManager(GestoraDestinos user) {
        GenericExecuteSpec spec = databaseClient
                .sql(UPSERT_DESTINATION_MANAGER)
                .bind("userId", user.id())
                .bind("nit", user.nit());
        spec = bindNullable(spec, "startOperationDate", user.startOperationDate(), LocalDate.class);
        return spec.fetch().rowsUpdated().then();
    }

    private Mono<Void> upsertAdmin(Admin user) {
        GenericExecuteSpec spec = databaseClient.sql(UPSERT_ADMIN).bind("userId", user.id());
        spec = bindNullable(spec, "dniType", user.dniType(), String.class);
        spec = bindNullable(spec, "dniNumber", user.dniNumber(), String.class);
        spec = bindNullable(spec, "lastAccessDate", toOffsetDateTime(user.lastAccessDate()), OffsetDateTime.class);
        return spec.fetch().rowsUpdated().then();
    }

    private Mono<Usuario> loadProfile(UserRecord user) {
        return switch (user.role()) {
            case "TURISTA" ->
                databaseClient
                        .sql("SELECT * FROM tourist_profiles WHERE user_id = :id")
                        .bind("id", user.id())
                        .map((row, metadata) -> (Usuario) mapper.toTourist(user, row))
                        .one()
                        .switchIfEmpty(missingProfile("turista", user.id()));
            case "GUIA" ->
                databaseClient
                        .sql("SELECT * FROM guide_profiles WHERE user_id = :id")
                        .bind("id", user.id())
                        .map((row, metadata) -> (Usuario) mapper.toGuide(user, row))
                        .one()
                        .switchIfEmpty(missingProfile("guía", user.id()));
            case "PRESTADORA_SERVICIOS" ->
                databaseClient
                        .sql("SELECT * FROM provider_profiles WHERE user_id = :id")
                        .bind("id", user.id())
                        .map((row, metadata) -> (Usuario) mapper.toProvider(user, row))
                        .one()
                        .switchIfEmpty(missingProfile("prestadora", user.id()));
            case "GESTORA_DESTINOS" ->
                databaseClient
                        .sql("SELECT * FROM destination_manager_profiles WHERE user_id = :id")
                        .bind("id", user.id())
                        .map((row, metadata) -> (Usuario) mapper.toDestinationManager(user, row))
                        .one()
                        .switchIfEmpty(missingProfile("gestora", user.id()));
            case "ADMIN" ->
                databaseClient
                        .sql("SELECT * FROM admin_profiles WHERE user_id = :id")
                        .bind("id", user.id())
                        .map((row, metadata) -> (Usuario) mapper.toAdmin(user, row))
                        .one()
                        .switchIfEmpty(missingProfile("administrador", user.id()));
            default -> Mono.error(new IllegalStateException("Rol no soportado en la base de datos: " + user.role()));
        };
    }

    private static GenericExecuteSpec bindNullable(GenericExecuteSpec spec, String name, Object value, Class<?> type) {
        return value == null ? spec.bindNull(name, type) : spec.bind(name, value);
    }

    private static OffsetDateTime toOffsetDateTime(Instant instant) {
        return instant == null ? null : OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    private static <T> Mono<T> missingProfile(String role, UUID userId) {
        return Mono.error(
                new IllegalStateException("No se encontró el perfil de " + role + " para el usuario " + userId));
    }
}
