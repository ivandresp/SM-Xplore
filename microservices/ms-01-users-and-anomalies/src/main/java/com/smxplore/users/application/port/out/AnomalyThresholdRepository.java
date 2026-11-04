package com.smxplore.users.application.port.out;

import com.smxplore.users.domain.model.AnomalyThreshold;
import com.smxplore.users.domain.model.AnomalyType;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface AnomalyThresholdRepository {

    Flux<AnomalyThreshold> findAll();

    Mono<AnomalyThreshold> findByType(AnomalyType type);

    Mono<AnomalyThreshold> save(AnomalyThreshold threshold);
}
