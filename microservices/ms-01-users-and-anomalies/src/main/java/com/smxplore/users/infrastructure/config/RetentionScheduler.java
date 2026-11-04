package com.smxplore.users.infrastructure.config;

import com.smxplore.users.application.service.DataRetentionService;
import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Ejecuta la política de retención una vez al día (03:30 por defecto). Un fallo se registra y se reintenta en la
 * siguiente ejecución; nunca detiene el servicio.
 */
@Component
@ConditionalOnProperty(name = "app.retention.enabled", havingValue = "true", matchIfMissing = true)
public class RetentionScheduler {

    private static final Logger LOGGER = LoggerFactory.getLogger(RetentionScheduler.class);

    private final DataRetentionService retention;
    private final Clock clock;

    public RetentionScheduler(DataRetentionService retention, Clock clock) {
        this.retention = retention;
        this.clock = clock;
    }

    @Scheduled(cron = "${app.retention.cron:0 30 3 * * *}")
    public void purge() {
        retention
                .purge(clock.instant())
                .doOnNext(outcome -> {
                    if (outcome.total() > 0) {
                        LOGGER.info(
                                "Retención aplicada: {} tokens de restablecimiento, {} intentos de acceso, "
                                        + "{} códigos de recuperación y {} entradas de auditoría eliminadas",
                                outcome.resetTokens(),
                                outcome.loginAttempts(),
                                outcome.recoveryCodes(),
                                outcome.auditEntries());
                    }
                })
                .doOnError(exception -> LOGGER.error("Falló la pasada de retención de datos", exception))
                .onErrorResume(exception -> reactor.core.publisher.Mono.empty())
                .subscribe();
    }
}
