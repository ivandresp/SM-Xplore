package com.smxplore.recommendations.application.projection;

import com.smxplore.recommendations.domain.exception.InvalidEventException;
import com.smxplore.recommendations.domain.model.event.ProjectedEvent;
import com.smxplore.recommendations.domain.model.event.ProjectionResult;
import com.smxplore.recommendations.domain.ports.in.HandleProjectionEventUseCase;
import com.smxplore.recommendations.domain.ports.out.EventProjectionPort;
import com.smxplore.recommendations.domain.ports.out.GraphVersionPort;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

@RequiredArgsConstructor
public class ProjectionService implements HandleProjectionEventUseCase {

    private final EventProjectionPort projectionPort;
    private final GraphVersionPort graphVersion;

    @Override
    public Mono<ProjectionResult> handle(ProjectedEvent event) {
        if (event == null) {
            return Mono.error(new InvalidEventException("event is required"));
        }
        return projectionPort.apply(event)
                .flatMap(this::bumpVersionWhenApplied);
    }

    /**
     * Only an applied event changes the graph. A duplicate or stale event leaves every cached
     * recommendation valid, so the version stays where it is.
     */
    private Mono<ProjectionResult> bumpVersionWhenApplied(ProjectionResult result) {
        if (result != ProjectionResult.APPLIED) {
            return Mono.just(result);
        }
        return graphVersion.bump().thenReturn(result);
    }
}
