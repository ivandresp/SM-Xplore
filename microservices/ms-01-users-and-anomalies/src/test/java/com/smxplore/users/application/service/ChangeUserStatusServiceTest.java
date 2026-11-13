package com.smxplore.users.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.smxplore.users.application.dto.ChangeUserStatusCommand;
import com.smxplore.users.application.port.out.AccountRevokedNotifier;
import com.smxplore.users.domain.exception.FieldValidationException;
import com.smxplore.users.domain.exception.ForbiddenOperationException;
import com.smxplore.users.domain.model.Admin;
import com.smxplore.users.domain.model.AuditAction;
import com.smxplore.users.domain.model.Email;
import com.smxplore.users.domain.model.Guia;
import com.smxplore.users.domain.model.PasswordHash;
import com.smxplore.users.domain.model.RegistrationStatus;
import com.smxplore.users.domain.model.Role;
import com.smxplore.users.domain.model.Turista;
import com.smxplore.users.domain.model.UserStatus;
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
 * Revocación de una cuenta aprobada (B8-11): motivo obligatorio, correo, ADMIN y DESTINATION_MANAGER.
 */
class ChangeUserStatusServiceTest {

    private static final Instant NOW = Instant.parse("2026-01-01T10:00:00Z");
    private static final UUID ADMIN_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");

    private final FakeUserRepository users = new FakeUserRepository();
    private final FakeAuditLogRepository auditLog = new FakeAuditLogRepository();
    private final FakeAuthVersionEvictor evictor = new FakeAuthVersionEvictor();
    private final List<String> revokedReasons = new ArrayList<>();
    private final AccountRevokedNotifier notifier = (target, reason) -> {
        revokedReasons.add(reason);
        return Mono.empty();
    };

    @Test
    void revokingAnApprovedGuideWithoutAReasonIsAValidationError() {
        Guia guide = approvedGuide();
        users.seed(guide).seed(admin());

        StepVerifier.create(service()
                        .change(
                                ADMIN_ID,
                                guide.id(),
                                new ChangeUserStatusCommand(UserStatus.BLOQUEADO, "  ", Role.ADMIN)))
                .expectError(FieldValidationException.class)
                .verify();

        assertThat(guide.status()).isEqualTo(UserStatus.ACTIVO);
        assertThat(revokedReasons).isEmpty();
    }

    @Test
    void revokingAnApprovedGuideWithAReasonNotifiesAndAudits() {
        Guia guide = approvedGuide();
        users.seed(guide).seed(admin());

        StepVerifier.create(service()
                        .change(
                                ADMIN_ID,
                                guide.id(),
                                new ChangeUserStatusCommand(
                                        UserStatus.BLOQUEADO, "Incumplimiento de la licencia", Role.ADMIN)))
                .verifyComplete();

        assertThat(guide.status()).isEqualTo(UserStatus.BLOQUEADO);
        assertThat(revokedReasons).containsExactly("Incumplimiento de la licencia");
        assertThat(evictor.evicted()).containsExactly(guide.id());
        assertThat(auditLog.saved())
                .singleElement()
                .satisfies(entry -> assertThat(entry.action()).isEqualTo(AuditAction.CUENTA_REVOCADA));
    }

    @Test
    void aDestinationManagerCanRevokeAGuide() {
        Guia guide = approvedGuide();
        users.seed(guide).seed(admin());

        StepVerifier.create(service()
                        .change(
                                ADMIN_ID,
                                guide.id(),
                                new ChangeUserStatusCommand(UserStatus.BLOQUEADO, "Licencia vencida", Role.DESTINATION_MANAGER)))
                .verifyComplete();

        assertThat(guide.status()).isEqualTo(UserStatus.BLOQUEADO);
        assertThat(revokedReasons).containsExactly("Licencia vencida");
    }

    @Test
    void aDestinationManagerCannotActivateAccounts() {
        Guia guide = approvedGuide();
        guide.changeStatus(UserStatus.BLOQUEADO, NOW);
        users.seed(guide).seed(admin());

        StepVerifier.create(service()
                        .change(
                                ADMIN_ID,
                                guide.id(),
                                new ChangeUserStatusCommand(UserStatus.ACTIVO, "Reactivar", Role.DESTINATION_MANAGER)))
                .expectError(ForbiddenOperationException.class)
                .verify();

        assertThat(guide.status()).isEqualTo(UserStatus.BLOQUEADO);
    }

    @Test
    void anAdminCanBlockANonReviewableAccountWithoutAReason() {
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
                        .change(ADMIN_ID, tourist.id(), new ChangeUserStatusCommand(UserStatus.BLOQUEADO, null, Role.ADMIN)))
                .verifyComplete();

        assertThat(tourist.status()).isEqualTo(UserStatus.BLOQUEADO);
        assertThat(revokedReasons).isEmpty();
        assertThat(auditLog.saved())
                .singleElement()
                .satisfies(entry -> assertThat(entry.action()).isEqualTo(AuditAction.ESTADO_CAMBIADO));
    }

    private static Usuario admin() {
        return Admin.create(
                ADMIN_ID,
                "Admin Test",
                new Email("admin@example.com"),
                "3000000000",
                new PasswordHash("hash"),
                "CC",
                "1000000000",
                NOW);
    }

    private ChangeUserStatusService service() {
        return new ChangeUserStatusService(
                users,
                new AuditTrailRecorder(users, auditLog),
                evictor,
                notifier,
                new FakeTransactionPort(),
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static Guia approvedGuide() {
        Guia guide = Guia.requestRegistration(
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
        guide.changeRegistrationStatus(RegistrationStatus.APROBADO, NOW);
        return guide;
    }
}
