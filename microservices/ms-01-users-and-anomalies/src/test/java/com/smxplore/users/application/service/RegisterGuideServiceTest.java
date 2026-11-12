package com.smxplore.users.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.smxplore.users.application.dto.RegisterGuideCommand;
import com.smxplore.users.application.port.out.GuideRegistrationReceivedNotifier;
import com.smxplore.users.domain.exception.EmailAlreadyExistsException;
import com.smxplore.users.domain.model.Email;
import com.smxplore.users.domain.model.Guia;
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
 * El alta pública de un guía debe dejar la cuenta pendiente de revisión y avisar por correo de que la solicitud se
 * recibió; no puede nacer aprobada ni habilitar el acceso.
 */
class RegisterGuideServiceTest {

    private static final Instant NOW = Instant.parse("2026-01-01T10:00:00Z");

    private final FakeUserRepository users = new FakeUserRepository();
    private final List<Usuario> notified = new ArrayList<>();
    private final GuideRegistrationReceivedNotifier notifier = guide -> {
        notified.add(guide);
        return Mono.empty();
    };

    @Test
    void createsTheGuidePendingReview() {
        StepVerifier.create(service().register(command()))
                .assertNext(view -> {
                    assertThat(view.role()).isEqualTo("GUIDE");
                    assertThat(view.status().name()).isEqualTo("ACTIVO");
                })
                .verifyComplete();

        Guia saved = (Guia) users.lastSaved();
        assertThat(saved.registrationStatus()).isEqualTo(RegistrationStatus.PENDIENTE);
        assertThat(saved.license()).isEqualTo("LIC-001");
        assertThat(saved.dniNumber()).isEqualTo("9876543210");
    }

    @Test
    void notifiesThatTheRequestWasReceived() {
        StepVerifier.create(service().register(command())).expectNextCount(1).verifyComplete();

        assertThat(notified).singleElement().satisfies(guide -> {
            assertThat(guide.email().value()).isEqualTo("guia@example.com");
            assertThat(((Guia) guide).registrationStatus()).isEqualTo(RegistrationStatus.PENDIENTE);
        });
    }

    @Test
    void rejectsAnEmailThatAlreadyHasAnAccount() {
        users.seed(Guia.requestRegistration(
                UUID.randomUUID(),
                "Gina Existente",
                new Email("guia@example.com"),
                "3001234567",
                new PasswordHash("hash"),
                "CC",
                "1111111111",
                "LIC-999",
                null,
                NOW));

        StepVerifier.create(service().register(command()))
                .expectError(EmailAlreadyExistsException.class)
                .verify();

        assertThat(notified).isEmpty();
    }

    private RegisterGuideService service() {
        return new RegisterGuideService(
                users, new FakePasswordHasher(), notifier, new FakeTransactionPort(), Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static RegisterGuideCommand command() {
        return new RegisterGuideCommand(
                "Gina Guía",
                "guia@example.com",
                "3001234567",
                "ChangeMe123!",
                "CC",
                "9876543210",
                "LIC-001",
                LocalDate.of(2024, 1, 15));
    }
}
