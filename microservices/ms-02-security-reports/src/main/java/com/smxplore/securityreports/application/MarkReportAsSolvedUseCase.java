package com.smxplore.securityreports.application;

import com.smxplore.securityreports.application.event.SecurityReportEventFactory;
import com.smxplore.securityreports.application.support.TransactionalOutboxWriter;
import com.smxplore.securityreports.domain.exceptions.ReportNotFoundException;
import com.smxplore.securityreports.domain.model.SecurityReport;
import com.smxplore.securityreports.domain.ports.repository.SecurityReportRepositoryPort;
import com.smxplore.securityreports.domain.ports.usecases.MarkReportAsSolvedUseCasePort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Slf4j
@RequiredArgsConstructor
public class MarkReportAsSolvedUseCase implements MarkReportAsSolvedUseCasePort {

    private final SecurityReportRepositoryPort securityReportRepo;
    private final TransactionalOutboxWriter outboxWriter;
    private final SecurityReportEventFactory eventFactory;

    @Override
    public Mono<SecurityReport> handle(UUID securityReportId, UUID actorId, String correlationId) {
        return securityReportRepo.findById(securityReportId)
                .switchIfEmpty(Mono.error(new ReportNotFoundException(securityReportId)))
                .flatMap(report -> {
                    report.markAsSolved(actorId, correlationId);
                    // MS02-15: structured audit trail; the actor is also persisted in the report document.
                    log.info("Security report status changed: reportId={} from=REPORTED to=SOLVED actorId={} correlationId={}",
                            securityReportId, actorId, correlationId);
                    return outboxWriter.save(
                            securityReportRepo.save(report),
                            eventFactory::reportChanged,
                            correlationId);
                });
    }
}
