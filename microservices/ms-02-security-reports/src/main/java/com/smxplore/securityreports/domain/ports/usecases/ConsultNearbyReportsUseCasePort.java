package com.smxplore.securityreports.domain.ports.usecases;

import com.smxplore.securityreports.domain.model.SecurityReport;
import com.smxplore.securityreports.domain.model.types.NearbyReportsQuery;
import reactor.core.publisher.Flux;

public interface ConsultNearbyReportsUseCasePort {
    Flux<SecurityReport> handle(NearbyReportsQuery query);
}
