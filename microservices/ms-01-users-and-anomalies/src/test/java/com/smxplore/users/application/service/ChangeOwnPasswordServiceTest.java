package com.smxplore.users.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.smxplore.users.application.dto.ChangePasswordCommand;
import com.smxplore.users.domain.model.Email;
import com.smxplore.users.domain.model.PasswordHash;
import com.smxplore.users.domain.model.Turista;
import com.smxplore.users.support.FakeAuthVersionEvictor;
import com.smxplore.users.support.FakePasswordHasher;
import com.smxplore.users.support.FakePasswordResetTokenRepository;
import com.smxplore.users.support.FakeTransactionPort;
import com.smxplore.users.support.FakeUserRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

/** Regresión de MS01-15: cambiar la contraseña invalida la caché de versión de autenticación de inmediato. */
class ChangeOwnPasswordServiceTest {

    private static final Instant NOW = Instant.parse("2026-01-01T10:00:00Z");

    private final FakeUserRepository users = new FakeUserRepository();
    private final FakePasswordHasher passwordHasher = new FakePasswordHasher();
    private final FakePasswordResetTokenRepository resetTokens = new FakePasswordResetTokenRepository();
    private final FakeAuthVersionEvictor evictor = new FakeAuthVersionEvictor();
    private final ChangeOwnPasswordService service = new ChangeOwnPasswordService(
            users, resetTokens, passwordHasher, evictor, new FakeTransactionPort(), Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void changingThePasswordBumpsTheAuthVersionAndEvictsTheCachedVersion() {
        UUID userId = UUID.randomUUID();
        users.seed(Turista.register(
                userId,
                "Ada Turista",
                new Email("ada@example.com"),
                "3001234567",
                new PasswordHash("hash:actual"),
                "CC",
                "1234567890",
                "es",
                LocalDate.of(1990, 1, 1),
                "Colombia",
                NOW));

        StepVerifier.create(service.change(userId, new ChangePasswordCommand("actual", "NuevaClave123!")))
                .verifyComplete();

        assertThat(users.lastSaved().authVersion()).isEqualTo(1);
        assertThat(evictor.evicted()).containsExactly(userId);
    }
}
