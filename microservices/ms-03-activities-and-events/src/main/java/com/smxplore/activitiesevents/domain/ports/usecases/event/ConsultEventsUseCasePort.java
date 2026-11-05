package com.smxplore.activitiesevents.domain.ports.usecases.event;

import com.smxplore.activitiesevents.domain.model.common.PageResult;
import com.smxplore.activitiesevents.domain.model.event.Event;

import reactor.core.publisher.Mono;

public interface ConsultEventsUseCasePort {
    Mono<PageResult<Event>> handle(int page, int size);
}
