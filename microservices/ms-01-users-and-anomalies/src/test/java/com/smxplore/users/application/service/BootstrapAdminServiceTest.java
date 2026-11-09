package com.smxplore.users.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.smxplore.users.application.dto.BootstrapAdminCommand;
import com.smxplore.users.domain.model.AuditAction;
import com.smxplore.users.domain.model.Email;
import com.smxplore.users.domain.model.PasswordHash;
import com.smxplore.users.domain.model.Role;
import com.smxplore.users.domain.model.Turista;
import com.smxplore.users.support.FakeAuditLogRepository;
import com.smxplore.users.support.FakePasswordHasher;
import com.smxplore.users.support.FakeTransactionPort;
import com.smxplore.users.support.FakeUserRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

/** Pruebas de regresión de MS01-12: el administrador inicial solo se crea en una instalación vacía. */
class BootstrapAdminServiceTest {

    private static final Instant NOW = Instant.parse("2026-01-01T10:00:00Z");

    private static final BootstrapAdminCommand COMMAND =
            new BootstrapAdminCommand("Administrador Inicial", "admin@smxplore.local", "ContrasenaSegura123!");

    @Test
    void createsTheInitialAdminAndAuditsItWhenThereAreNoAccounts() {
        FakeUserRepository users = new FakeUserRepository();
        FakeAuditLogRepository auditLog = new FakeAuditLogRepository();

        StepVerifier.create(service(users, auditLog).bootstrap(COMMAND)).verifyComplete();

        assertThat(users.saved()).hasSize(1);
        assertThat(users.saved().get(0).role()).isEqualTo(Role.ADMIN);
        assertThat(auditLog.saved()).singleElement().satisfies(entry -> {
            assertThat(entry.action()).isEqualTo(AuditAction.ADMIN_INICIAL_CREADO);
            assertThat(entry.targetUserId()).isEqualTo(users.saved().get(0).id());
        });
    }

    @Test
    void skipsBootstrapWhenTheInstallationAlreadyHasAccounts() {
        FakeUserRepository users = new FakeUserRepository().seed(turista());
        FakeAuditLogRepository auditLog = new FakeAuditLogRepository();

        StepVerifier.create(service(users, auditLog).bootstrap(COMMAND)).verifyComplete();

        assertThat(users.saved()).isEmpty();
        assertThat(auditLog.saved()).isEmpty();
    }

    private static BootstrapAdminService service(FakeUserRepository users, FakeAuditLogRepository auditLog) {
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        return new BootstrapAdminService(
                users,
                new FakePasswordHasher(),
                new FakeTransactionPort(),
                new AuditTrailRecorder(users, auditLog),
                clock);
    }

    private static Turista turista() {
        return Turista.register(
                UUID.fromString("11111111-1111-1111-1111-111111111111"),
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
    }
}
