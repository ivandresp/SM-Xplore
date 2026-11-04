package com.smxplore.users.application.port.in;

import com.smxplore.users.application.dto.AnomalyThresholdView;
import reactor.core.publisher.Flux;

public interface ListAnomalyThresholdsUseCase {

    Flux<AnomalyThresholdView> list();
}
