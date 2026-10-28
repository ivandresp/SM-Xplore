package com.smxplore.sitesandservices.application.notification;

import com.smxplore.sitesandservices.domain.exception.InvalidCatalogException;
import com.smxplore.sitesandservices.domain.exception.ResourceNotFoundException;
import com.smxplore.sitesandservices.domain.model.common.Actor;
import com.smxplore.sitesandservices.domain.model.notification.Notification;
import com.smxplore.sitesandservices.domain.ports.in.MarkNotificationReadUseCase;
import com.smxplore.sitesandservices.domain.ports.out.NotificationRepositoryPort;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

@RequiredArgsConstructor
public class NotificationMarkReadService implements MarkNotificationReadUseCase {

    private final NotificationRepositoryPort notificationRepository;
    private final NotificationAccess access;
    private final Clock clock;

    @Override
    public Mono<Notification> handle(UUID notificationId, Actor actor) {
        if (notificationId == null) {
            return Mono.error(new InvalidCatalogException("notificationId is required"));
        }
        return notificationRepository.findById(notificationId)
                .switchIfEmpty(Mono.error(
                        new ResourceNotFoundException("Notification", notificationId)))
                .flatMap(notification -> access.requireOwner(notification, actor))
                .flatMap(notification -> {
                    if (notification.readAt() != null) {
                        return Mono.just(notification);
                    }
                    Instant now = Instant.now(clock);
                    return notificationRepository.markRead(notification.notificationId(), now)
                            .thenReturn(notification.markRead(now));
                });
    }
}
