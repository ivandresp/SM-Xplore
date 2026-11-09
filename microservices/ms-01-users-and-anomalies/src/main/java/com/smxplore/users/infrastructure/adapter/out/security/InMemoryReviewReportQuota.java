package com.smxplore.users.infrastructure.adapter.out.security;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.smxplore.users.application.port.out.ReviewReportQuota;
import com.smxplore.users.domain.exception.ReportQuotaExceededException;
import com.smxplore.users.infrastructure.config.ReviewReportProperties;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * MS01-22: cuota en memoria de reportes de reseñas por turista.
 *
 * <p>La cuota es por instancia del servicio (igual que el límite por cuenta de autenticación): con varias réplicas el
 * límite efectivo se multiplica por el número de instancias. Se documenta como riesgo residual en el README.
 */
@Component
public final class InMemoryReviewReportQuota implements ReviewReportQuota {

    private final ReviewReportProperties properties;
    private final Clock clock;
    private final Cache<UUID, Window> windows;

    public InMemoryReviewReportQuota(ReviewReportProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
        this.windows = Caffeine.newBuilder()
                .expireAfterAccess(properties.window().multipliedBy(2))
                .maximumSize(50_000)
                .build();
    }

    @Override
    public Mono<Void> checkAndRecord(UUID reporterId) {
        return Mono.defer(() -> {
            Instant now = clock.instant();
            Window window = windows.get(reporterId, id -> new Window(now, properties.window()));
            Duration retryAfter = window.tryAcquire(properties.maxPerWindow(), now, properties.window());
            if (retryAfter == null) {
                return Mono.empty();
            }
            return Mono.error(new ReportQuotaExceededException(retryAfter));
        });
    }

    /** Ventana fija por turista; se reinicia cuando expira. */
    private static final class Window {

        private int count;
        private Instant resetAt;

        private Window(Instant now, Duration duration) {
            this.resetAt = now.plus(duration);
        }

        /**
         * Intenta consumir una unidad de cuota.
         *
         * @return {@code null} si se admite el reporte; si no, el tiempo que falta para que se reinicie la ventana
         */
        private synchronized Duration tryAcquire(int maxPerWindow, Instant now, Duration duration) {
            if (!now.isBefore(resetAt)) {
                count = 0;
                resetAt = now.plus(duration);
            }
            if (count >= maxPerWindow) {
                return Duration.between(now, resetAt);
            }
            count++;
            return null;
        }
    }
}
