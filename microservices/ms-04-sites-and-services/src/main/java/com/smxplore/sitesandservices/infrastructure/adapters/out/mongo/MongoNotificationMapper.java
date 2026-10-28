package com.smxplore.sitesandservices.infrastructure.adapters.out.mongo;

import com.smxplore.sitesandservices.domain.model.common.UserRole;
import com.smxplore.sitesandservices.domain.model.notification.Notification;
import com.smxplore.sitesandservices.domain.model.notification.NotificationType;
import com.smxplore.sitesandservices.infrastructure.adapters.out.mongo.document.NotificationDocument;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class MongoNotificationMapper {

    public NotificationDocument toDocument(Notification notification) {
        return NotificationDocument.builder()
                .notificationId(notification.notificationId().toString())
                .recipientId(notification.recipientId().toString())
                .recipientRole(notification.recipientRole().name())
                .reservationId(notification.reservationId().toString())
                .eventId(notification.eventId().toString())
                .type(notification.type().name())
                .title(notification.title())
                .message(notification.message())
                .createdAt(notification.createdAt())
                .readAt(notification.readAt())
                .build();
    }

    public Notification toDomain(NotificationDocument document) {
        return Notification.builder()
                .notificationId(UUID.fromString(document.getNotificationId()))
                .recipientId(UUID.fromString(document.getRecipientId()))
                .recipientRole(UserRole.valueOf(document.getRecipientRole()))
                .reservationId(UUID.fromString(document.getReservationId()))
                .eventId(UUID.fromString(document.getEventId()))
                .type(NotificationType.valueOf(document.getType()))
                .title(document.getTitle())
                .message(document.getMessage())
                .createdAt(document.getCreatedAt())
                .readAt(document.getReadAt())
                .build();
    }
}
