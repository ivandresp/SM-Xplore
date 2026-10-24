package com.smxplore.users.identity.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.smxplore.users.identity.domain.exception.ProfileMismatchException;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class UserTest {

    private static final Instant NOW = Instant.parse("2026-10-24T12:00:00Z");

    @Test
    void registersTouristAsActive() {
        User user = tourist();

        assertThat(user.role()).isEqualTo(Role.TURISTA);
        assertThat(user.status()).isEqualTo(UserStatus.ACTIVO);
        assertThat(user.authVersion()).isZero();
    }

    @Test
    void registersProviderAsPending() {
        User user = User.registerProvider(
                UserId.newId(),
                "Prestadora Demo",
                new Email("provider@example.com"),
                "3001234567",
                new PasswordHash("hash"),
                "900123456",
                "Hotel",
                null,
                null,
                null,
                NOW);

        assertThat(user.role()).isEqualTo(Role.PRESTADORA_SERVICIOS);
        assertThat(((ProviderProfile) user.profile()).registrationStatus())
                .isEqualTo(ProviderRegistrationStatus.PENDIENTE);
    }

    @Test
    void changePasswordIncrementsAuthVersion() {
        User user = tourist().changePassword(new PasswordHash("new-hash"), NOW);

        assertThat(user.authVersion()).isEqualTo(1);
    }

    @Test
    void rejectsProfileFromAnotherRole() {
        User user = tourist();

        assertThatThrownBy(() ->
                        user.updateProfile(user.fullName(), user.phone(), new AdminProfile(null, null, null), NOW))
                .isInstanceOf(ProfileMismatchException.class);
    }

    private static User tourist() {
        return User.registerTourist(
                UserId.newId(),
                "Ada Turista",
                new Email("ada@example.com"),
                "3001234567",
                new PasswordHash("hash"),
                "CC",
                "1234567890",
                "es",
                null,
                "Colombia",
                NOW);
    }
}
