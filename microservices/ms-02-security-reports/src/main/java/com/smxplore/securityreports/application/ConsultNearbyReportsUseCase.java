package com.smxplore.securityreports.application;

import com.smxplore.securityreports.domain.model.SecurityReport;
import com.smxplore.securityreports.domain.model.types.NearbyReportsQuery;
import com.smxplore.securityreports.domain.ports.repository.SecurityReportRepositoryPort;
import com.smxplore.securityreports.domain.ports.usecases.ConsultNearbyReportsUseCasePort;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Flux;

@RequiredArgsConstructor
public class ConsultNearbyReportsUseCase implements ConsultNearbyReportsUseCasePort {

    private final SecurityReportRepositoryPort securityReportRepo;

    @Override
    public Flux<SecurityReport> handle(NearbyReportsQuery query) {
        return securityReportRepo.findNearby(query);
    }
}
