package com.smxplore.sitesandservices.domain.ports.in;

import com.smxplore.sitesandservices.domain.model.common.Actor;
import com.smxplore.sitesandservices.domain.model.notification.Notification;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface MarkNotificationReadUseCase {

    Mono<Notification> handle(UUID notificationId, Actor actor);
}
