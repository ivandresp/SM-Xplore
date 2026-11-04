package com.smxplore.users.application.service;

import com.smxplore.users.application.dto.AnomalyView;
import com.smxplore.users.application.dto.ClassifyAnomalyCommand;
import com.smxplore.users.application.port.in.ClassifyAnomalyUseCase;
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

public final class ClassifyAnomalyService implements ClassifyAnomalyUseCase {

    private final AnomalyRepository anomalies;
    private final AuditTrailRecorder audit;
    private final TransactionPort transactions;
    private final Clock clock;

    public ClassifyAnomalyService(
            AnomalyRepository anomalies, AuditTrailRecorder audit, TransactionPort transactions, Clock clock) {
        this.anomalies = anomalies;
        this.audit = audit;
        this.transactions = transactions;
        this.clock = clock;
    }

    @Override
    public Mono<AnomalyView> classify(UUID adminId, UUID anomalyId, ClassifyAnomalyCommand command) {
        return transactions
                .execute(Mono.defer(() -> anomalies
                        .findById(anomalyId)
                        .switchIfEmpty(Mono.error(AnomalyNotFoundException::new))
                        .flatMap(anomaly -> classify(adminId, anomaly, command))))
                .map(AnomalyView::from);
    }

    private Mono<Anomaly> classify(UUID adminId, Anomaly anomaly, ClassifyAnomalyCommand command) {
        Instant now = clock.instant();
        String previousSeverity = anomaly.severity().code();
        anomaly.classify(command.severity());
        return anomalies
                .save(anomaly)
                .then(audit.record(
                        adminId, AuditAction.ANOMALIA_CLASIFICADA, null, details(anomaly, previousSeverity), now))
                .thenReturn(anomaly);
    }

    private static Map<String, String> details(Anomaly anomaly, String previousSeverity) {
        Map<String, String> details = new LinkedHashMap<>();
        details.put("anomaliaId", anomaly.id().toString());
        details.put("tipo", anomaly.type().code());
        details.put("severidadAnterior", previousSeverity);
        details.put("severidadNueva", anomaly.severity().code());
        return details;
    }
}
