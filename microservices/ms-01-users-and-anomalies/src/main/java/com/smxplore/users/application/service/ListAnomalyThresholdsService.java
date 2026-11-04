package com.smxplore.users.application.service;

import com.smxplore.users.application.dto.AnomalyThresholdView;
import com.smxplore.users.application.port.in.ListAnomalyThresholdsUseCase;
import com.smxplore.users.application.port.out.AnomalyThresholdRepository;
import com.smxplore.users.domain.model.AnomalyThreshold;
import com.smxplore.users.domain.model.AnomalyType;
import java.time.Clock;
import reactor.core.publisher.Flux;

public final class ListAnomalyThresholdsService implements ListAnomalyThresholdsUseCase {

    private final AnomalyThresholdRepository thresholds;
    private final Clock clock;

    public ListAnomalyThresholdsService(AnomalyThresholdRepository thresholds, Clock clock) {
        this.thresholds = thresholds;
        this.clock = clock;
    }

    @Override
    public Flux<AnomalyThresholdView> list() {
        return Flux.fromArray(AnomalyType.values())
                .concatMap(type ->
                        thresholds.findByType(type).defaultIfEmpty(AnomalyThreshold.defaults(type, clock.instant())))
                .map(AnomalyThresholdView::from);
    }
}
