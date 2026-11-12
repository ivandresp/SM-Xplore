package com.smxplore.users.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.smxplore.users.domain.exception.InvalidStatusTransitionException;
import com.smxplore.users.domain.exception.ValidationException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class GestoraRegistrationTest {

    private static final Instant NOW = Instant.parse("2026-11-08T00:00:00Z");
    private static final Instant LATER = NOW.plusSeconds(3_600);
    private static final UUID MANAGER_ID = UUID.fromString("00000000-0000-0000-0000-000000000201");

    @Test
    void publicRegistrationStartsActiveAndPending() {
        GestoraDestinos gestora = newGestora();

        assertThat(gestora.status()).isEqualTo(UserStatus.ACTIVO);
        assertThat(gestora.registrationStatus()).isEqualTo(RegistrationStatus.PENDIENTE);
        assertThat(gestora.authVersion()).isZero();
        assertThat(gestora.role()).isEqualTo(Role.DESTINATION_MANAGER);
    }

    @Test
    void rehydratePreservesTheStoredStatus() {
        GestoraDestinos gestora = GestoraDestinos.rehydrate(
                MANAGER_ID,
                "Gestora Aprobada",
                new Email("gestora.aprobada@example.com"),
                "3005550302",
                new PasswordHash("hash"),
                UserStatus.ACTIVO,
                2L,
                NOW,
                LATER,
                null,
                "900111222-3",
                LocalDate.parse("2024-04-01"),
                RegistrationStatus.APROBADO);

        assertThat(gestora.registrationStatus()).isEqualTo(RegistrationStatus.APROBADO);
        assertThat(gestora.authVersion()).isEqualTo(2L);
    }

    @Test
    void approvingPendingRegistrationBumpsAuthVersion() {
        GestoraDestinos gestora = newGestora();

        gestora.changeRegistrationStatus(RegistrationStatus.APROBADO, LATER);

        assertThat(gestora.registrationStatus()).isEqualTo(RegistrationStatus.APROBADO);
        assertThat(gestora.authVersion()).isEqualTo(1L);
        assertThat(gestora.updatedAt()).isEqualTo(LATER);
    }

    @Test
    void rejectsRepeatingTheSameDecision() {
        GestoraDestinos gestora = newGestora();
        gestora.changeRegistrationStatus(RegistrationStatus.RECHAZADO, LATER);

        assertThatThrownBy(() -> gestora.changeRegistrationStatus(RegistrationStatus.RECHAZADO, LATER))
                .isInstanceOf(InvalidStatusTransitionException.class);
    }

    @Test
    void rejectsANullStatus() {
        assertThatThrownBy(() -> newGestora().changeRegistrationStatus(null, LATER))
                .isInstanceOf(ValidationException.class);
    }

    private static GestoraDestinos newGestora() {
        return GestoraDestinos.register(
                MANAGER_ID,
                "Gestora Uno",
                new Email("gestora.uno@example.com"),
                "3005550300",
                new PasswordHash("hash"),
                "900111222-3",
                LocalDate.parse("2024-01-01"),
                NOW);
    }
}
