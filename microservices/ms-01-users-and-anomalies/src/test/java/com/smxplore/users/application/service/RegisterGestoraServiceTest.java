package com.smxplore.users.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.smxplore.users.application.dto.RegisterGestoraCommand;
import com.smxplore.users.application.port.out.GestoraRegistrationReceivedNotifier;
import com.smxplore.users.domain.exception.EmailAlreadyExistsException;
import com.smxplore.users.domain.model.Email;
import com.smxplore.users.domain.model.GestoraDestinos;
import com.smxplore.users.domain.model.PasswordHash;
import com.smxplore.users.domain.model.RegistrationStatus;
import com.smxplore.users.domain.model.Usuario;
import com.smxplore.users.support.FakePasswordHasher;
import com.smxplore.users.support.FakeTransactionPort;
import com.smxplore.users.support.FakeUserRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * El alta pública de una gestora de destinos también debe nacer pendiente de revisión: la aprueba un administrador y
 * hasta entonces la cuenta no puede iniciar sesión.
 */
class RegisterGestoraServiceTest {

    private static final Instant NOW = Instant.parse("2026-01-01T10:00:00Z");

    private final FakeUserRepository users = new FakeUserRepository();
    private final List<Usuario> notified = new ArrayList<>();
    private final GestoraRegistrationReceivedNotifier notifier = gestora -> {
        notified.add(gestora);
        return Mono.empty();
    };

    @Test
    void createsTheGestoraPendingReview() {
        StepVerifier.create(service().register(command()))
                .assertNext(view -> {
                    assertThat(view.role()).isEqualTo("DESTINATION_MANAGER");
                    assertThat(view.status().name()).isEqualTo("ACTIVO");
                })
                .verifyComplete();

        GestoraDestinos saved = (GestoraDestinos) users.lastSaved();
        assertThat(saved.registrationStatus()).isEqualTo(RegistrationStatus.PENDIENTE);
        assertThat(saved.nit()).isEqualTo("900000000-9");
        assertThat(saved.startOperationDate()).isEqualTo(LocalDate.of(2024, 1, 15));
    }

    @Test
    void notifiesThatTheRequestWasReceived() {
        StepVerifier.create(service().register(command())).expectNextCount(1).verifyComplete();

        assertThat(notified).singleElement().satisfies(gestora -> {
            assertThat(gestora.email().value()).isEqualTo("gestora@example.com");
            assertThat(((GestoraDestinos) gestora).registrationStatus()).isEqualTo(RegistrationStatus.PENDIENTE);
        });
    }

    @Test
    void rejectsAnEmailThatAlreadyHasAnAccount() {
        users.seed(GestoraDestinos.register(
                UUID.randomUUID(),
                "Gina Existente",
                new Email("gestora@example.com"),
                "3001234567",
                new PasswordHash("hash"),
                "900000000-1",
                LocalDate.of(2020, 1, 15),
                NOW));

        StepVerifier.create(service().register(command()))
                .expectError(EmailAlreadyExistsException.class)
                .verify();

        assertThat(notified).isEmpty();
    }

    private RegisterGestoraService service() {
        return new RegisterGestoraService(
                users, new FakePasswordHasher(), notifier, new FakeTransactionPort(), Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static RegisterGestoraCommand command() {
        return new RegisterGestoraCommand(
                "Gina Gestora",
                "gestora@example.com",
                "3001234567",
                "ChangeMe123!",
                "900000000-9",
                LocalDate.of(2024, 1, 15));
    }
}
