package com.smxplore.securityreports.application;

import com.smxplore.securityreports.application.event.SecurityReportEventFactory;
import com.smxplore.securityreports.application.support.TransactionalOutboxWriter;
import com.smxplore.securityreports.domain.exceptions.ReportNotFoundException;
import com.smxplore.securityreports.domain.model.SecurityReport;
import com.smxplore.securityreports.domain.ports.repository.SecurityReportRepositoryPort;
import com.smxplore.securityreports.domain.ports.usecases.CloseReportUseCasePort;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

import java.util.UUID;

@RequiredArgsConstructor
public class CloseReportUseCase implements CloseReportUseCasePort {
    private final SecurityReportRepositoryPort securityReportRepo;
    private final TransactionalOutboxWriter outboxWriter;
    private final SecurityReportEventFactory eventFactory;

    @Override
    public Mono<SecurityReport> handle(UUID securityReportId, String correlationId) {
        return securityReportRepo.findById(securityReportId)
                .switchIfEmpty(Mono.error(new ReportNotFoundException(securityReportId)))
                .flatMap(report -> {
                    report.markAsReported();
                    return outboxWriter.save(
                            securityReportRepo.save(report),
                            eventFactory::reportChanged,
                            correlationId);
                });
    }
}
