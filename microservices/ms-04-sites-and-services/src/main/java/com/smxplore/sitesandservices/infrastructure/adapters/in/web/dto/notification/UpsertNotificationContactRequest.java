package com.smxplore.sitesandservices.infrastructure.adapters.in.web.dto.notification;

import com.smxplore.sitesandservices.domain.model.notification.NotificationContact;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpsertNotificationContactRequest(
        @NotBlank
        @Email
        @Size(max = NotificationContact.MAX_EMAIL_LENGTH)
        String email
) {

    public UpsertNotificationContactRequest {
        email = email == null ? null : email.trim();
    }
}
