package com.smxplore.users.application.service;

import com.smxplore.users.application.dto.ServiceHealthStatus;
import com.smxplore.users.application.port.in.DetectAnomaliesUseCase;
import com.smxplore.users.application.port.out.AnomalyRepository;
import com.smxplore.users.application.port.out.AnomalyThresholdRepository;
import com.smxplore.users.application.port.out.FailedLoginSignal;
import com.smxplore.users.application.port.out.RequestRateSignal;
import com.smxplore.users.application.port.out.ServiceHealthSignal;
import com.smxplore.users.domain.model.Anomaly;
import com.smxplore.users.domain.model.AnomalyStatus;
import com.smxplore.users.domain.model.AnomalyThreshold;
import com.smxplore.users.domain.model.AnomalyType;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Evalúa las señales del servicio contra los umbrales configurados.
 *
 * <p>Las señales se mantienen en memoria por instancia; el umbral y la anomalía resultante sí se persisten en
 * PostgreSQL. Para no inundar el panel, no se crea otra anomalía del mismo tipo mientras la anterior siga pendiente o
 * dentro de su ventana de observación, y además se aplica un enfriamiento mínimo (MS01-24) entre creaciones del mismo
 * tipo.
 *
 * <p>MS01-24 (riesgo residual documentado): las señales son contadores globales por instancia, así que un atacante
 * todavía puede provocar como máximo una anomalía pendiente por tipo y ventana de enfriamiento; la detección no bloquea
 * tráfico por sí sola.
 */
public final class AnomalyDetectionService implements DetectAnomaliesUseCase {

    private final AnomalyRepository anomalies;
    private final AnomalyThresholdRepository thresholds;
    private final FailedLoginSignal failedLogins;
    private final RequestRateSignal requestRate;
    private final ServiceHealthSignal health;
    private final Clock clock;
    private final Duration minInterval;
    private final Map<AnomalyType, Instant> lastCreated = new EnumMap<>(AnomalyType.class);

    public AnomalyDetectionService(
            AnomalyRepository anomalies,
            AnomalyThresholdRepository thresholds,
            FailedLoginSignal failedLogins,
            RequestRateSignal requestRate,
            ServiceHealthSignal health,
            Clock clock,
            Duration minInterval) {
        this.anomalies = anomalies;
        this.thresholds = thresholds;
        this.failedLogins = failedLogins;
        this.requestRate = requestRate;
        this.health = health;
        this.clock = clock;
        this.minInterval = minInterval;
    }

    @Override
    public Mono<Integer> detect() {
        Instant now = clock.instant();
        Mono<ServiceHealthStatus> healthStatus = Mono.defer(health::check).cache();

        return Flux.fromArray(AnomalyType.values())
                .concatMap(type -> thresholds.findByType(type).defaultIfEmpty(AnomalyThreshold.defaults(type, now)))
                .filter(AnomalyThreshold::enabled)
                .concatMap(threshold -> evaluate(threshold, healthStatus, now))
                .count()
                .map(Long::intValue);
    }

    private Mono<Anomaly> evaluate(AnomalyThreshold threshold, Mono<ServiceHealthStatus> healthStatus, Instant now) {
        return trigger(threshold, healthStatus, now)
                .flatMap(description -> recordIfNeeded(threshold, description, now));
    }

    private Mono<String> trigger(AnomalyThreshold threshold, Mono<ServiceHealthStatus> healthStatus, Instant now) {
        Instant since = now.minusSeconds(threshold.windowSeconds());
        return switch (threshold.type()) {
            case SUSPICIOUS_ACCESS ->
                failedLogins
                        .countSince(since)
                        .filter(count -> count >= threshold.thresholdValue())
                        .map(count -> "Se detectaron " + count + " inicios de sesión fallidos en los últimos "
                                + threshold.windowSeconds() + " segundos.");
            case REQUEST_SPIKE ->
                requestRate
                        .countSince(since)
                        .filter(count -> count >= threshold.thresholdValue())
                        .map(count -> "Se detectaron " + count + " solicitudes en los últimos "
                                + threshold.windowSeconds() + " segundos.");
            case SERVICE_DOWNTIME ->
                healthStatus
                        .filter(status ->
                                !status.available() && status.consecutiveFailures() >= threshold.thresholdValue())
                        .map(status -> "El servicio acumula " + status.consecutiveFailures()
                                + " verificaciones de disponibilidad fallidas."
                                + (status.detail().isBlank() ? "" : " " + status.detail()));
        };
    }

    private Mono<Anomaly> recordIfNeeded(AnomalyThreshold threshold, String description, Instant now) {
        if (isWithinCooldown(threshold.type(), now)) {
            return Mono.empty();
        }
        return anomalies
                .findLatestByType(threshold.type())
                .map(latest -> isRecent(latest, threshold, now))
                .defaultIfEmpty(false)
                .flatMap(recent -> {
                    if (recent) {
                        return Mono.<Anomaly>empty();
                    }
                    return anomalies
                            .save(Anomaly.detect(threshold.type(), threshold.severity(), description, now))
                            .doOnNext(anomaly -> markCreated(threshold.type(), now));
                });
    }

    /**
     * MS01-24: limita la creación de anomalías del mismo tipo para evitar fatiga de alertas y que un atacante pueda
     * inundar el panel incluso resolviendo la anomalía anterior.
     */
    private boolean isWithinCooldown(AnomalyType type, Instant now) {
        Instant last = lastCreated.get(type);
        return last != null && now.isBefore(last.plus(minInterval));
    }

    private synchronized void markCreated(AnomalyType type, Instant now) {
        lastCreated.put(type, now);
    }

    private static boolean isRecent(Anomaly latest, AnomalyThreshold threshold, Instant now) {
        if (latest.status() != AnomalyStatus.RESOLVED) {
            return true;
        }
        return latest.detectedAt().plusSeconds(threshold.windowSeconds()).isAfter(now);
    }
}
