package com.smxplore.securityreports.application;

import com.smxplore.securityreports.domain.model.SecurityReport;
import com.smxplore.securityreports.domain.model.types.Page;
import com.smxplore.securityreports.domain.model.types.PageRequest;
import com.smxplore.securityreports.domain.ports.repository.SecurityReportRepositoryPort;
import com.smxplore.securityreports.domain.ports.usecases.ConsultReportsByUserUseCasePort;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

import java.util.UUID;

@RequiredArgsConstructor
public class ConsultReportsByUserUseCase implements ConsultReportsByUserUseCasePort {
    private final SecurityReportRepositoryPort securityReportRepo;

    @Override
    public Mono<Page<SecurityReport>> handle(UUID userId, PageRequest pageRequest) {
        return securityReportRepo.findAllByUserId(userId, pageRequest);
    }
}
