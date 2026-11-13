package com.smxplore.users.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.smxplore.users.application.dto.ReviewRegistrationCommand;
import com.smxplore.users.application.port.out.RegistrationReviewNotifier;
import com.smxplore.users.domain.exception.FieldValidationException;
import com.smxplore.users.domain.exception.ForbiddenOperationException;
import com.smxplore.users.domain.exception.InvalidStatusTransitionException;
import com.smxplore.users.domain.exception.ValidationException;
import com.smxplore.users.domain.model.AuditAction;
import com.smxplore.users.domain.model.Email;
import com.smxplore.users.domain.model.GestoraDestinos;
import com.smxplore.users.domain.model.Guia;
import com.smxplore.users.domain.model.PasswordHash;
import com.smxplore.users.domain.model.RegistrationStatus;
import com.smxplore.users.domain.model.Turista;
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

/**
 * La revisión del alta de un guía reutiliza la misma máquina de estados que la de la prestadora de servicios: mismo
 * caso de uso, mismo registro de auditoría y mismo aviso por correo, sin una segunda implementación.
 */
class ReviewRegistrationServiceTest {

    private static final Instant NOW = Instant.parse("2026-01-01T10:00:00Z");
    private static final UUID ADMIN_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");

    private final FakeUserRepository users = new FakeUserRepository();
    private final FakeAuditLogRepository auditLog = new FakeAuditLogRepository();
    private final FakeAuthVersionEvictor evictor = new FakeAuthVersionEvictor();
    private final List<RegistrationStatus> decisions = new ArrayList<>();
    private final List<String> reasons = new ArrayList<>();
    private final RegistrationReviewNotifier notifier = (target, decision, reason) -> {
        decisions.add(decision);
        reasons.add(reason);
        return Mono.empty();
    };

    @Test
    void approvingAGuidePublishesTheDecision() {
        Guia guide = guide();
        users.seed(guide).seed(admin());

        StepVerifier.create(service()
                        .review(
                                ADMIN_ID,
                                guide.id(),
                                new ReviewRegistrationCommand(RegistrationStatus.APROBADO, "Documentación verificada")))
                .verifyComplete();

        assertThat(guide.registrationStatus()).isEqualTo(RegistrationStatus.APROBADO);
        assertThat(decisions).containsExactly(RegistrationStatus.APROBADO);
        assertThat(evictor.evicted()).containsExactly(guide.id());
        assertThat(auditLog.saved())
                .singleElement()
                .satisfies(entry -> assertThat(entry.action()).isEqualTo(AuditAction.GUIA_APROBADA));
    }

    @Test
    void rejectingAGuideKeepsTheReason() {
        Guia guide = guide();
        users.seed(guide).seed(admin());

        StepVerifier.create(service()
                        .review(
                                ADMIN_ID,
                                guide.id(),
                                new ReviewRegistrationCommand(RegistrationStatus.RECHAZADO, "Licencia no verificable")))
                .verifyComplete();

        assertThat(guide.registrationStatus()).isEqualTo(RegistrationStatus.RECHAZADO);
        assertThat(reasons).containsExactly("Licencia no verificable");
        assertThat(auditLog.saved())
                .singleElement()
                .satisfies(entry -> assertThat(entry.action()).isEqualTo(AuditAction.GUIA_RECHAZADA));
    }

