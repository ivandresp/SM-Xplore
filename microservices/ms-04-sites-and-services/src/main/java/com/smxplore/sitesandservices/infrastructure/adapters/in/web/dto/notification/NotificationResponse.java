package com.smxplore.sitesandservices.infrastructure.adapters.in.web.dto.notification;

import com.smxplore.sitesandservices.domain.model.common.UserRole;
import com.smxplore.sitesandservices.domain.model.notification.NotificationType;

import java.time.Instant;
import java.util.UUID;

public record NotificationResponse(
        UUID notificationId,
        UUID recipientId,
        UserRole recipientRole,
        UUID reservationId,
        UUID eventId,
        NotificationType type,
        String title,
        String message,
        Instant createdAt,
        Instant readAt
) {
}
