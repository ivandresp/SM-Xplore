package com.smxplore.users.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.smxplore.users.application.dto.ChangeUserStatusCommand;
import com.smxplore.users.application.port.out.AccountEventPublisher;
import com.smxplore.users.domain.model.Admin;
import com.smxplore.users.domain.model.Email;
import com.smxplore.users.domain.model.PasswordHash;
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

/** B3: bloquear una cuenta publica users.account.blocked para que ms-03 despublique su contenido. */
class ChangeUserStatusServiceTest {

    private static final Instant NOW = Instant.parse("2026-01-01T10:00:00Z");
    private static final UUID ADMIN_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");

    private final FakeUserRepository users = new FakeUserRepository();
    private final FakeAuditLogRepository auditLog = new FakeAuditLogRepository();
    private final FakeAuthVersionEvictor evictor = new FakeAuthVersionEvictor();
    private final List<UUID> blocked = new ArrayList<>();
    private final AccountEventPublisher events = target -> {
        blocked.add(target.id());
        return Mono.empty();
    };

    @Test
    void blockingAnAccountPublishesTheEvent() {
        Turista tourist = tourist();
        users.seed(tourist).seed(admin());

        StepVerifier.create(service()
                        .change(
                                ADMIN_ID,
                                tourist.id(),
                                new ChangeUserStatusCommand(UserStatus.BLOQUEADO, "Incumplimiento")))
                .verifyComplete();

        assertThat(tourist.status()).isEqualTo(UserStatus.BLOQUEADO);
        assertThat(blocked).containsExactly(tourist.id());
    }

    @Test
    void activatingAnAccountDoesNotPublish() {
        Turista tourist = tourist();
        tourist.changeStatus(UserStatus.BLOQUEADO, NOW);
        users.seed(tourist).seed(admin());

        StepVerifier.create(
                        service().change(ADMIN_ID, tourist.id(), new ChangeUserStatusCommand(UserStatus.ACTIVO, null)))
                .verifyComplete();

        assertThat(tourist.status()).isEqualTo(UserStatus.ACTIVO);
        assertThat(blocked).isEmpty();
    }

    private ChangeUserStatusService service() {
        return new ChangeUserStatusService(
                users,
                new AuditTrailRecorder(users, auditLog),
                evictor,
                events,
                new FakeTransactionPort(),
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static Turista tourist() {
        return Turista.register(
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
}
