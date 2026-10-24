package com.smxplore.users.identity.application.dto;

import com.smxplore.users.identity.domain.model.Role;
import com.smxplore.users.identity.domain.model.UserStatus;
import com.smxplore.users.identity.domain.model.Usuario;
import java.time.Instant;
import java.util.UUID;

public record UserView(
        UUID id,
        String fullName,
        String email,
        String phone,
        Role role,
        UserStatus status,
        RoleDetailsView profile,
        Instant signedUpAt) {

    public static UserView from(Usuario user) {
        return new UserView(
                user.id().value(),
                user.fullName(),
                user.email().value(),
                user.phone(),
                user.role(),
                user.status(),
                RoleDetailsView.from(user),
                user.signedUpAt());
    }
}
