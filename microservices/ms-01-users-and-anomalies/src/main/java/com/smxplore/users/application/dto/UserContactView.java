package com.smxplore.users.application.dto;

import com.smxplore.users.domain.model.Usuario;
import java.util.UUID;

/**
 * Datos de contacto de una cuenta para el endpoint interno consumido por otros microservicios.
 *
 * <p>No incluye contraseñas, documentos ni secretos MFA: solo lo necesario para notificar al usuario.
 */
public record UserContactView(UUID userId, String email, String fullName, String role, String phone, boolean active) {

    public static UserContactView from(Usuario user) {
        return new UserContactView(
                user.id(), user.email().value(), user.fullName(), user.role().code(), user.phone(), user.isActive());
    }
}
