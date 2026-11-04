package com.smxplore.users.application.service;

import com.smxplore.users.application.dto.AnomalyView;
import com.smxplore.users.application.dto.ResolveAnomalyCommand;
import com.smxplore.users.application.port.in.ResolveAnomalyUseCase;
import com.smxplore.users.application.port.out.AnomalyRepository;
import com.smxplore.users.application.port.out.TransactionPort;
import com.smxplore.users.domain.exception.AnomalyNotFoundException;
import com.smxplore.users.domain.model.Anomaly;
import com.smxplore.users.domain.model.AuditAction;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import reactor.core.publisher.Mono;

public final class ResolveAnomalyService implements ResolveAnomalyUseCase {

    private final AnomalyRepository anomalies;
    private final AuditTrailRecorder audit;
    private final TransactionPort transactions;
    private final Clock clock;

    public ResolveAnomalyService(
            AnomalyRepository anomalies, AuditTrailRecorder audit, TransactionPort transactions, Clock clock) {
        this.anomalies = anomalies;
        this.audit = audit;
        this.transactions = transactions;
        this.clock = clock;
    }

    @Override
    public Mono<AnomalyView> resolve(UUID adminId, UUID anomalyId, ResolveAnomalyCommand command) {
        return transactions
                .execute(Mono.defer(() -> anomalies
                        .findById(anomalyId)
                        .switchIfEmpty(Mono.error(AnomalyNotFoundException::new))
                        .flatMap(anomaly -> resolve(adminId, anomaly, command))))
                .map(AnomalyView::from);
    }

    private Mono<Anomaly> resolve(UUID adminId, Anomaly anomaly, ResolveAnomalyCommand command) {
        Instant now = clock.instant();
        anomaly.resolve(adminId, command == null ? null : command.note(), now);
        return anomalies
                .save(anomaly)
                .then(audit.record(adminId, AuditAction.ANOMALIA_RESUELTA, null, details(anomaly), now))
                .thenReturn(anomaly);
    }

    private static Map<String, String> details(Anomaly anomaly) {
        Map<String, String> details = new LinkedHashMap<>();
        details.put("anomaliaId", anomaly.id().toString());
        details.put("tipo", anomaly.type().code());
        details.put("severidad", anomaly.severity().code());
        if (anomaly.resolutionNote() != null) {
            details.put("nota", anomaly.resolutionNote());
        }
        return details;
    }
}