    @Test
    void rejectingWithoutAReasonIsAFieldValidationError() {
        Guia guide = guide();
        users.seed(guide).seed(admin());

        StepVerifier.create(service()
                        .review(
                                ADMIN_ID,
                                guide.id(),
                                new ReviewRegistrationCommand(RegistrationStatus.RECHAZADO, "  ")))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(FieldValidationException.class);
                    assertThat(((FieldValidationException) error).fieldErrors()).containsKey("reason");
                })
                .verify();

        assertThat(guide.registrationStatus()).isEqualTo(RegistrationStatus.PENDIENTE);
        assertThat(decisions).isEmpty();
    }

    @Test
    void aRejectedRegistrationCanBeApprovedAgain() {
        Guia guide = guide();
        guide.changeRegistrationStatus(RegistrationStatus.RECHAZADO, NOW);
        users.seed(guide).seed(admin());

        StepVerifier.create(service()
                        .review(
                                ADMIN_ID,
                                guide.id(),
                                new ReviewRegistrationCommand(RegistrationStatus.APROBADO, "Revisión manual")))
                .verifyComplete();

        assertThat(guide.registrationStatus()).isEqualTo(RegistrationStatus.APROBADO);
        assertThat(decisions).containsExactly(RegistrationStatus.APROBADO);
        assertThat(reasons).containsExactly("Revisión manual");
    }

    @Test
    void reopeningARejectedRegistrationWithoutAReasonIsAFieldValidationError() {
        Guia guide = guide();
        guide.changeRegistrationStatus(RegistrationStatus.RECHAZADO, NOW);
        users.seed(guide).seed(admin());

        StepVerifier.create(service()
                        .review(ADMIN_ID, guide.id(), new ReviewRegistrationCommand(RegistrationStatus.APROBADO, null)))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(FieldValidationException.class);
                    assertThat(((FieldValidationException) error).fieldErrors()).containsKey("reason");
                })
                .verify();

        assertThat(guide.registrationStatus()).isEqualTo(RegistrationStatus.RECHAZADO);
    }

    @Test
    void rejectingAnApprovedRegistrationWithoutAReasonIsAConflictFirst() {
        Guia guide = guide();
        guide.changeRegistrationStatus(RegistrationStatus.APROBADO, NOW);
        users.seed(guide).seed(admin());

        // B8-14: el estado terminal gana al motivo ausente; es 409, no 400.
        StepVerifier.create(service()
                        .review(ADMIN_ID, guide.id(), new ReviewRegistrationCommand(RegistrationStatus.RECHAZADO, null)))
                .expectError(InvalidStatusTransitionException.class)
                .verify();
    }

    @Test
    void anApprovedRegistrationIsTerminal() {
        Guia guide = guide();
        guide.changeRegistrationStatus(RegistrationStatus.APROBADO, NOW);
        users.seed(guide).seed(admin());

        // Aprobar es terminal: revocar una guia ya aprobada se hace por estado de usuario desde administracion,
        // no por revision. Asi dos gestoras con la lista cacheada no se sobrescriben.
        StepVerifier.create(service()
                        .review(
                                ADMIN_ID,
                                guide.id(),
                                new ReviewRegistrationCommand(RegistrationStatus.RECHAZADO, "Ya no cumple")))
                .expectError(InvalidStatusTransitionException.class)
                .verify();

        assertThat(guide.registrationStatus()).isEqualTo(RegistrationStatus.APROBADO);
        assertThat(decisions).isEmpty();
    }

    @Test
    void aProfileWithoutRegistrationCannotBeReviewed() {
        Turista tourist = Turista.register(
                UUID.randomUUID(),
                "Ada Turista",
                new Email("ada@example.com"),
                "3001234567",
                new PasswordHash("hash"),
                "CC",
                "1234567890",
                "es",
                LocalDate.of(1990, 1, 1),
                "Colombia",
                NOW);
        users.seed(tourist).seed(admin());

        StepVerifier.create(service()
                        .review(
                                ADMIN_ID,
                                tourist.id(),
                                new ReviewRegistrationCommand(RegistrationStatus.APROBADO, null)))
                .expectError(ValidationException.class)
                .verify();
    }

    @Test
    void aRepeatedDecisionIsAConflict() {
        Guia guide = guide();
        guide.changeRegistrationStatus(RegistrationStatus.APROBADO, NOW);
        users.seed(guide).seed(admin());

        StepVerifier.create(service()
                        .review(ADMIN_ID, guide.id(), new ReviewRegistrationCommand(RegistrationStatus.APROBADO, null)))
                .expectError(InvalidStatusTransitionException.class)
                .verify();
    }

    @Test
    void onlyAnAdminCanReviewTheRegistrationOfAGestora() {
        GestoraDestinos pending = gestora("gina.gestora@example.com");
        Usuario anotherManager = com.smxplore.users.domain.model.GestoraDestinos.register(
                UUID.fromString("55555555-5555-5555-5555-555555555555"),
                "Otra Gestora",
                new Email("otra.gestora@example.com"),
                "3009999999",
                new PasswordHash("hash"),
                "900000000-1",
                LocalDate.of(2020, 1, 15),
                NOW);
        users.seed(pending).seed(anotherManager);

        StepVerifier.create(service()
                        .review(
                                anotherManager.id(),
                                pending.id(),
                                new ReviewRegistrationCommand(RegistrationStatus.APROBADO, null)))
                .expectError(ForbiddenOperationException.class)
                .verify();

        assertThat(pending.registrationStatus()).isEqualTo(RegistrationStatus.PENDIENTE);
        assertThat(decisions).isEmpty();

        users.seed(admin());

        StepVerifier.create(service()
                        .review(
                                ADMIN_ID,
                                pending.id(),
                                new ReviewRegistrationCommand(RegistrationStatus.APROBADO, null)))
                .verifyComplete();

        assertThat(pending.registrationStatus()).isEqualTo(RegistrationStatus.APROBADO);
        assertThat(auditLog.saved())
                .singleElement()
                .satisfies(entry -> assertThat(entry.action()).isEqualTo(AuditAction.GESTORA_APROBADA));
    }

    private ReviewRegistrationService service() {
        return new ReviewRegistrationService(
                users,
                new AuditTrailRecorder(users, auditLog),
                notifier,
                evictor,
                new FakeTransactionPort(),
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static Guia guide() {
        return Guia.requestRegistration(
                UUID.fromString("44444444-4444-4444-4444-444444444444"),
                "Gina Guía",
                new Email("guia@example.com"),
                "3001234567",
                new PasswordHash("hash"),
                "CC",
                "9876543210",
                "LIC-001",
                null,
                NOW);
    }

    private static GestoraDestinos gestora(String email) {
        return GestoraDestinos.requestRegistration(
                UUID.fromString("66666666-6666-6666-6666-666666666666"),
                "Gina Gestora",
                new Email(email),
                "3008888888",
                new PasswordHash("hash"),
                "900000000-9",
                LocalDate.of(2024, 2, 1),
                NOW);
    }

    private static Usuario admin() {
        return com.smxplore.users.domain.model.Admin.create(
                ADMIN_ID,
                "Admin Test",
                new Email("admin@example.com"),
                "3000000000",
                new PasswordHash("hash"),
                "CC",
                "1000000000",
                NOW);
    }
}
