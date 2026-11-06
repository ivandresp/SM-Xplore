package com.smxplore.recommendations.domain.ports.out;

import com.smxplore.recommendations.domain.model.event.ProjectedEvent;
import com.smxplore.recommendations.domain.model.event.ProjectionResult;
import reactor.core.publisher.Mono;

public interface EventProjectionPort {

    Mono<ProjectionResult> apply(ProjectedEvent event);
}
