package com.smxplore.users.application.dto;

import com.smxplore.users.domain.model.Usuario;
import java.util.UUID;

public record PublicProfileView(UUID id, String fullName, String role, PublicProfileDetails profile) {

    public static PublicProfileView from(Usuario user) {
        return new PublicProfileView(user.id(), user.fullName(), user.role().code(), PublicProfileDetails.from(user));
    }
}
