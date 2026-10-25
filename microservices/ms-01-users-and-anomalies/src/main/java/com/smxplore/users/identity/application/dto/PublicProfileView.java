package com.smxplore.users.identity.application.dto;

import com.smxplore.users.identity.domain.model.Role;
import com.smxplore.users.identity.domain.model.Usuario;
import java.util.UUID;

public record PublicProfileView(UUID id, String fullName, Role role, PublicProfileDetails profile) {

    public static PublicProfileView from(Usuario user) {
        return new PublicProfileView(user.id().value(), user.fullName(), user.role(), PublicProfileDetails.from(user));
    }
}
