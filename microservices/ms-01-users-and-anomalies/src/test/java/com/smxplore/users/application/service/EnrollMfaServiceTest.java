package com.smxplore.users.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.smxplore.users.domain.exception.InvalidCurrentPasswordException;
import com.smxplore.users.domain.exception.MfaAlreadyEnabledException;
import com.smxplore.users.domain.model.Email;
import com.smxplore.users.domain.model.MfaEnrollment;
import com.smxplore.users.domain.model.PasswordHash;
import com.smxplore.users.domain.model.Turista;
import com.smxplore.users.domain.model.Usuario;
import com.smxplore.users.support.FakeMfaRepository;
import com.smxplore.users.support.FakePasswordHasher;
import com.smxplore.users.support.FakeTotpAuthenticator;
import com.smxplore.users.support.FakeTransactionPort;
import com.smxplore.users.support.FakeUserRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

class EnrollMfaServiceTest {

    private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final Instant NOW = Instant.parse("2026-01-01T10:00:00Z");

    private final FakeUserRepository users = new FakeUserRepository().seed(turista());
    private final FakeMfaRepository mfa = new FakeMfaRepository();
    private final FakePasswordHasher passwordHasher = new FakePasswordHasher();
    private final FakeTotpAuthenticator totp = new FakeTotpAuthenticator();
    private final EnrollMfaService service = new EnrollMfaService(
            users,
            passwordHasher,
            mfa,
            totp,
            () -> "ABCD-EFGH",
            raw -> "hash:" + raw,
            new FakeTransactionPort(),
            Clock.fixed(NOW, ZoneOffset.UTC),
            "SM-Xplore");

    @Test
    void requiresTheCurrentPasswordBeforeEnrolling() {
        passwordHasher.matches(false);

        StepVerifier.create(service.enroll(USER_ID, "contrasena-incorrecta"))
                .expectError(InvalidCurrentPasswordException.class)
                .verify();

        assertThat(mfa.enrollment()).isNull();
    }

    @Test
    void returnsTheSecretAndRecoveryCodesWhenThePasswordIsCorrect() {
        StepVerifier.create(service.enroll(USER_ID, "Correcta123456"))
                .assertNext(view -> {
                    assertThat(view.secret()).isEqualTo("JBSWY3DPEHPK3PXP");
                    assertThat(view.otpauthUri()).startsWith("otpauth://totp/SM-Xplore:");
                    assertThat(view.recoveryCodes()).hasSize(10);
                })
                .verifyComplete();

        assertThat(mfa.enrollment()).isNotNull();
        assertThat(mfa.enrollment().isPending()).isTrue();
        assertThat(mfa.recoveryHashes()).hasSize(10);
    }

    @Test
    void rejectsASecondEnrollmentWhenMfaIsAlreadyEnabled() {
        mfa.seed(MfaEnrollment.pending(USER_ID, "JBSWY3DPEHPK3PXP", NOW).confirm(NOW));

        StepVerifier.create(service.enroll(USER_ID, "Correcta123456"))
                .expectError(MfaAlreadyEnabledException.class)
                .verify();
    }

    private static Usuario turista() {
        return Turista.register(
                USER_ID,
                "Ada Turista",
                new Email("ada@example.com"),
                "3001234567",
                new PasswordHash("hash-de-prueba"),
                "CC",
                "1234567890",
                "es",
                LocalDate.of(1990, 1, 1),
                "Colombia",
                NOW);
    }
}
