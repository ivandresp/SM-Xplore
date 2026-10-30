package com.smxplore.users.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.smxplore.users.domain.exception.ValidationException;
import com.smxplore.users.domain.model.Email;
import com.smxplore.users.domain.model.PasswordHash;
import com.smxplore.users.domain.model.PrestadoraServicios;
import com.smxplore.users.domain.model.ProviderRegistrationStatus;
import com.smxplore.users.domain.model.Role;
import com.smxplore.users.domain.model.RoleProfileData;
import com.smxplore.users.domain.model.Turista;
import com.smxplore.users.domain.model.UserStatus;
import com.smxplore.users.domain.model.Usuario;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RoleMigrationTest {

    private static final Instant NOW = Instant.parse("2026-10-25T12:00:00Z");

    @Test
    void migratesTouristToProviderAndKeepsCommonData() {
        Turista tourist = tourist();

        Usuario migrated = RoleMigration.changeRole(tourist, Role.SERVICE_PROVIDER, providerData(), NOW);

        assertThat(migrated).isInstanceOf(PrestadoraServicios.class);
        PrestadoraServicios provider = (PrestadoraServicios) migrated;
        assertThat(provider.id()).isEqualTo(tourist.id());
        assertThat(provider.email()).isEqualTo(tourist.email());
        assertThat(provider.status()).isEqualTo(UserStatus.ACTIVO);
        assertThat(provider.authVersion()).isEqualTo(tourist.authVersion() + 1);
        assertThat(provider.nit()).isEqualTo("900123456");
        assertThat(provider.websiteUrl()).isEqualTo("https://prestadora.example.com");
        assertThat(provider.registrationStatus()).isEqualTo(ProviderRegistrationStatus.PENDIENTE);
    }

    @Test
    void migratesProviderToAdmin() {
        PrestadoraServicios provider =
                (PrestadoraServicios) RoleMigration.changeRole(tourist(), Role.SERVICE_PROVIDER, providerData(), NOW);

        Usuario migrated = RoleMigration.changeRole(
                provider,
                Role.ADMIN,
                data("CC", "123456", null, null, null, null, null, null, null, null, null, null),
                NOW);

        assertThat(migrated.role()).isEqualTo(Role.ADMIN);
        assertThat(migrated.authVersion()).isEqualTo(2);
        assertThat(((com.smxplore.users.domain.model.Admin) migrated).dniNumber())
                .isEqualTo("123456");
    }

    @Test
    void requiresRoleSpecificProfileData() {
        assertThatThrownBy(() -> RoleMigration.changeRole(
                        tourist(),
                        Role.GUIDE,
                        data("CC", "123456", null, null, null, null, null, null, null, null, null, null),
                        NOW))
                .isInstanceOf(ValidationException.class);
    }

    private static Turista tourist() {
        return Turista.register(
                UUID.randomUUID(),
                "Ada Turista",
                new Email("ada@example.com"),
                "3001234567",
                new PasswordHash("hash"),
                "CC",
                "1234567890",
                "es",
                null,
                "Colombia",
                NOW);
    }

    private static RoleProfileData providerData() {
        return data(
                null,
                null,
                null,
                null,
                null,
                null,
                "900123456",
                "Hotel",
                null,
                null,
                "https://prestadora.example.com",
                null);
    }

    private static RoleProfileData data(
            String dniType,
            String dniNumber,
            String preferredLanguage,
            java.time.LocalDate birthDate,
            String nationality,
            String license,
            String nit,
            String businessType,
            java.math.BigDecimal latitude,
            java.math.BigDecimal longitude,
            String websiteUrl,
            java.time.LocalDate startOperationDate) {
        return new RoleProfileData(
                dniType,
                dniNumber,
                preferredLanguage,
                birthDate,
                nationality,
                license,
                nit,
                businessType,
                latitude,
                longitude,
                websiteUrl,
                startOperationDate);
    }
}
