package com.smxplore.users.application.port.in;

import com.smxplore.users.application.dto.ReputationSummaryView;
import com.smxplore.users.domain.model.ReviewTargetType;
import java.util.UUID;
import reactor.core.publisher.Mono;

public interface GetReputationSummaryUseCase {

    Mono<ReputationSummaryView> get(ReviewTargetType targetType, UUID targetId);
}
