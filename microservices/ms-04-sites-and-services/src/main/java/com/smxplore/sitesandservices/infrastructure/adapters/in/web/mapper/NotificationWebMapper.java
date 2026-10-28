package com.smxplore.sitesandservices.infrastructure.adapters.in.web.mapper;

import com.smxplore.sitesandservices.domain.model.notification.Notification;
import com.smxplore.sitesandservices.infrastructure.adapters.in.web.dto.notification.NotificationResponse;
import org.springframework.stereotype.Component;

@Component
public class NotificationWebMapper {

    public NotificationResponse toResponse(Notification notification) {
        return new NotificationResponse(
                notification.notificationId(),
                notification.recipientId(),
                notification.recipientRole(),
                notification.reservationId(),
                notification.eventId(),
                notification.type(),
                notification.title(),
                notification.message(),
                notification.createdAt(),
                notification.readAt()
        );
    }
}
