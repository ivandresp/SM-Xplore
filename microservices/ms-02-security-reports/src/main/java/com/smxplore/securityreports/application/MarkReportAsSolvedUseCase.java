package com.smxplore.securityreports.application;

import com.smxplore.securityreports.domain.exceptions.ReportNotFoundException;
import com.smxplore.securityreports.domain.model.SecurityReport;
import com.smxplore.securityreports.domain.ports.repository.SecurityReportRepositoryPort;
import com.smxplore.securityreports.domain.ports.usecases.MarkReportAsSolvedUseCasePort;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

import java.util.UUID;

@RequiredArgsConstructor
public class MarkReportAsSolvedUseCase implements MarkReportAsSolvedUseCasePort {

    private final SecurityReportRepositoryPort securityReportRepo;

    @Override
    public Mono<SecurityReport> handle(UUID securityReportId) {
        return securityReportRepo.findById(securityReportId)
                .switchIfEmpty(Mono.error(new ReportNotFoundException(securityReportId)))
                .flatMap(report -> {
                    report.markAsSolved();
                    return Mono.just(report);
                })
                .flatMap(securityReportRepo::save);
    }
}
