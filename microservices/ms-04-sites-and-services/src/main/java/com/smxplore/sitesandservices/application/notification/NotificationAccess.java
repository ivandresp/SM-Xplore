package com.smxplore.sitesandservices.application.notification;

import com.smxplore.sitesandservices.domain.exception.ForbiddenOperationException;
import com.smxplore.sitesandservices.domain.model.common.Actor;
import com.smxplore.sitesandservices.domain.model.notification.Notification;
import com.smxplore.sitesandservices.domain.model.notification.NotificationFilter;
import reactor.core.publisher.Mono;

public class NotificationAccess {

    public Mono<NotificationFilter> scope(NotificationFilter filter, Actor actor) {
        return Mono.just(new NotificationFilter(
                actor.userId(),
                filter.unreadOnly(),
                filter.page(),
                filter.size()
        ));
    }

    public Mono<Notification> requireOwner(Notification notification, Actor actor) {
        return actor.isAdmin() || notification.recipientId().equals(actor.userId())
                ? Mono.just(notification)
                : Mono.error(new ForbiddenOperationException(
                        "Only the notification recipient can mark it as read"));
    }
}
