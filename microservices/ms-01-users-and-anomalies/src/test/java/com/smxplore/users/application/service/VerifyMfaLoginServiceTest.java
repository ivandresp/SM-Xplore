package com.smxplore.users.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.smxplore.users.application.dto.MfaChallengeContent;
import com.smxplore.users.application.dto.VerifyMfaCommand;
import com.smxplore.users.application.port.out.MfaChallengeVerifier;
import com.smxplore.users.application.port.out.TokenIssuer;
import com.smxplore.users.domain.exception.InvalidMfaChallengeException;
import com.smxplore.users.domain.exception.InvalidMfaCodeException;
import com.smxplore.users.domain.exception.TooManyLoginAttemptsException;
import com.smxplore.users.domain.model.Email;
import com.smxplore.users.domain.model.MfaEnrollment;
import com.smxplore.users.domain.model.PasswordHash;
import com.smxplore.users.domain.model.Turista;
import com.smxplore.users.domain.model.Usuario;
import com.smxplore.users.support.FakeMfaChallengeRepository;
import com.smxplore.users.support.FakeMfaRepository;
import com.smxplore.users.support.FakeTotpAuthenticator;
import com.smxplore.users.support.FakeUserRepository;
import com.smxplore.users.support.RecordingAccountAttemptLimiter;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * Pruebas de MS01-06 y MS01-08: el desafío MFA debe pertenecer a la versión de sesión vigente y solo puede canjearse
 * una vez; además el código está limitado por cuenta.
 */
class VerifyMfaLoginServiceTest {

    private static final UUID USER_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID JTI = UUID.fromString("44444444-4444-4444-4444-444444444444");
    private static final Instant NOW = Instant.parse("2026-01-01T10:00:00Z");

    private final FakeUserRepository users = new FakeUserRepository().seed(user());
    private final FakeMfaRepository mfa = new FakeMfaRepository().seed(enabledEnrollment());
    private final FakeMfaChallengeRepository challenges = new FakeMfaChallengeRepository();
    private final FakeTotpAuthenticator totp = new FakeTotpAuthenticator();
    private final RecordingAccountAttemptLimiter limiter = new RecordingAccountAttemptLimiter(5);

    @Test
    void rejectsTheChallengeWhenTheSessionVersionChanged() {
        // MS01-08: la cuenta ya revocó sesiones (authVersion 1), pero el desafío se emitió con la versión 0.
        VerifyMfaLoginService service = service(new MfaChallengeContent(USER_ID, 0, JTI));

        StepVerifier.create(service.verify(new VerifyMfaCommand("challenge", "123456")))
                .expectError(InvalidMfaChallengeException.class)
                .verify();

        assertThat(challenges.isConsumed(JTI)).isFalse();
    }

    @Test
    void acceptsAValidCodeAndConsumesTheChallenge() {
        VerifyMfaLoginService service = service(new MfaChallengeContent(USER_ID, 1, JTI));

        StepVerifier.create(service.verify(new VerifyMfaCommand("challenge", "123456")))
                .assertNext(result -> {
                    assertThat(result.accessToken()).isEqualTo("access-token");
                    assertThat(result.mfaRequired()).isFalse();
                })
                .verifyComplete();

        assertThat(challenges.isConsumed(JTI)).isTrue();
    }

    @Test
    void rejectsAChallengeThatWasAlreadyUsed() {
        VerifyMfaLoginService service = service(new MfaChallengeContent(USER_ID, 1, JTI));

        StepVerifier.create(service.verify(new VerifyMfaCommand("challenge", "123456")))
                .expectNextCount(1)
                .verifyComplete();

        // El atacante reintenta con un código del siguiente paso; el desafío ya fue canjeado, así que falla.
        totp.step(43L);

        StepVerifier.create(service.verify(new VerifyMfaCommand("challenge", "123456")))
                .expectError(InvalidMfaChallengeException.class)
                .verify();
    }

    @Test
    void blocksTheAccountAfterRepeatedInvalidCodes() {
        RecordingAccountAttemptLimiter accountLimiter = new RecordingAccountAttemptLimiter(1);
        VerifyMfaLoginService service = service(new MfaChallengeContent(USER_ID, 1, JTI), accountLimiter);

        StepVerifier.create(service.verify(new VerifyMfaCommand("challenge", "000000")))
                .expectError(InvalidMfaCodeException.class)
                .verify();

        StepVerifier.create(service.verify(new VerifyMfaCommand("challenge", "000000")))
                .expectError(TooManyLoginAttemptsException.class)
                .verify();
    }

    private VerifyMfaLoginService service(MfaChallengeContent content) {
        return service(content, limiter);
    }

    private VerifyMfaLoginService service(MfaChallengeContent content, RecordingAccountAttemptLimiter accountLimiter) {
        // El emisor real persiste el jti al entregar el desafío; aquí lo replicamos para que el canje sea posible.
        challenges.save(content.jti(), content.userId(), NOW.plusSeconds(300)).block();
        MfaChallengeVerifier verifier = token -> Mono.just(content);
        TokenIssuer tokenIssuer =
                (user, mfaVerified) -> Mono.just(new TokenIssuer.IssuedToken("access-token", NOW.plusSeconds(3600)));
        return new VerifyMfaLoginService(
                users,
                mfa,
                verifier,
                challenges,
                new MfaCodeVerifier(mfa, totp),
                accountLimiter,
                tokenIssuer,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static MfaEnrollment enabledEnrollment() {
        return MfaEnrollment.pending(USER_ID, "JBSWY3DPEHPK3PXP", NOW).confirm(NOW);
    }

    private static Usuario user() {
        Turista turista = Turista.register(
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
        turista.revokeSessions(NOW);
        return turista;
    }
}
