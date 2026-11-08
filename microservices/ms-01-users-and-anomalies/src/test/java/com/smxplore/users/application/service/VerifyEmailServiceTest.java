package com.smxplore.users.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.smxplore.users.application.port.out.TokenHasher;
import com.smxplore.users.domain.exception.InvalidEmailVerificationTokenException;
import com.smxplore.users.domain.model.AuditAction;
import com.smxplore.users.domain.model.Email;
import com.smxplore.users.domain.model.PasswordHash;
import com.smxplore.users.domain.model.PasswordResetToken;
import com.smxplore.users.domain.model.TokenPurpose;
import com.smxplore.users.domain.model.Turista;
import com.smxplore.users.infrastructure.adapter.out.security.Sha256TokenHasher;
import com.smxplore.users.support.FakeAuditLogRepository;
import com.smxplore.users.support.FakeEmailVerificationRepository;
import com.smxplore.users.support.FakePasswordResetTokenRepository;
import com.smxplore.users.support.FakeTransactionPort;
import com.smxplore.users.support.FakeUserRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

/** Regresión de MS01-18: la verificación de correo consume un token de un solo uso y deja rastro de auditoría. */
class VerifyEmailServiceTest {

    private static final Instant NOW = Instant.parse("2026-01-01T10:00:00Z");
    private static final String RAW_TOKEN = "token-de-verificacion-de-prueba";

    private final FakeUserRepository users = new FakeUserRepository();
    private final FakePasswordResetTokenRepository tokens = new FakePasswordResetTokenRepository();
    private final FakeEmailVerificationRepository verifications = new FakeEmailVerificationRepository();
    private final FakeAuditLogRepository auditLog = new FakeAuditLogRepository();
    private final TokenHasher tokenHasher = new Sha256TokenHasher();
    private final VerifyEmailService service = new VerifyEmailService(
            users,
            tokens,
            verifications,
            tokenHasher,
            new AuditTrailRecorder(users, auditLog),
            new FakeTransactionPort(),
            Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void verifiesTheEmailAndConsumesTheToken() {
        Turista user = seedTourist();
        tokens.seed(verificationToken(user.id(), RAW_TOKEN, NOW.plus(Duration.ofHours(24))));

        StepVerifier.create(service.verify(RAW_TOKEN)).verifyComplete();

        assertThat(verifications.markedVerified()).containsExactly(user.id());
        assertThat(tokens.saved())
                .singleElement()
                .satisfies(token -> assertThat(token.usedAt()).isEqualTo(NOW));
        assertThat(auditLog.saved()).singleElement().satisfies(entry -> {
            assertThat(entry.action()).isEqualTo(AuditAction.CORREO_VERIFICADO);
            assertThat(entry.targetUserId()).isEqualTo(user.id());
        });
    }

    @Test
    void rejectsATokenThatWasAlreadyUsed() {
        Turista user = seedTourist();
        PasswordResetToken used = verificationToken(user.id(), RAW_TOKEN, NOW.plus(Duration.ofHours(24)))
                .markUsed(NOW);
        tokens.seed(used);

        StepVerifier.create(service.verify(RAW_TOKEN))
                .expectError(InvalidEmailVerificationTokenException.class)
                .verify();

        assertThat(verifications.markedVerified()).isEmpty();
    }

    @Test
    void rejectsATokenWithAnotherPurpose() {
        Turista user = seedTourist();
        tokens.seed(PasswordResetToken.create(
                user.id(),
                tokenHasher.hash(RAW_TOKEN),
                NOW.minus(Duration.ofMinutes(1)),
                Duration.ofHours(1),
                TokenPurpose.PASSWORD_RESET));

        StepVerifier.create(service.verify(RAW_TOKEN))
                .expectError(InvalidEmailVerificationTokenException.class)
                .verify();
    }

    @Test
    void rejectsABlankToken() {
        StepVerifier.create(service.verify(" "))
                .expectError(InvalidEmailVerificationTokenException.class)
                .verify();
    }

    private Turista seedTourist() {
        Turista tourist = Turista.register(
                UUID.randomUUID(),
                "Ada Turista",
                new Email("ada@example.com"),
                "3001234567",
                new PasswordHash("hash:secreto"),
                "CC",
                "1234567890",
                "es",
                LocalDate.of(1990, 1, 1),
                "Colombia",
                NOW);
        users.seed(tourist);
        return tourist;
    }

    private PasswordResetToken verificationToken(UUID userId, String rawToken, Instant expiresAt) {
        return PasswordResetToken.create(
                userId,
                tokenHasher.hash(rawToken),
                NOW.minus(Duration.ofMinutes(1)),
                Duration.ofHours(24),
                TokenPurpose.EMAIL_VERIFICATION);
    }
}
