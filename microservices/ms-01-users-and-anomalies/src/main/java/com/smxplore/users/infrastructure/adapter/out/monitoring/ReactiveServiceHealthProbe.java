package com.smxplore.users.infrastructure.adapter.out.monitoring;

import com.smxplore.users.application.dto.ComponentAvailability;
import com.smxplore.users.application.dto.ServiceHealthStatus;
import com.smxplore.users.application.port.out.ServiceHealthSignal;
import com.smxplore.users.application.port.out.SystemAvailabilityProbe;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.bson.Document;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * Comprueba que PostgreSQL y MongoDB respondan.
 *
 * <p>{@link #check()} alimenta la detección de anomalías y acumula fallos consecutivos; {@link #checkComponents()}
 * devuelve el estado de cada dependencia para el panel administrativo sin alterar ese contador.
 */
@Component
public class ReactiveServiceHealthProbe implements ServiceHealthSignal, SystemAvailabilityProbe {

    private static final Duration TIMEOUT = Duration.ofSeconds(5);
    private static final int MAX_DETAIL_LENGTH = 800;

    private final DatabaseClient databaseClient;
    private final ReactiveMongoTemplate mongoTemplate;
    private final AtomicInteger consecutiveFailures = new AtomicInteger();

    public ReactiveServiceHealthProbe(DatabaseClient databaseClient, ReactiveMongoTemplate mongoTemplate) {
        this.databaseClient = databaseClient;
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public Mono<ServiceHealthStatus> check() {
        return Mono.zip(checkPostgres(), checkMongo())
                .map(tuple -> aggregate(tuple.getT1(), tuple.getT2()))
                .timeout(TIMEOUT.plusSeconds(1))
                .onErrorResume(error -> Mono.fromSupplier(() -> unexpectedFailure(error)));
    }

    @Override
    public Mono<List<ComponentAvailability>> checkComponents() {
        return Mono.zip(checkPostgres(), checkMongo())
                .map(tuple -> List.of(toAvailability(tuple.getT1()), toAvailability(tuple.getT2())))
                .timeout(TIMEOUT.plusSeconds(1))
                .onErrorResume(error -> Mono.fromSupplier(() -> unverified(error)));
    }

    private Mono<ComponentStatus> checkPostgres() {
        Mono<Void> operation = databaseClient.sql("SELECT 1").fetch().one().then();
        return timed("PostgreSQL", operation);
    }

    private Mono<ComponentStatus> checkMongo() {
        Mono<Void> operation =
                mongoTemplate.executeCommand(new Document("ping", 1)).then();
        return timed("MongoDB", operation);
    }

    private Mono<ComponentStatus> timed(String component, Mono<Void> operation) {
        return Mono.defer(() -> {
            long started = System.nanoTime();
            return operation
                    .timeout(TIMEOUT)
                    .thenReturn(ComponentStatus.available(component, elapsedMillis(started)))
                    .onErrorResume(error ->
                            Mono.just(ComponentStatus.unavailable(component, elapsedMillis(started), message(error))));
        });
    }

    private ServiceHealthStatus aggregate(ComponentStatus postgres, ComponentStatus mongo) {
        boolean available = postgres.available() && mongo.available();
        int failures;
        if (available) {
            consecutiveFailures.set(0);
            failures = 0;
        } else {
            failures = consecutiveFailures.incrementAndGet();
        }
        String detail = Stream.of(postgres, mongo)
                .filter(component -> !component.available())
                .map(ComponentStatus::detail)
                .collect(Collectors.joining("; "));
        return new ServiceHealthStatus(available, failures, truncate(detail));
    }

    private ServiceHealthStatus unexpectedFailure(Throwable error) {
        int failures = consecutiveFailures.incrementAndGet();
        return ServiceHealthStatus.unavailable(
                failures, truncate("No se pudo verificar la disponibilidad: " + message(error)));
    }

    private static List<ComponentAvailability> unverified(Throwable error) {
        String detail = truncate("No se pudo verificar la disponibilidad: " + message(error));
        return List.of(
                new ComponentAvailability("PostgreSQL", false, 0, detail),
                new ComponentAvailability("MongoDB", false, 0, detail));
    }

    private static ComponentAvailability toAvailability(ComponentStatus status) {
        return new ComponentAvailability(status.component(), status.available(), status.latencyMs(), status.detail());
    }

    private static long elapsedMillis(long started) {
        return Duration.ofNanos(System.nanoTime() - started).toMillis();
    }

    private static String message(Throwable error) {
        Throwable root = error;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        String message = root.getMessage();
        return message == null || message.isBlank() ? root.getClass().getSimpleName() : message;
    }

    private static String truncate(String value) {
        if (value == null || value.length() <= MAX_DETAIL_LENGTH) {
            return value == null ? "" : value;
        }
        return value.substring(0, MAX_DETAIL_LENGTH);
    }

    private record ComponentStatus(String component, boolean available, long latencyMs, String detail) {

        static ComponentStatus available(String component, long latencyMs) {
            return new ComponentStatus(component, true, latencyMs, component + " disponible");
        }

        static ComponentStatus unavailable(String component, long latencyMs, String message) {
            return new ComponentStatus(component, false, latencyMs, component + ": " + message);
        }
    }
}
