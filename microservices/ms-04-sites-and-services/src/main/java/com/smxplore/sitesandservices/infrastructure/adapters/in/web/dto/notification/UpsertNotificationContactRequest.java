package com.smxplore.sitesandservices.infrastructure.adapters.in.web.dto.notification;

import com.smxplore.sitesandservices.domain.model.notification.NotificationContact;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * MS04-09: {@code confirm} es obligatorio para cambiar un correo de contacto, de modo que un
 * administrador no pueda redirigir notificaciones de otro usuario con una sola peticion.
 */
public record UpsertNotificationContactRequest(
        @NotBlank
        @Email
        @Size(max = NotificationContact.MAX_EMAIL_LENGTH)
        String email,
        boolean confirm
) {

    public UpsertNotificationContactRequest {
        email = email == null ? null : email.trim();
    }
}
