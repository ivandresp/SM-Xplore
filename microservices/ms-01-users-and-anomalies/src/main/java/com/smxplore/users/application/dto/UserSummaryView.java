package com.smxplore.users.application.dto;

import com.smxplore.users.domain.model.PrestadoraServicios;
import com.smxplore.users.domain.model.ProviderRegistrationStatus;
import com.smxplore.users.domain.model.UserStatus;
import com.smxplore.users.domain.model.Usuario;
import java.time.Instant;
import java.util.UUID;

public record UserSummaryView(
        UUID id,
        String fullName,
        String email,
        String role,
        UserStatus status,
        ProviderRegistrationStatus providerStatus,
        Instant signedUpAt) {

    public static UserSummaryView from(Usuario user) {
        ProviderRegistrationStatus providerStatus =
                user instanceof PrestadoraServicios provider ? provider.registrationStatus() : null;
        return new UserSummaryView(
                user.id(),
                user.fullName(),
                user.email().value(),
                user.role().code(),
                user.status(),
                providerStatus,
                user.signedUpAt());
    }
}
