package com.smxplore.securityreports.application;

import com.smxplore.securityreports.domain.model.SecurityReport;
import com.smxplore.securityreports.domain.exceptions.ReportNotFoundException;
import com.smxplore.securityreports.domain.ports.repository.SecurityReportRepositoryPort;
import com.smxplore.securityreports.domain.ports.usecases.AddReportPhotosUseCasePort;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

import java.util.Collection;
import java.util.UUID;

@RequiredArgsConstructor
public class AddReportPhotosUseCase implements AddReportPhotosUseCasePort {
    private final SecurityReportRepositoryPort securityReportRepo;

    @Override
    public Mono<SecurityReport> handle(UUID reportId, Collection<String> photos) {
        return securityReportRepo.findById(reportId)
                .switchIfEmpty(Mono.error(new ReportNotFoundException(reportId)))
                .flatMap(report -> {
                    report.addPhotos(photos);
                    return securityReportRepo.save(report);
                });
    }
}
