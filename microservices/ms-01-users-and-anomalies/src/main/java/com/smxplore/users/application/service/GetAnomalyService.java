package com.smxplore.users.application.service;

import com.smxplore.users.application.dto.AnomalyView;
import com.smxplore.users.application.port.in.GetAnomalyUseCase;
import com.smxplore.users.application.port.out.AnomalyRepository;
import com.smxplore.users.domain.exception.AnomalyNotFoundException;
import java.util.UUID;
import reactor.core.publisher.Mono;

public final class GetAnomalyService implements GetAnomalyUseCase {

    private final AnomalyRepository anomalies;

    public GetAnomalyService(AnomalyRepository anomalies) {
        this.anomalies = anomalies;
    }

    @Override
    public Mono<AnomalyView> get(UUID anomalyId) {
        return anomalies
                .findById(anomalyId)
                .switchIfEmpty(Mono.error(AnomalyNotFoundException::new))
                .map(AnomalyView::from);
    }
}
