package com.smxplore.users.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.smxplore.users.application.dto.EmailVerificationPolicy;
import com.smxplore.users.application.dto.RegisterGestoraCommand;
import com.smxplore.users.application.dto.RoleDetailsView;
import com.smxplore.users.application.port.out.PasswordHasher;
import com.smxplore.users.domain.exception.EmailAlreadyExistsException;
import com.smxplore.users.domain.exception.ValidationException;
import com.smxplore.users.domain.model.Email;
import com.smxplore.users.domain.model.GestoraDestinos;
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

class RegisterGestoraServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-08T00:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final String VALID_PASSWORD = "ChangeMe123!";

    private final FakeUserRepository users = new FakeUserRepository();
    private final FakePasswordResetTokenRepository tokens = new FakePasswordResetTokenRepository();
    private final List<Usuario> notified = new ArrayList<>();

    @Test
    void registersAnActivePendingDestinationManagerAndSendsTheVerificationMail() {
        StepVerifier.create(service(true).register(command("gestora.nueva@example.com")))
                .assertNext(view -> {
                    assertThat(view.role()).isEqualTo("DESTINATION_MANAGER");
                    assertThat(view.status()).isEqualTo(UserStatus.ACTIVO);
                    RoleDetailsView.DestinationManager profile = (RoleDetailsView.DestinationManager) view.profile();
                    assertThat(profile.registrationStatus()).isEqualTo("PENDIENTE");
                    assertThat(profile.nit()).isEqualTo("900555111-2");
                })
                .verifyComplete();

        GestoraDestinos saved = (GestoraDestinos) users.lastSaved();
        assertThat(saved.registrationStatus()).isEqualTo(RegistrationStatus.PENDIENTE);
        assertThat(tokens.saved()).singleElement().satisfies(token -> {
            assertThat(token.userId()).isEqualTo(saved.id());
            assertThat(token.purpose()).isEqualTo(TokenPurpose.EMAIL_VERIFICATION);
        });
        assertThat(notified).containsExactly(saved);
    }

    @Test
    void rejectsADuplicatedEmail() {
        users.seed(GestoraDestinos.register(
                java.util.UUID.randomUUID(),
                "Gestora Existente",
                new Email("gestora.duplicada@example.com"),
                "3005551200",
                new PasswordHash("hash"),
                "900555120-1",
                LocalDate.parse("2024-01-01"),
                NOW));

        StepVerifier.create(service(true).register(command("gestora.duplicada@example.com")))
                .expectError(EmailAlreadyExistsException.class)
                .verify();

        assertThat(users.saved()).isEmpty();
    }

    @Test
    void rejectsAWeakPassword() {
        assertThatThrownBy(() -> service(true)
                        .register(new RegisterGestoraCommand(
                                "Gestora Débil",
                                "gestora.debil@example.com",
                                "3005551202",
                                "corta",
                                "900555120-2",
                                LocalDate.parse("2024-01-01"))))
                .isInstanceOf(ValidationException.class);

        assertThat(users.saved()).isEmpty();
    }

    private RegisterGestoraService service(boolean verificationEnabled) {
        return new RegisterGestoraService(
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
                    return Mono.empty();
                },
                new EmailVerificationPolicy(verificationEnabled, Duration.ofHours(24)),
                new FakeTransactionPort(),
                CLOCK);
    }

    private static RegisterGestoraCommand command(String email) {
        return new RegisterGestoraCommand(
                "Gestora Nueva", email, "3005551201", VALID_PASSWORD, "900555111-2", LocalDate.parse("2024-01-01"));
    }
}
