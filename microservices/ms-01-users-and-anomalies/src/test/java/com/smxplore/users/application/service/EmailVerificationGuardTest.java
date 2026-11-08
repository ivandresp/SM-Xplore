package com.smxplore.users.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.smxplore.users.application.dto.EmailVerificationPolicy;
import com.smxplore.users.domain.exception.ForbiddenOperationException;
import com.smxplore.users.support.FakeEmailVerificationRepository;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

/** Regresión de MS01-18: las cuentas sin correo verificado no pueden publicar reseñas. */
class EmailVerificationGuardTest {

    private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    private final FakeEmailVerificationRepository verifications = new FakeEmailVerificationRepository();

    private EmailVerificationGuard guard(boolean enabled) {
        return new EmailVerificationGuard(verifications, new EmailVerificationPolicy(enabled, Duration.ofHours(24)));
    }

    @Test
    void rejectsAnUnverifiedAccountWhenTheControlIsEnabled() {
        StepVerifier.create(guard(true).ensureVerified(USER_ID))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(ForbiddenOperationException.class);
                    assertThat(error.getMessage()).contains("verificar tu correo");
                })
                .verify();
    }

    @Test
    void allowsAVerifiedAccount() {
        verifications.verify(USER_ID);

        StepVerifier.create(guard(true).ensureVerified(USER_ID)).verifyComplete();
    }

    @Test
    void allowsAnyAccountWhenTheControlIsDisabled() {
        StepVerifier.create(guard(false).ensureVerified(USER_ID)).verifyComplete();
    }
}
