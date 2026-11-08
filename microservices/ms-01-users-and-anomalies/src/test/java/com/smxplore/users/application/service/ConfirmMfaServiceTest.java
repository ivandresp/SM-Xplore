package com.smxplore.users.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.smxplore.users.domain.exception.InvalidCurrentPasswordException;
import com.smxplore.users.domain.exception.InvalidMfaCodeException;
import com.smxplore.users.domain.model.AuditAction;
import com.smxplore.users.domain.model.Email;
import com.smxplore.users.domain.model.MfaEnrollment;
import com.smxplore.users.domain.model.PasswordHash;
import com.smxplore.users.domain.model.Turista;
import com.smxplore.users.domain.model.Usuario;
import com.smxplore.users.support.FakeAuditLogRepository;
import com.smxplore.users.support.FakeAuthVersionEvictor;
import com.smxplore.users.support.FakeMfaEnabledNotifier;
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

class ConfirmMfaServiceTest {

    private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final Instant NOW = Instant.parse("2026-01-01T10:00:00Z");

    private final FakeUserRepository users = new FakeUserRepository().seed(turista());
    private final FakeMfaRepository mfa =
            new FakeMfaRepository().seed(MfaEnrollment.pending(USER_ID, "JBSWY3DPEHPK3PXP", NOW));
    private final FakePasswordHasher passwordHasher = new FakePasswordHasher();
    private final FakeTotpAuthenticator totp = new FakeTotpAuthenticator();
    private final FakeAuditLogRepository auditLog = new FakeAuditLogRepository();
    private final FakeMfaEnabledNotifier notifier = new FakeMfaEnabledNotifier();
    private final FakeAuthVersionEvictor evictor = new FakeAuthVersionEvictor();
    private final ConfirmMfaService service = new ConfirmMfaService(
            users,
            passwordHasher,
            mfa,
            totp,
            new FakeTransactionPort(),
            new AuditTrailRecorder(users, auditLog),
            notifier,
            evictor,
            Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void requiresTheCurrentPasswordBeforeConfirming() {
        passwordHasher.matches(false);

        StepVerifier.create(service.confirm(USER_ID, "123456", "contrasena-incorrecta"))
                .expectError(InvalidCurrentPasswordException.class)
                .verify();

        assertThat(mfa.enrollment().enabled()).isFalse();
        assertThat(users.saved()).isEmpty();
    }

    @Test
    void rejectsAnInvalidTotpCodeWithoutEnablingMfa() {
        StepVerifier.create(service.confirm(USER_ID, "000000", "Correcta123456"))
                .expectError(InvalidMfaCodeException.class)
                .verify();

        assertThat(mfa.enrollment().enabled()).isFalse();
    }

    @Test
    void confirmsMfaRevokesSessionsAuditsAndNotifiesTheUser() {
        StepVerifier.create(service.confirm(USER_ID, "123456", "Correcta123456"))
                .verifyComplete();

        assertThat(mfa.enrollment().enabled()).isTrue();
        assertThat(users.lastSaved().authVersion()).isEqualTo(1);
        assertThat(auditLog.saved()).hasSize(1);
        assertThat(auditLog.saved().get(0).action()).isEqualTo(AuditAction.MFA_ACTIVADO);
        assertThat(auditLog.saved().get(0).details()).containsEntry("sesionesRevocadas", "true");
        assertThat(evictor.evicted()).containsExactly(USER_ID);
        assertThat(notifier.notified()).hasSize(1);
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
