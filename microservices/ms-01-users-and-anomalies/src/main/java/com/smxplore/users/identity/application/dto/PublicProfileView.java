package com.smxplore.users.identity.application.dto;

import com.smxplore.users.identity.domain.model.Role;
import com.smxplore.users.identity.domain.model.User;
import java.util.UUID;

public record PublicProfileView(UUID id, String fullName, Role role, ProfileView profile) {

    public static PublicProfileView from(User user) {
        return new PublicProfileView(
                user.id().value(), user.fullName().value(), user.role(), ProfileView.from(user.profile()));
    }
}
