package com.smxplore.users.domain.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.smxplore.users.domain.model.Email;
import com.smxplore.users.domain.model.PasswordHash;
import com.smxplore.users.domain.model.PrestadoraServicios;
import com.smxplore.users.domain.model.UserStatus;
import com.smxplore.users.domain.model.Usuario;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class UserAnonymizerTest {

    private static final Instant NOW = Instant.parse("2026-10-25T12:00:00Z");

    @Test
    void removesProviderPiiAndBlocksTheAccount() {
        PrestadoraServicios provider = PrestadoraServicios.register(
                UUID.randomUUID(),
                "Prestadora Real",
                new Email("real@example.com"),
                "3001234567",
                new PasswordHash("hash"),
                "900123456",
                "Hotel",
                null,
                null,
                "https://prestadora.example.com",
                null,
                NOW);

        Usuario anonymized = UserAnonymizer.anonymize(
                provider,
                new Email("eliminado+" + provider.id() + "@anonimo.smxplore.local"),
                new PasswordHash("unusable"),
                NOW);

        assertThat(anonymized).isInstanceOf(PrestadoraServicios.class);
        PrestadoraServicios result = (PrestadoraServicios) anonymized;
        assertThat(result.status()).isEqualTo(UserStatus.ELIMINADO);
        assertThat(result.fullName()).isEqualTo("Usuario eliminado");
        assertThat(result.email().value()).startsWith("eliminado+");
        assertThat(result.phone()).isNull();
        assertThat(result.authVersion()).isEqualTo(provider.authVersion() + 1);
        assertThat(result.nit()).startsWith("ANON-");
        assertThat(result.websiteUrl()).isNull();
        assertThat(result.businessType()).isNull();
    }
}
