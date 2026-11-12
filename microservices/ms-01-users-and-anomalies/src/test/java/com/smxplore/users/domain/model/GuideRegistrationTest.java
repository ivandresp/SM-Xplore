package com.smxplore.users.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.smxplore.users.domain.exception.InvalidStatusTransitionException;
import com.smxplore.users.domain.exception.ValidationException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class GuideRegistrationTest {

    private static final Instant NOW = Instant.parse("2026-11-08T00:00:00Z");
    private static final Instant LATER = NOW.plusSeconds(3_600);
    private static final UUID GUIDE_ID = UUID.fromString("00000000-0000-0000-0000-000000000101");

    @Test
    void publicRegistrationStartsActiveAndPending() {
        Guia guide = newGuide();

        assertThat(guide.status()).isEqualTo(UserStatus.ACTIVO);
        assertThat(guide.registrationStatus()).isEqualTo(RegistrationStatus.PENDIENTE);
        assertThat(guide.authVersion()).isZero();
        assertThat(guide.role()).isEqualTo(Role.GUIDE);
    }

    @Test
    void administrativeCreationCanStartApproved() {
        Guia guide = Guia.register(
                GUIDE_ID,
                "Guía Aprobada",
                new Email("guia.aprobada@example.com"),
                "3005550201",
                new PasswordHash("hash"),
                "CC",
                "1000000201",
                "LIC-201",
                LocalDate.parse("2024-02-01"),
                RegistrationStatus.APROBADO,
                NOW);

        assertThat(guide.registrationStatus()).isEqualTo(RegistrationStatus.APROBADO);
    }

    @Test
    void approvingPendingRegistrationBumpsAuthVersionAndUpdatedAt() {
        Guia guide = newGuide();
        long previousVersion = guide.authVersion();

        guide.changeRegistrationStatus(RegistrationStatus.APROBADO, LATER);

        assertThat(guide.registrationStatus()).isEqualTo(RegistrationStatus.APROBADO);
        assertThat(guide.authVersion()).isEqualTo(previousVersion + 1);
        assertThat(guide.updatedAt()).isEqualTo(LATER);
    }

    @Test
    void rehydratePreservesTheStoredStatus() {
        Guia guide = Guia.rehydrate(
                GUIDE_ID,
                "Guía Rechazada",
                new Email("guia.rechazada@example.com"),
                "3005550202",
                new PasswordHash("hash"),
                UserStatus.ACTIVO,
                4L,
                NOW,
                LATER,
                null,
                "CC",
                "1000000202",
                "LIC-202",
                LocalDate.parse("2024-03-01"),
                RegistrationStatus.RECHAZADO);

        assertThat(guide.registrationStatus()).isEqualTo(RegistrationStatus.RECHAZADO);
        assertThat(guide.authVersion()).isEqualTo(4L);
    }

    @Test
    void rejectsRepeatingTheSameDecision() {
        Guia guide = newGuide();
        guide.changeRegistrationStatus(RegistrationStatus.APROBADO, LATER);

        assertThatThrownBy(() -> guide.changeRegistrationStatus(RegistrationStatus.APROBADO, LATER))
                .isInstanceOf(InvalidStatusTransitionException.class)
                .hasMessageContaining("APROBADO");
    }

    @Test
    void rejectsANullStatus() {
        Guia guide = newGuide();

        assertThatThrownBy(() -> guide.changeRegistrationStatus(null, LATER)).isInstanceOf(ValidationException.class);
    }

    @Test
    void canCorrectAPreviousDecision() {
        Guia guide = newGuide();
        guide.changeRegistrationStatus(RegistrationStatus.RECHAZADO, LATER);

        guide.changeRegistrationStatus(RegistrationStatus.APROBADO, LATER.plusSeconds(60));

        assertThat(guide.registrationStatus()).isEqualTo(RegistrationStatus.APROBADO);
        assertThat(guide.authVersion()).isEqualTo(2L);
    }

    private static Guia newGuide() {
        return Guia.register(
                GUIDE_ID,
                "Guía Uno",
                new Email("guia.uno@example.com"),
                "3005550200",
                new PasswordHash("hash"),
                "CC",
                "1000000200",
                "LIC-200",
                LocalDate.parse("2024-01-01"),
                NOW);
    }
}
