package com.smxplore.users.application.port.in;

import com.smxplore.users.application.dto.AnomalyView;
import com.smxplore.users.application.dto.ClassifyAnomalyCommand;
import java.util.UUID;
import reactor.core.publisher.Mono;

public interface ClassifyAnomalyUseCase {

    Mono<AnomalyView> classify(UUID adminId, UUID anomalyId, ClassifyAnomalyCommand command);
}
