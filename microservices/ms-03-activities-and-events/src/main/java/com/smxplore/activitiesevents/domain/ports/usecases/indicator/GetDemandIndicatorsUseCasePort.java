package com.smxplore.activitiesevents.domain.ports.usecases.indicator;

import com.smxplore.activitiesevents.domain.model.indicator.DemandFilter;
import com.smxplore.activitiesevents.domain.model.indicator.DemandIndicators;
import com.smxplore.activitiesevents.domain.model.user.UserRole;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface GetDemandIndicatorsUseCasePort {

    Mono<DemandIndicators> handle(DemandFilter filter, UUID userId, UserRole role, String bearerToken);
}
