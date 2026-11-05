package com.smxplore.activitiesevents.application.events;

import com.smxplore.activitiesevents.domain.exceptions.event.InvalidEventRequestException;
import com.smxplore.activitiesevents.domain.model.common.PageResult;
import com.smxplore.activitiesevents.domain.model.event.Event;
import com.smxplore.activitiesevents.domain.ports.repository.EventRepositoryPort;
import com.smxplore.activitiesevents.domain.ports.usecases.event.SearchEventsUseCasePort;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

@RequiredArgsConstructor
public class SearchEventsUseCase implements SearchEventsUseCasePort {

    private final EventRepositoryPort eventRepo;

    @Override
    public Mono<PageResult<Event>> handle(String name, int page, int size) {
        if (name == null || name.isBlank()) {
            return Mono.error(new InvalidEventRequestException("Name must not be blank."));
        }
        return eventRepo.searchPublishedByNamePage(name, page, size);
    }
}
