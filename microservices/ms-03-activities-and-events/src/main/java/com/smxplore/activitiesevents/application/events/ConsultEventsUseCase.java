package com.smxplore.activitiesevents.application.events;

import com.smxplore.activitiesevents.domain.model.common.PageResult;
import com.smxplore.activitiesevents.domain.model.event.Event;
import com.smxplore.activitiesevents.domain.ports.repository.EventRepositoryPort;
import com.smxplore.activitiesevents.domain.ports.usecases.event.ConsultEventsUseCasePort;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

@RequiredArgsConstructor
public class ConsultEventsUseCase implements ConsultEventsUseCasePort {

    private final EventRepositoryPort eventRepo;

    @Override
    public Mono<PageResult<Event>> handle(int page, int size) {
        return eventRepo.findPublishedPage(page, size);
    }
}
