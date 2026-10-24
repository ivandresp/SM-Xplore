package com.smxplore.users.identity.application.dto;

import com.smxplore.users.identity.domain.model.Role;
import com.smxplore.users.identity.domain.model.Usuario;
import java.util.UUID;

public record PublicProfileView(UUID id, String fullName, Role role, RoleDetailsView profile) {

    public static PublicProfileView from(Usuario user) {
        return new PublicProfileView(user.id().value(), user.fullName(), user.role(), RoleDetailsView.from(user));
    }
}
