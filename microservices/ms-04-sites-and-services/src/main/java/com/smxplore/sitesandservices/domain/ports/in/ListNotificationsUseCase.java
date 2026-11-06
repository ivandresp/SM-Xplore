package com.smxplore.sitesandservices.domain.ports.in;

import com.smxplore.sitesandservices.domain.model.common.Actor;
import com.smxplore.sitesandservices.domain.model.common.PageResult;
import com.smxplore.sitesandservices.domain.model.notification.Notification;
import com.smxplore.sitesandservices.domain.model.notification.NotificationFilter;
import reactor.core.publisher.Mono;

public interface ListNotificationsUseCase {

    Mono<PageResult<Notification>> handle(NotificationFilter filter, Actor actor);
}
