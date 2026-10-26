package com.smxplore.recommendations.domain.ports.in;

import com.smxplore.recommendations.domain.model.event.ProjectedEvent;
import com.smxplore.recommendations.domain.model.event.ProjectionResult;
import reactor.core.publisher.Mono;

public interface HandleProjectionEventUseCase {

    Mono<ProjectionResult> handle(ProjectedEvent event);
}
