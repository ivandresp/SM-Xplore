package com.smxplore.users.application.port.in;

import com.smxplore.users.application.dto.AnomalyView;
import com.smxplore.users.application.dto.ResolveAnomalyCommand;
import java.util.UUID;
import reactor.core.publisher.Mono;

public interface ResolveAnomalyUseCase {

    Mono<AnomalyView> resolve(UUID adminId, UUID anomalyId, ResolveAnomalyCommand command);
}
