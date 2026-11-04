package com.smxplore.users.application.service;

import com.smxplore.users.application.dto.AnomalyThresholdView;
import com.smxplore.users.application.dto.UpdateAnomalyThresholdCommand;
import com.smxplore.users.application.port.in.UpdateAnomalyThresholdUseCase;
import com.smxplore.users.application.port.out.AnomalyThresholdRepository;
import com.smxplore.users.application.port.out.TransactionPort;
import com.smxplore.users.domain.model.AnomalyThreshold;
import com.smxplore.users.domain.model.AnomalyType;
import com.smxplore.users.domain.model.AuditAction;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import reactor.core.publisher.Mono;

public final class UpdateAnomalyThresholdService implements UpdateAnomalyThresholdUseCase {

    private final AnomalyThresholdRepository thresholds;
    private final AuditTrailRecorder audit;
    private final TransactionPort transactions;
    private final Clock clock;

    public UpdateAnomalyThresholdService(
            AnomalyThresholdRepository thresholds,
            AuditTrailRecorder audit,
            TransactionPort transactions,
            Clock clock) {
        this.thresholds = thresholds;
        this.audit = audit;
        this.transactions = transactions;
        this.clock = clock;
    }

    @Override
    public Mono<AnomalyThresholdView> update(UUID adminId, AnomalyType type, UpdateAnomalyThresholdCommand command) {
        return transactions
                .execute(Mono.defer(() -> thresholds
                        .findByType(type)
                        .defaultIfEmpty(AnomalyThreshold.defaults(type, clock.instant()))
                        .flatMap(current -> update(adminId, current, command))))
                .map(AnomalyThresholdView::from);
    }

    private Mono<AnomalyThreshold> update(
            UUID adminId, AnomalyThreshold current, UpdateAnomalyThresholdCommand command) {
        Instant now = clock.instant();
        AnomalyThreshold updated = current.update(
                command.thresholdValue(), command.windowSeconds(), command.severity(), command.enabled(), now, adminId);
        return thresholds
                .save(updated)
                .then(audit.record(adminId, AuditAction.UMBRAL_ANOMALIA_ACTUALIZADO, null, details(updated), now))
                .thenReturn(updated);
    }

    private static Map<String, String> details(AnomalyThreshold threshold) {
        Map<String, String> details = new LinkedHashMap<>();
        details.put("tipo", threshold.type().code());
        details.put("umbral", Integer.toString(threshold.thresholdValue()));
        details.put("ventanaSegundos", Integer.toString(threshold.windowSeconds()));
        details.put("severidad", threshold.severity().code());
        details.put("habilitado", Boolean.toString(threshold.enabled()));
        return details;
    }
}
