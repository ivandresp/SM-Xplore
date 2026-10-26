package com.smxplore.recommendations.application.projection;

import com.smxplore.recommendations.domain.exception.InvalidEventException;
import com.smxplore.recommendations.domain.model.event.ProjectedEvent;
import com.smxplore.recommendations.domain.model.event.ProjectionResult;
import com.smxplore.recommendations.domain.ports.in.HandleProjectionEventUseCase;
import com.smxplore.recommendations.domain.ports.out.EventProjectionPort;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

@RequiredArgsConstructor
public class ProjectionService implements HandleProjectionEventUseCase {

    private final EventProjectionPort projectionPort;

    @Override
    public Mono<ProjectionResult> handle(ProjectedEvent event) {
        if (event == null) {
            return Mono.error(new InvalidEventException("event is required"));
        }
        return projectionPort.apply(event);
    }
}
