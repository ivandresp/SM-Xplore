package com.smxplore.securityreports.application;

import com.smxplore.securityreports.domain.model.SecurityReport;
import com.smxplore.securityreports.domain.model.SecurityReportStatus;
import com.smxplore.securityreports.domain.ports.repository.SecurityReportRepositoryPort;
import com.smxplore.securityreports.domain.ports.usecases.ConsultReportsUseCasePort;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Flux;

@RequiredArgsConstructor
public class ConsultReportsUseCase implements ConsultReportsUseCasePort {

    private final SecurityReportRepositoryPort securityReportRepo;

    @Override
    public Flux<SecurityReport> handle(SecurityReportStatus status) {
        return securityReportRepo.findAllByStatus(status);
    }
}
