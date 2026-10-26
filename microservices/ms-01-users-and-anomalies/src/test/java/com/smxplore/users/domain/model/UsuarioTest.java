package com.smxplore.users.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.smxplore.users.domain.exception.ValidationException;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class UsuarioTest {

    private static final Instant NOW = Instant.parse("2026-10-24T12:00:00Z");

    @Test
    void registersTouristAsActive() {
        Usuario user = tourist();

        assertThat(user.role()).isEqualTo(Role.TOURIST);
        assertThat(user.status()).isEqualTo(UserStatus.ACTIVO);
        assertThat(user.authVersion()).isZero();
        assertThat(user).isInstanceOf(Turista.class);
    }

    @Test
    void registersProviderAsPending() {
        PrestadoraServicios user = PrestadoraServicios.register(
                UUID.randomUUID(),
                "Prestadora Demo",
                new Email("provider@example.com"),
                "3001234567",
                new PasswordHash("hash"),
                "900123456",
                "Hotel",
                null,
                null,
                null,
                null,
                NOW);

        assertThat(user).isInstanceOf(Usuario.class);
        assertThat(user.role()).isEqualTo(Role.SERVICE_PROVIDER);
        assertThat(user.registrationStatus()).isEqualTo(ProviderRegistrationStatus.PENDIENTE);
    }

    @Test
    void keepsAndNormalizesTheOptionalProviderWebsite() {
        PrestadoraServicios user = provider("  https://prestadora.example.com  ");

        assertThat(user.websiteUrl()).isEqualTo("https://prestadora.example.com");
    }

    @Test
    void rejectsAProviderWebsiteThatIsNotHttp() {
        assertThatThrownBy(() -> provider("ftp://prestadora.example.com")).isInstanceOf(ValidationException.class);
    }

    @Test
    void clearsTheProviderWebsiteWhenAnEmptyValueIsSent() {
        PrestadoraServicios user = provider("https://prestadora.example.com");

        user.updateBusinessDetails(null, null, null, "", NOW);

        assertThat(user.websiteUrl()).isNull();
    }

    @Test
    void changePasswordIncrementsAuthVersion() {
        Usuario user = tourist();

        user.changePassword(new PasswordHash("new-hash"), NOW);

        assertThat(user.authVersion()).isEqualTo(1);
    }

    @Test
    void roleSubtypesPreserveTheirTypeWhenChangingCommonData() {
        Usuario user = tourist();

        user.updateContact("Ada Actualizada", "3000000000", NOW);

        assertThat(user).isInstanceOf(Turista.class);
        assertThat(user.fullName()).isEqualTo("Ada Actualizada");
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

    private static PrestadoraServicios provider(String websiteUrl) {
        return PrestadoraServicios.register(
                UUID.randomUUID(),
                "Prestadora Demo",
                new Email("provider@example.com"),
                "3001234567",
                new PasswordHash("hash"),
                "900123456",
                "Hotel",
                null,
                null,
                websiteUrl,
                null,
                NOW);
    }
}
