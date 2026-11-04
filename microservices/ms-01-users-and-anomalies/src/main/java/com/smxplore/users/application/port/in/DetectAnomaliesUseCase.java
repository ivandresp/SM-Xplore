package com.smxplore.users.application.port.in;

import reactor.core.publisher.Mono;

public interface DetectAnomaliesUseCase {

    /**
     * Evalúa los umbrales configurados y registra las anomalías que correspondan.
     *
     * @return cantidad de anomalías nuevas
     */
    Mono<Integer> detect();
}
