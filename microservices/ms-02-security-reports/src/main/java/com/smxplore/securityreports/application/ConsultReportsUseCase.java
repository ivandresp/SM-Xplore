package com.smxplore.securityreports.application;

import com.smxplore.securityreports.domain.model.SecurityReport;
import com.smxplore.securityreports.domain.model.SecurityReportStatus;
import com.smxplore.securityreports.domain.model.types.Page;
import com.smxplore.securityreports.domain.model.types.PageRequest;
import com.smxplore.securityreports.domain.ports.repository.SecurityReportRepositoryPort;
import com.smxplore.securityreports.domain.ports.usecases.ConsultReportsUseCasePort;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

@RequiredArgsConstructor
public class ConsultReportsUseCase implements ConsultReportsUseCasePort {

    private final SecurityReportRepositoryPort securityReportRepo;

    @Override
    public Mono<Page<SecurityReport>> handle(SecurityReportStatus status, PageRequest pageRequest) {
        return securityReportRepo.findAllByStatus(status, pageRequest);
    }
}
