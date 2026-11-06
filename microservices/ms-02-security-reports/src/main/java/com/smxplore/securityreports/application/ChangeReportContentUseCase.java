package com.smxplore.securityreports.application;

import com.smxplore.securityreports.application.event.SecurityReportEventFactory;
import com.smxplore.securityreports.application.support.TransactionalOutboxWriter;
import com.smxplore.securityreports.domain.exceptions.ReportNotFoundException;
import com.smxplore.securityreports.domain.model.SecurityReport;
import com.smxplore.securityreports.domain.ports.repository.SecurityReportRepositoryPort;
import com.smxplore.securityreports.domain.ports.usecases.ChangeReportContentUseCasePort;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

import java.util.UUID;

@RequiredArgsConstructor
public class ChangeReportContentUseCase implements ChangeReportContentUseCasePort {
    private final SecurityReportRepositoryPort securityReportRepo;
    private final TransactionalOutboxWriter outboxWriter;
    private final SecurityReportEventFactory eventFactory;

    @Override
    public Mono<SecurityReport> handle(UUID reportId, String newTitle, String newDescription,
                                       String correlationId) {
        return securityReportRepo.findById(reportId)
                .switchIfEmpty(Mono.error(new ReportNotFoundException(reportId)))
                .doOnNext(report -> report.changeContent(newTitle, newDescription))
                .flatMap(report -> outboxWriter.save(
                        securityReportRepo.save(report),
                        eventFactory::reportChanged,
                        correlationId));
    }
}
