package com.smxplore.users.infrastructure.adapter.out.monitoring;

import com.smxplore.users.application.dto.ServiceHealthStatus;
import com.smxplore.users.application.port.out.ServiceHealthSignal;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.bson.Document;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * Comprueba que PostgreSQL y MongoDB respondan. Si una dependencia falla dos veces seguidas, la detección la reporta
 * como {@code CAIDA_SERVICIO}. La comprobación se ejecuta únicamente cuando la detección la solicita.
 */
@Component
public class ReactiveServiceHealthProbe implements ServiceHealthSignal {

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

    private Mono<ComponentStatus> checkPostgres() {
        return databaseClient
                .sql("SELECT 1")
                .fetch()
                .one()
                .timeout(TIMEOUT)
                .map(row -> ComponentStatus.available("PostgreSQL"))
                .onErrorResume(error -> Mono.just(ComponentStatus.unavailable("PostgreSQL", message(error))));
    }

    private Mono<ComponentStatus> checkMongo() {
        return mongoTemplate
                .executeCommand(new Document("ping", 1))
                .timeout(TIMEOUT)
                .map(document -> ComponentStatus.available("MongoDB"))
                .onErrorResume(error -> Mono.just(ComponentStatus.unavailable("MongoDB", message(error))));
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

    private record ComponentStatus(boolean available, String detail) {

        static ComponentStatus available(String component) {
            return new ComponentStatus(true, component + " disponible");
        }

        static ComponentStatus unavailable(String component, String message) {
            return new ComponentStatus(false, component + ": " + message);
        }
    }
}
