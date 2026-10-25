package com.smxplore.users.identity.application.dto;

import com.smxplore.users.identity.domain.model.PrestadoraServicios;
import com.smxplore.users.identity.domain.model.ProviderRegistrationStatus;
import com.smxplore.users.identity.domain.model.Role;
import com.smxplore.users.identity.domain.model.UserStatus;
import com.smxplore.users.identity.domain.model.Usuario;
import java.time.Instant;
import java.util.UUID;

public record UserSummaryView(
        UUID id,
        String fullName,
        String email,
        Role role,
        UserStatus status,
        ProviderRegistrationStatus providerStatus,
        Instant signedUpAt) {

    public static UserSummaryView from(Usuario user) {
        ProviderRegistrationStatus providerStatus =
                user instanceof PrestadoraServicios provider ? provider.registrationStatus() : null;
        return new UserSummaryView(
                user.id().value(),
                user.fullName(),
                user.email().value(),
                user.role(),
                user.status(),
                providerStatus,
                user.signedUpAt());
    }
}
