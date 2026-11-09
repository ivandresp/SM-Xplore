package com.smxplore.users.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.smxplore.users.application.dto.LoginCommand;
import com.smxplore.users.application.port.out.FailedLoginSignal;
import com.smxplore.users.application.port.out.MfaChallengeIssuer;
import com.smxplore.users.application.port.out.TokenIssuer;
import com.smxplore.users.domain.exception.InvalidCredentialsException;
import com.smxplore.users.domain.model.Email;
import com.smxplore.users.domain.model.PasswordHash;
import com.smxplore.users.domain.model.Turista;
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
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * Pruebas de regresión de MS01-04: el inicio de sesión responde igual ante una cuenta inexistente, una inactiva o una
 * bloqueada, y también cuenta los intentos fallidos de correos que no existen.
 */
class LoginServiceEnumerationTest {

    private static final Instant NOW = Instant.parse("2026-01-01T10:00:00Z");

    private final FakeUserRepository users = new FakeUserRepository();
    private final RecordingAccountAttemptLimiter accountLimiter = new RecordingAccountAttemptLimiter(5);

    @Test
    void returnsTheSameInvalidCredentialsErrorForAnInactiveAccount() {
        Turista turista = turista();
        turista.changeStatus(UserStatus.BLOQUEADO, NOW);
        users.seed(turista);

        StepVerifier.create(service(new FakePasswordHasher()).login(new LoginCommand("ada@example.com", "secreto")))
                .expectError(InvalidCredentialsException.class)
                .verify();
    }

    @Test
    void countsFailedAttemptsForEmailsThatDoNotExist() {
        StepVerifier.create(
                        service(new FakePasswordHasher()).login(new LoginCommand("fantasma@example.com", "secreto")))
                .expectError(InvalidCredentialsException.class)
                .verify();

        assertThat(accountLimiter.failureKeys()).containsExactly("login:fantasma@example.com");
    }

    private LoginService service(FakePasswordHasher passwordHasher) {
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
                passwordHasher,
                tokenIssuer,
                failedLogins,
                protection,
                accountLimiter,
                new FakeMfaRepository(),
                challengeIssuer,
                clock);
    }

    private static Turista turista() {
        return Turista.register(
                UUID.fromString("11111111-1111-1111-1111-111111111111"),
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
