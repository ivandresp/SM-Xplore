package com.smxplore.sitesandservices.application.notification;

import com.smxplore.sitesandservices.domain.model.common.Actor;
import com.smxplore.sitesandservices.domain.model.common.PageResult;
import com.smxplore.sitesandservices.domain.model.notification.Notification;
import com.smxplore.sitesandservices.domain.model.notification.NotificationFilter;
import com.smxplore.sitesandservices.domain.ports.in.ListNotificationsUseCase;
import com.smxplore.sitesandservices.domain.ports.out.NotificationRepositoryPort;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

@RequiredArgsConstructor
public class NotificationListService implements ListNotificationsUseCase {

    private final NotificationRepositoryPort notificationRepository;
    private final NotificationAccess access;

    @Override
    public Mono<PageResult<Notification>> handle(NotificationFilter filter, Actor actor) {
        return access.scope(filter, actor)
                .flatMap(notificationRepository::findAll);
    }
}
