package com.smxplore.users.application.port.in;

import com.smxplore.users.application.dto.AnomalyView;
import java.util.UUID;
import reactor.core.publisher.Mono;

public interface GetAnomalyUseCase {

    Mono<AnomalyView> get(UUID anomalyId);
}
