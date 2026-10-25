package com.smxplore.activitiesevents.application.events;

import com.smxplore.activitiesevents.domain.exceptions.event.InvalidEventRequestException;
import com.smxplore.activitiesevents.domain.model.event.Event;
import com.smxplore.activitiesevents.domain.model.event.EventStatus;
import com.smxplore.activitiesevents.domain.ports.repository.EventRepositoryPort;
import com.smxplore.activitiesevents.domain.ports.usecases.event.SearchEventsUseCasePort;

import lombok.RequiredArgsConstructor;

import reactor.core.publisher.Flux;

@RequiredArgsConstructor
public class SearchEventsUseCase implements SearchEventsUseCasePort {

    private final EventRepositoryPort eventRepo;

    @Override
    public Flux<Event> handle(String name) {
        if (name == null || name.isBlank()) {
            return Flux.error(new InvalidEventRequestException("Name must not be blank."));
        }
        return eventRepo.searchByName(name)
                .filter(event -> event.getStatus() == EventStatus.PUBLISHED);
    }
}
