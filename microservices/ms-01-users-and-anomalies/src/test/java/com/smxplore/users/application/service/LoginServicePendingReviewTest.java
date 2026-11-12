package com.smxplore.users.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.smxplore.users.application.dto.LoginCommand;
import com.smxplore.users.application.port.out.FailedLoginSignal;
import com.smxplore.users.application.port.out.MfaChallengeIssuer;
import com.smxplore.users.application.port.out.TokenIssuer;
import com.smxplore.users.domain.exception.AccountPendingReviewException;
import com.smxplore.users.domain.exception.AccountRegistrationRejectedException;
import com.smxplore.users.domain.exception.InvalidCredentialsException;
import com.smxplore.users.domain.model.Email;
import com.smxplore.users.domain.model.Guia;
import com.smxplore.users.domain.model.PasswordHash;
import com.smxplore.users.domain.model.RegistrationStatus;
import com.smxplore.users.domain.model.UserStatus;
import com.smxplore.users.domain.policy.LoginLockoutPolicy;
import com.smxplore.users.support.FakeAccountLockedNotifier;
import com.smxplore.users.support.FakeLoginAttemptRepository;
import com.smxplore.users.support.FakeMfaRepository;
import com.smxplore.users.support.FakePasswordHasher;
import com.smxplore.users.support.FakeUserRepository;
import com.smxplore.users.support.RecordingAccountAttemptLimiter;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * Un guía sin aprobar no puede iniciar sesión, pero la rama que lo delata se evalúa solo después de verificar la
 * contraseña: así el 403 no se convierte en un oráculo de existencia de cuentas.
 */
class LoginServicePendingReviewTest {

    private static final Instant NOW = Instant.parse("2026-01-01T10:00:00Z");
    private static final String EMAIL = "guia@example.com";
    private static final String PASSWORD = "secreto";

    private final FakeUserRepository users = new FakeUserRepository();
    private final RecordingAccountAttemptLimiter accountLimiter = new RecordingAccountAttemptLimiter(5);

    @Test
    void aPendingGuideWithTheRightPasswordReceivesPendingReview() {
        users.seed(guia(RegistrationStatus.PENDIENTE));

        StepVerifier.create(service(true).login(new LoginCommand(EMAIL, PASSWORD)))
                .expectErrorSatisfies(error -> assertThat(error)
                        .isInstanceOf(AccountPendingReviewException.class)
                        .hasMessageContaining("revisión"))
                .verify();
    }

    @Test
    void aPendingGuideWithAWrongPasswordReceivesInvalidCredentials() {
        users.seed(guia(RegistrationStatus.PENDIENTE));

        StepVerifier.create(service(false).login(new LoginCommand(EMAIL, PASSWORD)))
                .expectError(InvalidCredentialsException.class)
                .verify();
    }

    @Test
    void aBlockedGuideWithTheRightPasswordReceivesInvalidCredentials() {
        Guia blocked = guia(RegistrationStatus.APROBADO);
        blocked.changeStatus(UserStatus.BLOQUEADO, NOW);
        users.seed(blocked);

        StepVerifier.create(service(true).login(new LoginCommand(EMAIL, PASSWORD)))
                .expectError(InvalidCredentialsException.class)
                .verify();
    }

    @Test
    void aRejectedGuideWithTheRightPasswordCannotEnter() {
        users.seed(guia(RegistrationStatus.RECHAZADO));

        StepVerifier.create(service(true).login(new LoginCommand(EMAIL, PASSWORD)))
                .expectError(AccountRegistrationRejectedException.class)
                .verify();
    }

    @Test
    void anApprovedGuideWithTheRightPasswordSignsIn() {
        users.seed(guia(RegistrationStatus.APROBADO));

        StepVerifier.create(service(true).login(new LoginCommand(EMAIL, PASSWORD)))
                .assertNext(result -> assertThat(result.accessToken()).isEqualTo("access-token"))
                .verifyComplete();
    }

    private LoginService service(boolean passwordMatches) {
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        LoginProtection protection = new LoginProtection(
                new FakeLoginAttemptRepository(),
                new LoginLockoutPolicy(5, Duration.ofMinutes(15), Duration.ofMinutes(15)),
                new FakeAccountLockedNotifier(),
                true,
                clock);
        FailedLoginSignal failedLogins = new FailedLoginSignal() {
            @Override
            public Mono<Void> record() {
                return Mono.empty();
            }

            @Override
            public Mono<Long> countSince(Instant since) {
                return Mono.just(0L);
            }
        };
        TokenIssuer tokenIssuer =
                (user, mfaVerified) -> Mono.just(new TokenIssuer.IssuedToken("access-token", NOW.plusSeconds(3600)));
        MfaChallengeIssuer challengeIssuer =
                user -> Mono.just(new MfaChallengeIssuer.IssuedChallenge("challenge", NOW.plusSeconds(300)));
        return new LoginService(
                users,
                new FakePasswordHasher().matches(passwordMatches),
                tokenIssuer,
                failedLogins,
                protection,
                accountLimiter,
                new FakeMfaRepository(),
                challengeIssuer,
                clock);
    }

    private static Guia guia(RegistrationStatus registrationStatus) {
        return Guia.rehydrate(
                UUID.fromString("22222222-2222-2222-2222-222222222222"),
                "Gina Guía",
                new Email(EMAIL),
                "3001234567",
                new PasswordHash("hash-de-prueba"),
                UserStatus.ACTIVO,
                0L,
                NOW,
                NOW,
                null,
                "CC",
                "9876543210",
                "LIC-001",
                registrationStatus,
                null);
    }
}
