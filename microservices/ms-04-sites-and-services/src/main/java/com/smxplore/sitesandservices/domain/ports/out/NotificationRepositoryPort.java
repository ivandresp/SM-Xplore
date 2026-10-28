package com.smxplore.sitesandservices.domain.ports.out;

import com.smxplore.sitesandservices.domain.model.common.PageResult;
import com.smxplore.sitesandservices.domain.model.notification.Notification;
import com.smxplore.sitesandservices.domain.model.notification.NotificationFilter;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

public interface NotificationRepositoryPort {

    Mono<Void> saveIfAbsent(Notification notification);

    Mono<Notification> findById(UUID notificationId);

    Mono<PageResult<Notification>> findAll(NotificationFilter filter);

    Mono<Void> markRead(UUID notificationId, Instant readAt);
}
