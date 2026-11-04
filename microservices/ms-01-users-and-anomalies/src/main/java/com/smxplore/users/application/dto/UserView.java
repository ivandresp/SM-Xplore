package com.smxplore.users.application.dto;

import com.smxplore.users.domain.model.UserStatus;
import com.smxplore.users.domain.model.Usuario;
import java.time.Instant;
import java.util.UUID;

public record UserView(
        UUID id,
        String fullName,
        String email,
        String phone,
        String role,
        UserStatus status,
        RoleDetailsView profile,
        Instant signedUpAt) {

    public static UserView from(Usuario user) {
        return new UserView(
                user.id(),
                user.fullName(),
                user.email().value(),
                user.phone(),
                user.role().code(),
                user.status(),
                RoleDetailsView.from(user),
                user.signedUpAt());
    }
}
