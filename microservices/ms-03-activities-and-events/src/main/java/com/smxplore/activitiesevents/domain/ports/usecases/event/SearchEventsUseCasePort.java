package com.smxplore.activitiesevents.domain.ports.usecases.event;

import com.smxplore.activitiesevents.domain.model.common.PageResult;
import com.smxplore.activitiesevents.domain.model.event.Event;

import reactor.core.publisher.Mono;

public interface SearchEventsUseCasePort {
    Mono<PageResult<Event>> handle(String name, int page, int size);
}
