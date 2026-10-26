package com.smxplore.users.infrastructure.adapter.out.persistence.r2dbc;

import com.smxplore.users.domain.model.Admin;
import com.smxplore.users.domain.model.Email;
import com.smxplore.users.domain.model.GestoraDestinos;
import com.smxplore.users.domain.model.Guia;
import com.smxplore.users.domain.model.PasswordHash;
import com.smxplore.users.domain.model.PrestadoraServicios;
import com.smxplore.users.domain.model.ProviderRegistrationStatus;
import com.smxplore.users.domain.model.Turista;
import com.smxplore.users.domain.model.UserStatus;
import io.r2dbc.spi.Row;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;

final class UserPersistenceMapper {

    UserRecord toRecord(Row row) {
        return new UserRecord(
                row.get("id", java.util.UUID.class),
                row.get("full_name", String.class),
                row.get("email", String.class),
                row.get("phone", String.class),
                row.get("password_hash", String.class),
                row.get("role", String.class),
                row.get("status", String.class),
                valueOrDefault(row.get("auth_version", Long.class), 0L),
                row.get("signed_up_at", OffsetDateTime.class),
                row.get("updated_at", OffsetDateTime.class),
                row.get("deleted_at", OffsetDateTime.class),
                valueOrDefault(row.get("version", Long.class), 0L));
    }

    Turista toTourist(UserRecord user, Row profile) {
        return Turista.rehydrate(
                user.id(),
                user.fullName(),
                new Email(user.email()),
                user.phone(),
                new PasswordHash(user.passwordHash()),
                UserStatus.valueOf(user.status()),
                user.authVersion(),
                toInstant(user.signedUpAt()),
                toInstant(user.updatedAt()),
                toInstant(user.deletedAt()),
                profile.get("dni_type", String.class),
                profile.get("dni_number", String.class),
                profile.get("preferred_language", String.class),
                profile.get("birth_date", LocalDate.class),
                profile.get("nationality", String.class));
    }

    Guia toGuide(UserRecord user, Row profile) {
        return Guia.rehydrate(
                user.id(),
                user.fullName(),
                new Email(user.email()),
                user.phone(),
                new PasswordHash(user.passwordHash()),
                UserStatus.valueOf(user.status()),
                user.authVersion(),
                toInstant(user.signedUpAt()),
                toInstant(user.updatedAt()),
                toInstant(user.deletedAt()),
                profile.get("dni_type", String.class),
                profile.get("dni_number", String.class),
                profile.get("license", String.class),
                profile.get("start_operation_date", LocalDate.class));
    }

    PrestadoraServicios toProvider(UserRecord user, Row profile) {
        return PrestadoraServicios.rehydrate(
                user.id(),
                user.fullName(),
                new Email(user.email()),
                user.phone(),
                new PasswordHash(user.passwordHash()),
                UserStatus.valueOf(user.status()),
                user.authVersion(),
                toInstant(user.signedUpAt()),
                toInstant(user.updatedAt()),
                toInstant(user.deletedAt()),
                profile.get("nit", String.class),
                profile.get("business_type", String.class),
                ProviderRegistrationStatus.valueOf(profile.get("registration_status", String.class)),
                profile.get("latitude", BigDecimal.class),
                profile.get("longitude", BigDecimal.class),
                profile.get("start_operation_date", LocalDate.class));
    }

    GestoraDestinos toDestinationManager(UserRecord user, Row profile) {
        return GestoraDestinos.rehydrate(
                user.id(),
                user.fullName(),
                new Email(user.email()),
                user.phone(),
                new PasswordHash(user.passwordHash()),
                UserStatus.valueOf(user.status()),
                user.authVersion(),
                toInstant(user.signedUpAt()),
                toInstant(user.updatedAt()),
                toInstant(user.deletedAt()),
                profile.get("nit", String.class),
                profile.get("start_operation_date", LocalDate.class));
    }

    Admin toAdmin(UserRecord user, Row profile) {
        return Admin.rehydrate(
                user.id(),
                user.fullName(),
                new Email(user.email()),
                user.phone(),
                new PasswordHash(user.passwordHash()),
                UserStatus.valueOf(user.status()),
                user.authVersion(),
                toInstant(user.signedUpAt()),
                toInstant(user.updatedAt()),
                toInstant(user.deletedAt()),
                profile.get("dni_type", String.class),
                profile.get("dni_number", String.class),
                toInstant(profile.get("last_access_date", OffsetDateTime.class)));
    }

    static Instant toInstant(OffsetDateTime value) {
        return value == null ? null : value.toInstant();
    }

    private static long valueOrDefault(Long value, long defaultValue) {
        return value == null ? defaultValue : value;
    }
}
