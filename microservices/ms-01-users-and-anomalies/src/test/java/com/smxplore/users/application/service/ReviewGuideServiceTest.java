package com.smxplore.users.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.smxplore.users.application.dto.ReviewRegistrationCommand;
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

class ReviewGuideServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-08T00:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final UUID ACTOR_ID = UUID.fromString("00000000-0000-0000-0000-000000000301");
    private static final UUID GUIDE_ID = UUID.fromString("00000000-0000-0000-0000-000000000302");

    private final FakeUserRepository users = new FakeUserRepository();
    private final FakeAuditLogRepository audits = new FakeAuditLogRepository();
    private final FakeAuthVersionEvictor evictor = new FakeAuthVersionEvictor();
    private final RecordingNotifier notifier = new RecordingNotifier();

    @Test
    void approvesAPendingGuide() {
        seedActor();
        Guia guide = seedGuide(RegistrationStatus.PENDIENTE);

        StepVerifier.create(service()
                        .review(
                                ACTOR_ID,
                                guide.id(),
                                new ReviewRegistrationCommand(RegistrationStatus.APROBADO, "Documentación verificada")))
                .verifyComplete();

        assertThat(guide.registrationStatus()).isEqualTo(RegistrationStatus.APROBADO);
        assertThat(guide.authVersion()).isEqualTo(1L);
        assertThat(audits.saved()).singleElement().satisfies(entry -> {
            assertThat(entry.action()).isEqualTo(AuditAction.GUIA_APROBADA);
            assertThat(entry.targetUserId()).isEqualTo(GUIDE_ID);
            assertThat(entry.actorRole()).isEqualTo(com.smxplore.users.domain.model.Role.DESTINATION_MANAGER);
            assertThat(entry.details())
                    .containsEntry("decision", "APROBADO")
                    .containsEntry("motivo", "Documentación verificada");
        });
        assertThat(evictor.evicted()).containsExactly(GUIDE_ID);
        assertThat(notifier.decisions).containsExactly(RegistrationStatus.APROBADO);
        assertThat(notifier.reasons).containsExactly("Documentación verificada");
    }

    @Test
    void rejectsAPendingGuide() {
        seedActor();
        Guia guide = seedGuide(RegistrationStatus.PENDIENTE);

        StepVerifier.create(service()
                        .review(
                                ACTOR_ID,
                                guide.id(),
                                new ReviewRegistrationCommand(RegistrationStatus.RECHAZADO, "Licencia vencida")))
                .verifyComplete();

        assertThat(guide.registrationStatus()).isEqualTo(RegistrationStatus.RECHAZADO);
        assertThat(audits.saved()).singleElement().satisfies(entry -> {
            assertThat(entry.action()).isEqualTo(AuditAction.GUIA_RECHAZADA);
            assertThat(entry.details()).containsEntry("motivo", "Licencia vencida");
        });
        assertThat(notifier.decisions).containsExactly(RegistrationStatus.RECHAZADO);
    }

    @Test
    void canCorrectAPreviousDecision() {
        seedActor();
        Guia guide = seedGuide(RegistrationStatus.APROBADO);

        StepVerifier.create(service()
                        .review(
                                ACTOR_ID,
                                guide.id(),
                                new ReviewRegistrationCommand(RegistrationStatus.RECHAZADO, "Documento alterado")))
                .verifyComplete();

        assertThat(guide.registrationStatus()).isEqualTo(RegistrationStatus.RECHAZADO);
        assertThat(guide.authVersion()).isEqualTo(1L);
    }

    @Test
    void reportsAnUnknownGuide() {
        seedActor();

        StepVerifier.create(service()
                        .review(
                                ACTOR_ID,
                                UUID.fromString("00000000-0000-0000-0000-000000000399"),
                                new ReviewRegistrationCommand(RegistrationStatus.APROBADO, null)))
                .expectError(UserNotFoundException.class)
                .verify();
    }

    @Test
    void onlyGuidesCanBeReviewed() {
        seedActor();
        GestoraDestinos gestora = GestoraDestinos.register(
                GUIDE_ID,
                "Gestora No Guía",
                new Email("gestora.no.guia@example.com"),
                "3005551302",
                new PasswordHash("hash"),
                "900555130-2",
                LocalDate.parse("2024-01-01"),
                NOW);
        users.seed(gestora);

        StepVerifier.create(service()
                        .review(ACTOR_ID, GUIDE_ID, new ReviewRegistrationCommand(RegistrationStatus.APROBADO, null)))
                .expectError(ValidationException.class)
                .verify();
    }

    @Test
    void rejectsRepeatingTheSameDecision() {
        seedActor();
        Guia guide = seedGuide(RegistrationStatus.APROBADO);

        StepVerifier.create(service()
                        .review(ACTOR_ID, guide.id(), new ReviewRegistrationCommand(RegistrationStatus.APROBADO, null)))
                .expectError(InvalidStatusTransitionException.class)
                .verify();
    }

    @Test
    void rejectsPendingOrMissingDecision() {
        seedActor();
        Guia guide = seedGuide(RegistrationStatus.PENDIENTE);

        assertThatThrownBy(() -> service()
                        .review(
                                ACTOR_ID,
                                guide.id(),
                                new ReviewRegistrationCommand(RegistrationStatus.PENDIENTE, null)))
                .isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> service().review(ACTOR_ID, guide.id(), new ReviewRegistrationCommand(null, null)))
                .isInstanceOf(ValidationException.class);
    }

    private ReviewGuideService service() {
        return new ReviewGuideService(
                users, new AuditTrailRecorder(users, audits), notifier, evictor, new FakeTransactionPort(), CLOCK);
    }

    private void seedActor() {
        GestoraDestinos actor = GestoraDestinos.register(
                ACTOR_ID,
                "Gestora Actora",
                new Email("gestora.actora@example.com"),
                "3005551301",
                new PasswordHash("hash"),
                "900555130-1",
                LocalDate.parse("2024-01-01"),
                NOW);
        users.seed(actor);
    }

    private Guia seedGuide(RegistrationStatus status) {
        Guia guide = Guia.register(
                GUIDE_ID,
                "Guía Revisada",
                new Email("guia.revisada@example.com"),
                "3005551303",
                new PasswordHash("hash"),
                "CC",
                "1000001303",
                "LIC-303",
                LocalDate.parse("2024-01-01"),
                status,
                NOW);
        users.seed(guide);
        return guide;
    }

    private static final class RecordingNotifier
            implements com.smxplore.users.application.port.out.RegistrationReviewNotifier {

        private final List<RegistrationStatus> decisions = new ArrayList<>();
        private final List<String> reasons = new ArrayList<>();

        @Override
        public Mono<Void> notify(Usuario user, RegistrationStatus decision, String reason) {
            decisions.add(decision);
            reasons.add(reason);
            return Mono.empty();
        }
    }
}
