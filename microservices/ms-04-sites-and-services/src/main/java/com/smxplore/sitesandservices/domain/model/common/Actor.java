package com.smxplore.sitesandservices.domain.model.common;

import com.smxplore.sitesandservices.domain.exception.InvalidJwtException;

import java.util.UUID;

public record Actor(
        UUID userId,
        UserRole role,
        String name
) {

    public Actor {
        validateUserId(userId);
        validateRole(role);
    }

    public boolean isAdmin() {
        return role == UserRole.ADMIN;
    }

    public boolean isGestora() {
        return role == UserRole.GESTORA_DESTINOS;
    }

    public boolean isPrestadora() {
        return role == UserRole.PRESTADORA_SERVICIOS;
    }

    public boolean isTourist() {
        return role == UserRole.TURISTA;
    }

    public boolean owns(UUID ownerId) {
        return userId.equals(ownerId);
    }

    public String displayName() {
        return name == null || name.isBlank() ? "Usuario" : name;
    }

    private static void validateUserId(UUID userId) {
        if (userId == null) {
            throw new InvalidJwtException("JWT user id is required");
        }
    }

    private static void validateRole(UserRole role) {
        if (role == null) {
            throw new InvalidJwtException("JWT role is required");
        }
    }
}
