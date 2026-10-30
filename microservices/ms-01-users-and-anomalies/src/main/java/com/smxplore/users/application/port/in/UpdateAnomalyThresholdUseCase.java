package com.smxplore.users.application.port.in;

import com.smxplore.users.application.dto.AnomalyThresholdView;
import com.smxplore.users.application.dto.UpdateAnomalyThresholdCommand;
import com.smxplore.users.domain.model.AnomalyType;
import java.util.UUID;
import reactor.core.publisher.Mono;

public interface UpdateAnomalyThresholdUseCase {

    Mono<AnomalyThresholdView> update(UUID adminId, AnomalyType type, UpdateAnomalyThresholdCommand command);
}
