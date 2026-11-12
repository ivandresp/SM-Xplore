package com.smxplore.users.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.smxplore.users.application.dto.ReviewRegistrationCommand;
import com.smxplore.users.application.port.out.RegistrationReviewNotifier;
import com.smxplore.users.domain.exception.InvalidStatusTransitionException;
import com.smxplore.users.domain.exception.UserNotFoundException;
import com.smxplore.users.domain.exception.ValidationException;
import com.smxplore.users.domain.model.AuditAction;
import com.smxplore.users.domain.model.Email;
import com.smxplore.users.domain.model.GestoraDestinos;
import com.smxplore.users.domain.model.Guia;
import com.smxplore.users.domain.model.PasswordHash;
import com.smxplore.users.domain.model.RegistrationStatus;
import com.smxplore.users.domain.model.Usuario;
import com.smxplore.users.support.FakeAuditLogRepository;
import com.smxplore.users.support.FakeAuthVersionEvictor;
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

class ReviewGestoraServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-08T00:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final UUID ACTOR_ID = UUID.fromString("00000000-0000-0000-0000-000000000401");
    private static final UUID MANAGER_ID = UUID.fromString("00000000-0000-0000-0000-000000000402");

    private final FakeUserRepository users = new FakeUserRepository();
    private final FakeAuditLogRepository audits = new FakeAuditLogRepository();
    private final FakeAuthVersionEvictor evictor = new FakeAuthVersionEvictor();
    private final RecordingNotifier notifier = new RecordingNotifier();

    @Test
    void approvesAPendingDestinationManager() {
        seedActor();
        GestoraDestinos gestora = seedGestora(RegistrationStatus.PENDIENTE);

        StepVerifier.create(service()
                        .review(
                                ACTOR_ID,
                                gestora.id(),
                                new ReviewRegistrationCommand(
                                        RegistrationStatus.APROBADO, "Cámara de comercio validada")))
                .verifyComplete();

        assertThat(gestora.registrationStatus()).isEqualTo(RegistrationStatus.APROBADO);
        assertThat(gestora.authVersion()).isEqualTo(1L);
        assertThat(audits.saved()).singleElement().satisfies(entry -> {
            assertThat(entry.action()).isEqualTo(AuditAction.GESTORA_APROBADA);
            assertThat(entry.targetUserId()).isEqualTo(MANAGER_ID);
            assertThat(entry.details())
                    .containsEntry("decision", "APROBADO")
                    .containsEntry("motivo", "Cámara de comercio validada");
        });
        assertThat(evictor.evicted()).containsExactly(MANAGER_ID);
        assertThat(notifier.decisions).containsExactly(RegistrationStatus.APROBADO);
    }

    @Test
    void rejectsAPendingDestinationManager() {
        seedActor();
        GestoraDestinos gestora = seedGestora(RegistrationStatus.PENDIENTE);

        StepVerifier.create(service()
                        .review(
                                ACTOR_ID,
                                gestora.id(),
                                new ReviewRegistrationCommand(RegistrationStatus.RECHAZADO, "NIT inactivo")))
                .verifyComplete();

        assertThat(gestora.registrationStatus()).isEqualTo(RegistrationStatus.RECHAZADO);
        assertThat(audits.saved())
                .singleElement()
                .satisfies(entry -> assertThat(entry.action()).isEqualTo(AuditAction.GESTORA_RECHAZADA));
    }

    @Test
    void reportsAnUnknownDestinationManager() {
        seedActor();

        StepVerifier.create(service()
                        .review(
                                ACTOR_ID,
                                UUID.fromString("00000000-0000-0000-0000-000000000499"),
                                new ReviewRegistrationCommand(RegistrationStatus.APROBADO, null)))
                .expectError(UserNotFoundException.class)
                .verify();
    }

    @Test
    void onlyDestinationManagersCanBeReviewed() {
        seedActor();
        Guia guide = Guia.register(
                MANAGER_ID,
                "Guía No Gestora",
                new Email("guia.no.gestora@example.com"),
                "3005551402",
                new PasswordHash("hash"),
                "CC",
                "1000001402",
                "LIC-402",
                LocalDate.parse("2024-01-01"),
                NOW);
        users.seed(guide);

        StepVerifier.create(service()
                        .review(ACTOR_ID, MANAGER_ID, new ReviewRegistrationCommand(RegistrationStatus.APROBADO, null)))
                .expectError(ValidationException.class)
                .verify();
    }

    @Test
    void rejectsRepeatingTheSameDecision() {
        seedActor();
        GestoraDestinos gestora = seedGestora(RegistrationStatus.APROBADO);

        StepVerifier.create(service()
                        .review(
                                ACTOR_ID,
                                gestora.id(),
                                new ReviewRegistrationCommand(RegistrationStatus.APROBADO, null)))
                .expectError(InvalidStatusTransitionException.class)
                .verify();
    }

    private ReviewGestoraService service() {
        return new ReviewGestoraService(
                users, new AuditTrailRecorder(users, audits), notifier, evictor, new FakeTransactionPort(), CLOCK);
    }

    private void seedActor() {
        users.seed(GestoraDestinos.register(
                ACTOR_ID,
                "Administrador Actor",
                new Email("admin.actor@example.com"),
                "3005551401",
                new PasswordHash("hash"),
                "900555140-1",
                LocalDate.parse("2024-01-01"),
                NOW));
    }

    private GestoraDestinos seedGestora(RegistrationStatus status) {
        GestoraDestinos gestora = GestoraDestinos.register(
                MANAGER_ID,
                "Gestora Revisada",
                new Email("gestora.revisada@example.com"),
                "3005551403",
                new PasswordHash("hash"),
                "900555140-3",
                LocalDate.parse("2024-01-01"),
                status,
                NOW);
        users.seed(gestora);
        return gestora;
    }

    private static final class RecordingNotifier implements RegistrationReviewNotifier {

        private final List<RegistrationStatus> decisions = new ArrayList<>();

        @Override
        public Mono<Void> notify(Usuario user, RegistrationStatus decision, String reason) {
            decisions.add(decision);
            return Mono.empty();
        }
    }
}
