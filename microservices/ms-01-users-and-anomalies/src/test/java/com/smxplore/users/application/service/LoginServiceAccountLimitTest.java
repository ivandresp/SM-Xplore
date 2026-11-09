package com.smxplore.users.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.smxplore.users.application.dto.LoginCommand;
import com.smxplore.users.application.port.out.FailedLoginSignal;
import com.smxplore.users.application.port.out.MfaChallengeIssuer;
import com.smxplore.users.application.port.out.TokenIssuer;
import com.smxplore.users.domain.exception.InvalidCredentialsException;
import com.smxplore.users.domain.exception.TooManyLoginAttemptsException;
import com.smxplore.users.domain.model.Email;
import com.smxplore.users.domain.model.PasswordHash;
import com.smxplore.users.domain.model.Turista;
import com.smxplore.users.domain.model.Usuario;
import com.smxplore.users.domain.policy.LoginLockoutPolicy;
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

class LoginServiceAccountLimitTest {

    private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final String EMAIL = "ada@example.com";
    private static final Instant NOW = Instant.parse("2026-01-01T10:00:00Z");

    private final FakeUserRepository users = new FakeUserRepository().seed(turista());
    private final FakePasswordHasher passwordHasher = new FakePasswordHasher();
    private final RecordingAccountAttemptLimiter limiter = new RecordingAccountAttemptLimiter(2);
    private final LoginService service = new LoginService(
            users,
            passwordHasher,
            (user, mfaVerified) -> Mono.just(new TokenIssuer.IssuedToken("token", NOW.plusSeconds(60))),
            new FailedLoginSignal() {
                @Override
                public Mono<Void> record() {
                    return Mono.empty();
                }

                @Override
                public Mono<Long> countSince(Instant since) {
                    return Mono.just(0L);
                }
            },
            new LoginProtection(
                    new FakeLoginAttemptRepository(),
                    new LoginLockoutPolicy(5, Duration.ofMinutes(15), Duration.ofMinutes(15)),
                    new com.smxplore.users.support.FakeAccountLockedNotifier(),
                    false,
                    Clock.fixed(NOW, ZoneOffset.UTC)),
            limiter,
            new FakeMfaRepository(),
            (MfaChallengeIssuer)
                    user -> Mono.just(new MfaChallengeIssuer.IssuedChallenge("challenge", NOW.plusSeconds(300))),
            Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void blocksTheAccountAfterRepeatedFailuresEvenIfTheIpChanges() {
        passwordHasher.matches(false);

        StepVerifier.create(service.login(new LoginCommand(EMAIL, "incorrecta")))
                .expectError(InvalidCredentialsException.class)
                .verify();
        StepVerifier.create(service.login(new LoginCommand(EMAIL, "incorrecta")))
                .expectError(InvalidCredentialsException.class)
                .verify();

        StepVerifier.create(service.login(new LoginCommand(EMAIL, "incorrecta")))
                .expectError(TooManyLoginAttemptsException.class)
                .verify();

        assertThat(limiter.failureKeys()).containsExactly("login:" + EMAIL, "login:" + EMAIL);
        assertThat(limiter.ensuredKeys()).last().isEqualTo("login:" + EMAIL);
    }

    @Test
    void clearsTheAccountCounterAfterASuccessfulLogin() {
        passwordHasher.matches(true);

        StepVerifier.create(service.login(new LoginCommand(EMAIL, "Correcta123456")))
                .assertNext(result -> assertThat(result.accessToken()).isEqualTo("token"))
                .verifyComplete();

        assertThat(limiter.clearedKeys()).containsExactly("login:" + EMAIL);
    }

    private static Usuario turista() {
        return Turista.register(
                USER_ID,
                "Ada Turista",
                new Email(EMAIL),
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
