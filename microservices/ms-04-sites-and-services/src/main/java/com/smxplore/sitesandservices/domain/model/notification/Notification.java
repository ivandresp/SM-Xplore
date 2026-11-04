package com.smxplore.sitesandservices.domain.model.notification;

import com.smxplore.sitesandservices.domain.exception.InvalidCatalogException;
import com.smxplore.sitesandservices.domain.model.common.UserRole;
import lombok.Builder;
import lombok.Getter;
import lombok.experimental.Accessors;

import java.time.Instant;
import java.util.UUID;

@Getter
@Accessors(fluent = true)
@Builder(toBuilder = true)
public final class Notification {

    private final UUID notificationId;
    private final UUID recipientId;
    private final UserRole recipientRole;
    private final UUID reservationId;
    private final UUID eventId;
    private final NotificationType type;
    private final String title;
    private final String message;
    private final Instant createdAt;
    private final Instant readAt;

    public void validate() {
        validateId(notificationId);
        validateRecipient(recipientId, recipientRole);
        validateReferences(reservationId, eventId);
        validateType(type);
        validateText("title", title, 200);
        validateText("message", message, 1000);
        validateCreatedAt(createdAt);
        validateReadAt(createdAt, readAt);
    }

    public Notification markRead(Instant now) {
        if (readAt != null) {
            return this;
        }
        return toBuilder().readAt(now).build();
    }

    private static void validateId(UUID notificationId) {
        if (notificationId == null) {
            throw new InvalidCatalogException("notificationId is required");
        }
    }

    private static void validateRecipient(UUID recipientId, UserRole recipientRole) {
        if (recipientId == null) {
            throw new InvalidCatalogException("notification recipientId is required");
        }
        if (recipientRole == null) {
            throw new InvalidCatalogException("notification recipientRole is required");
        }
    }

    private static void validateReferences(UUID reservationId, UUID eventId) {
        if (reservationId == null) {
            throw new InvalidCatalogException("notification reservationId is required");
        }
        if (eventId == null) {
            throw new InvalidCatalogException("notification eventId is required");
        }
    }

    private static void validateType(NotificationType type) {
        if (type == null) {
            throw new InvalidCatalogException("notification type is required");
        }
    }

    private static void validateText(String field, String value, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new InvalidCatalogException("notification " + field + " is required");
        }
        if (value.length() > maxLength) {
            throw new InvalidCatalogException(
                    "notification " + field + " cannot exceed " + maxLength + " characters");
        }
    }

    private static void validateCreatedAt(Instant createdAt) {
        if (createdAt == null) {
            throw new InvalidCatalogException("notification createdAt is required");
        }
    }

    private static void validateReadAt(Instant createdAt, Instant readAt) {
        if (readAt != null && readAt.isBefore(createdAt)) {
            throw new InvalidCatalogException(
                    "notification readAt cannot be before createdAt");
        }
    }
}
