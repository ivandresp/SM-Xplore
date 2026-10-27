package com.smxplore.users.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.smxplore.users.application.dto.AnomalySearchCriteria;
import com.smxplore.users.application.dto.PageResult;
import com.smxplore.users.application.dto.ServiceHealthStatus;
import com.smxplore.users.application.port.out.AnomalyRepository;
import com.smxplore.users.application.port.out.AnomalyThresholdRepository;
import com.smxplore.users.application.port.out.FailedLoginSignal;
import com.smxplore.users.application.port.out.RequestRateSignal;
import com.smxplore.users.application.port.out.ServiceHealthSignal;
import com.smxplore.users.domain.model.Anomaly;
import com.smxplore.users.domain.model.AnomalySeverity;
import com.smxplore.users.domain.model.AnomalyStatus;
import com.smxplore.users.domain.model.AnomalyThreshold;
import com.smxplore.users.domain.model.AnomalyType;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class AnomalyDetectionServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-27T00:00:00Z");
    private static final UUID ADMIN_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    private final MutableClock clock = new MutableClock(NOW);
    private final FakeAnomalyRepository anomalies = new FakeAnomalyRepository();
    private final Map<AnomalyType, AnomalyThreshold> thresholds = new EnumMap<>(AnomalyType.class);
    private final AtomicLong failedLogins = new AtomicLong();
    private final AtomicLong requests = new AtomicLong();
    private ServiceHealthStatus health = ServiceHealthStatus.healthy();

    @Test
    void detectsSuspiciousAccessWhenThresholdIsReached() {
        thresholds.put(
                AnomalyType.SUSPICIOUS_ACCESS,
                threshold(AnomalyType.SUSPICIOUS_ACCESS, 3, 300, AnomalySeverity.HIGH, true));
        failedLogins.set(3);

        StepVerifier.create(service().detect()).expectNext(1).verifyComplete();

        assertThat(anomalies.saved).singleElement().satisfies(anomaly -> {
            assertThat(anomaly.type()).isEqualTo(AnomalyType.SUSPICIOUS_ACCESS);
            assertThat(anomaly.severity()).isEqualTo(AnomalySeverity.HIGH);
            assertThat(anomaly.status()).isEqualTo(AnomalyStatus.DETECTED);
            assertThat(anomaly.description()).contains("3", "300");
        });
    }

    @Test
    void doesNotDuplicatePendingAnomaly() {
        thresholds.put(
                AnomalyType.SUSPICIOUS_ACCESS,
                threshold(AnomalyType.SUSPICIOUS_ACCESS, 3, 300, AnomalySeverity.HIGH, true));
        failedLogins.set(3);

        StepVerifier.create(service().detect()).expectNext(1).verifyComplete();
        StepVerifier.create(service().detect()).expectNext(0).verifyComplete();

        assertThat(anomalies.saved).hasSize(1);
    }

    @Test
    void createsNewAnomalyAfterResolutionWindowHasPassed() {
        thresholds.put(
                AnomalyType.SUSPICIOUS_ACCESS,
                threshold(AnomalyType.SUSPICIOUS_ACCESS, 3, 300, AnomalySeverity.HIGH, true));
        failedLogins.set(3);

        StepVerifier.create(service().detect()).expectNext(1).verifyComplete();
        Anomaly first = anomalies.saved.getFirst();
        first.resolve(ADMIN_ID, "Atendida", clock.instant());
        anomalies.save(first).block();

        StepVerifier.create(service().detect()).expectNext(0).verifyComplete();
        clock.advance(Duration.ofSeconds(301));
        StepVerifier.create(service().detect()).expectNext(1).verifyComplete();

        assertThat(anomalies.saved).hasSize(2);
        assertThat(anomalies.saved.getLast().status()).isEqualTo(AnomalyStatus.DETECTED);
    }

    @Test
    void detectsServiceDowntimeAfterConsecutiveFailures() {
        thresholds.put(
                AnomalyType.SERVICE_DOWNTIME,
                threshold(AnomalyType.SERVICE_DOWNTIME, 2, 60, AnomalySeverity.CRITICAL, true));
        health = ServiceHealthStatus.unavailable(2, "MongoDB: timeout");

        StepVerifier.create(service().detect()).expectNext(1).verifyComplete();

        assertThat(anomalies.saved).singleElement().satisfies(anomaly -> {
            assertThat(anomaly.type()).isEqualTo(AnomalyType.SERVICE_DOWNTIME);
            assertThat(anomaly.severity()).isEqualTo(AnomalySeverity.CRITICAL);
            assertThat(anomaly.description()).contains("MongoDB", "2");
        });
    }

    @Test
    void detectsRequestSpike() {
        thresholds.put(
                AnomalyType.REQUEST_SPIKE, threshold(AnomalyType.REQUEST_SPIKE, 2, 60, AnomalySeverity.MEDIUM, true));
        requests.set(5);

        StepVerifier.create(service().detect()).expectNext(1).verifyComplete();

        assertThat(anomalies.saved).singleElement().satisfies(anomaly -> {
            assertThat(anomaly.type()).isEqualTo(AnomalyType.REQUEST_SPIKE);
            assertThat(anomaly.description()).contains("5", "60");
        });
    }

    @Test
    void ignoresDisabledThreshold() {
        thresholds.put(
                AnomalyType.SUSPICIOUS_ACCESS,
                threshold(AnomalyType.SUSPICIOUS_ACCESS, 3, 300, AnomalySeverity.HIGH, false));
        failedLogins.set(10);

        StepVerifier.create(service().detect()).expectNext(0).verifyComplete();

        assertThat(anomalies.saved).isEmpty();
    }

    private AnomalyDetectionService service() {
        FailedLoginSignal failedLoginSignal = new FailedLoginSignal() {
            @Override
            public Mono<Void> record() {
                failedLogins.incrementAndGet();
                return Mono.empty();
            }

            @Override
            public Mono<Long> countSince(Instant since) {
                return Mono.just(failedLogins.get());
            }
        };
        RequestRateSignal requestRateSignal = new RequestRateSignal() {
            @Override
            public Mono<Void> record() {
                requests.incrementAndGet();
                return Mono.empty();
            }

            @Override
            public Mono<Long> countSince(Instant since) {
                return Mono.just(requests.get());
            }
        };
        ServiceHealthSignal healthSignal = () -> Mono.just(health);
        return new AnomalyDetectionService(
                anomalies,
                new InMemoryThresholdRepository(thresholds),
                failedLoginSignal,
                requestRateSignal,
                healthSignal,
                clock);
    }

    private static AnomalyThreshold threshold(
            AnomalyType type, int value, int window, AnomalySeverity severity, boolean enabled) {
        return new AnomalyThreshold(type, value, window, severity, enabled, NOW, null);
    }

    private static final class InMemoryThresholdRepository implements AnomalyThresholdRepository {

        private final Map<AnomalyType, AnomalyThreshold> thresholds;

        private InMemoryThresholdRepository(Map<AnomalyType, AnomalyThreshold> thresholds) {
            this.thresholds = thresholds;
        }

        @Override
        public Flux<AnomalyThreshold> findAll() {
            return Flux.fromIterable(thresholds.values());
        }

        @Override
        public Mono<AnomalyThreshold> findByType(AnomalyType type) {
            return Mono.justOrEmpty(thresholds.get(type));
        }

        @Override
        public Mono<AnomalyThreshold> save(AnomalyThreshold threshold) {
            thresholds.put(threshold.type(), threshold);
            return Mono.just(threshold);
        }
    }

    private static final class FakeAnomalyRepository implements AnomalyRepository {

        private final List<Anomaly> saved = new ArrayList<>();

        @Override
        public Mono<Anomaly> save(Anomaly anomaly) {
            saved.removeIf(existing -> existing.id().equals(anomaly.id()));
            saved.add(anomaly);
            return Mono.just(anomaly);
        }

        @Override
        public Mono<Anomaly> findById(UUID anomalyId) {
            return saved.stream()
                    .filter(anomaly -> anomaly.id().equals(anomalyId))
                    .findFirst()
                    .map(Mono::just)
                    .orElse(Mono.empty());
        }

        @Override
        public Mono<Anomaly> findLatestByType(AnomalyType type) {
            return Mono.justOrEmpty(saved.stream()
                    .filter(anomaly -> anomaly.type() == type)
                    .max(Comparator.comparing(Anomaly::detectedAt)));
        }

        @Override
        public Mono<PageResult<Anomaly>> search(AnomalySearchCriteria criteria) {
            return Mono.error(new UnsupportedOperationException("No usado en la prueba"));
        }
    }

    private static final class MutableClock extends Clock {

        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        private void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
