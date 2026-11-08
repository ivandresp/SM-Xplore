package com.smxplore.securityreports.application;

import com.smxplore.securityreports.domain.model.SecurityReportStatus;
import com.smxplore.securityreports.domain.ports.repository.SecurityReportRepositoryPort;
import com.smxplore.securityreports.domain.ports.usecases.ConsultReportCountsUseCasePort;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

import java.util.Map;

@RequiredArgsConstructor
public class ConsultReportCountsUseCase implements ConsultReportCountsUseCasePort {

    private final SecurityReportRepositoryPort securityReportRepo;

    @Override
    public Mono<Map<SecurityReportStatus, Long>> handle() {
        return securityReportRepo.countByStatus();
    }
}
