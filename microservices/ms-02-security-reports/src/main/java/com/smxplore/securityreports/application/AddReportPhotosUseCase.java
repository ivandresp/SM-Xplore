package com.smxplore.securityreports.application;

import com.smxplore.securityreports.application.event.SecurityReportEventFactory;
import com.smxplore.securityreports.application.support.TransactionalOutboxWriter;
import com.smxplore.securityreports.domain.exceptions.ReportNotFoundException;
import com.smxplore.securityreports.domain.model.SecurityReport;
import com.smxplore.securityreports.domain.ports.out.PhotoUrlPolicyPort;
import com.smxplore.securityreports.domain.ports.repository.SecurityReportRepositoryPort;
import com.smxplore.securityreports.domain.ports.usecases.AddReportPhotosUseCasePort;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

import java.util.Collection;
import java.util.UUID;

@RequiredArgsConstructor
public class AddReportPhotosUseCase implements AddReportPhotosUseCasePort {
    private final SecurityReportRepositoryPort securityReportRepo;
    private final TransactionalOutboxWriter outboxWriter;
    private final SecurityReportEventFactory eventFactory;
    private final PhotoUrlPolicyPort photoUrlPolicy;

    @Override
    public Mono<SecurityReport> handle(UUID reportId, Collection<String> photos, String correlationId) {
        if (photos != null) {
            photos.forEach(photoUrlPolicy::validate);
        }
        return securityReportRepo.findById(reportId)
                .switchIfEmpty(Mono.error(new ReportNotFoundException(reportId)))
                .flatMap(report -> {
                    report.addPhotos(photos);
                    return outboxWriter.save(
                            securityReportRepo.save(report),
                            eventFactory::reportChanged,
                            correlationId);
                });
    }
}
