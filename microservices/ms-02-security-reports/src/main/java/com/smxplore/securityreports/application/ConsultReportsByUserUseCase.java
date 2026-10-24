package com.smxplore.securityreports.application;

import com.smxplore.securityreports.domain.model.SecurityReport;
import com.smxplore.securityreports.domain.ports.repository.SecurityReportRepositoryPort;
import com.smxplore.securityreports.domain.ports.usecases.ConsultReportsByUserUseCasePort;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Flux;

import java.util.UUID;

@RequiredArgsConstructor
public class ConsultReportsByUserUseCase implements ConsultReportsByUserUseCasePort {
    private final SecurityReportRepositoryPort securityReportRepo;

    @Override
    public Flux<SecurityReport> handle(UUID userId) {
        return securityReportRepo.findAllByUserId(userId);
    }


}
