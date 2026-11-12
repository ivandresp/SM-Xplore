package com.smxplore.users.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.smxplore.users.application.dto.EmailVerificationPolicy;
import com.smxplore.users.application.dto.RegisterGuideCommand;
import com.smxplore.users.application.dto.RoleDetailsView;
import com.smxplore.users.application.port.out.PasswordHasher;
import com.smxplore.users.domain.exception.EmailAlreadyExistsException;
import com.smxplore.users.domain.exception.ValidationException;
import com.smxplore.users.domain.model.Email;
import com.smxplore.users.domain.model.Guia;
import com.smxplore.users.domain.model.PasswordHash;
import com.smxplore.users.domain.model.RegistrationStatus;
import com.smxplore.users.domain.model.TokenPurpose;
import com.smxplore.users.domain.model.UserStatus;
import com.smxplore.users.domain.model.Usuario;
import com.smxplore.users.support.FakePasswordResetTokenRepository;
import com.smxplore.users.support.FakeTransactionPort;
import com.smxplore.users.support.FakeUserRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class RegisterGuideServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-08T00:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final String VALID_PASSWORD = "ChangeMe123!";

    private final FakeUserRepository users = new FakeUserRepository();
    private final FakePasswordResetTokenRepository tokens = new FakePasswordResetTokenRepository();
    private final List<Usuario> notified = new ArrayList<>();
    private final List<String> notifiedTokens = new ArrayList<>();

    @Test
    void registersAnActivePendingGuideAndSendsTheVerificationMail() {
        StepVerifier.create(service(true).register(command("guia.nueva@example.com")))
                .assertNext(view -> {
                    assertThat(view.role()).isEqualTo("GUIDE");
                    assertThat(view.status()).isEqualTo(UserStatus.ACTIVO);
                    assertThat(view.authVersion()).isZero();
                    RoleDetailsView.Guide profile = (RoleDetailsView.Guide) view.profile();
                    assertThat(profile.registrationStatus()).isEqualTo("PENDIENTE");
                    assertThat(profile.license()).isEqualTo("LIC-100");
                })
                .verifyComplete();

        Guia saved = (Guia) users.lastSaved();
        assertThat(saved.registrationStatus()).isEqualTo(RegistrationStatus.PENDIENTE);
        assertThat(saved.passwordHash().value()).isEqualTo("hash:" + VALID_PASSWORD);
        assertThat(tokens.saved()).singleElement().satisfies(token -> {
            assertThat(token.userId()).isEqualTo(saved.id());
            assertThat(token.purpose()).isEqualTo(TokenPurpose.EMAIL_VERIFICATION);
            assertThat(token.tokenHash()).isEqualTo("hashed:raw-token");
        });
        assertThat(tokens.invalidatedPurposes()).containsExactly("EMAIL_VERIFICATION");
        assertThat(notified).containsExactly(saved);
        assertThat(notifiedTokens).containsExactly("raw-token");
    }

    @Test
    void persistsNoTokenWhenVerificationIsDisabled() {
        StepVerifier.create(service(false).register(command("guia.sin.token@example.com")))
                .assertNext(view -> assertThat(view.role()).isEqualTo("GUIDE"))
                .verifyComplete();

        assertThat(tokens.saved()).isEmpty();
        assertThat(notified).isEmpty();
        assertThat(users.saved()).hasSize(1);
    }

    @Test
    void rejectsADuplicatedEmail() {
        users.seed(Guia.register(
                java.util.UUID.randomUUID(),
                "Guía Existente",
                new Email("guia.duplicada@example.com"),
                "3005551100",
                new PasswordHash("hash"),
                "CC",
                "1000001100",
                "LIC-EXP",
                LocalDate.parse("2024-01-01"),
                NOW));

        StepVerifier.create(service(true).register(command("guia.duplicada@example.com")))
                .expectError(EmailAlreadyExistsException.class)
                .verify();

        assertThat(users.saved()).isEmpty();
        assertThat(tokens.saved()).isEmpty();
    }

    @Test
    void rejectsAWeakPassword() {
        assertThatThrownBy(() -> service(true)
                        .register(new RegisterGuideCommand(
                                "Guía Débil",
                                "guia.debil@example.com",
                                "3005551101",
                                "corta",
                                "CC",
                                "1000001101",
                                "LIC-101",
                                LocalDate.parse("2024-01-01"))))
                .isInstanceOf(ValidationException.class);

        assertThat(users.saved()).isEmpty();
    }

    private RegisterGuideService service(boolean verificationEnabled) {
        return new RegisterGuideService(
                users,
                new PasswordHasher() {
                    @Override
                    public Mono<PasswordHash> hash(String rawPassword) {
                        return Mono.just(new PasswordHash("hash:" + rawPassword));
                    }

                    @Override
                    public Mono<Boolean> matches(String rawPassword, PasswordHash passwordHash) {
                        return Mono.just(passwordHash.value().equals("hash:" + rawPassword));
                    }
                },
                tokens,
                () -> "raw-token",
                raw -> "hashed:" + raw,
                (user, rawToken) -> {
                    notified.add(user);
                    notifiedTokens.add(rawToken);
                    return Mono.empty();
                },
                new EmailVerificationPolicy(verificationEnabled, Duration.ofHours(24)),
                new FakeTransactionPort(),
                CLOCK);
    }

    private static RegisterGuideCommand command(String email) {
        return new RegisterGuideCommand(
                "Guía Nueva",
                email,
                "3005551102",
                VALID_PASSWORD,
                "CC",
                "1000001102",
                "LIC-100",
                LocalDate.parse("2024-01-01"));
    }
}
