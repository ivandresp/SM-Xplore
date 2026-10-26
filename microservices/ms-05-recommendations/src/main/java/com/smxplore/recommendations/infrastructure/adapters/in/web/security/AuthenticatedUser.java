package com.smxplore.recommendations.infrastructure.adapters.in.web.security;

import java.util.UUID;

public record AuthenticatedUser(
        UUID touristId,
        String role,
        String name,
        String language
) {

    public boolean isAdmin() {
        return "ADMIN".equals(role);
    }
}
