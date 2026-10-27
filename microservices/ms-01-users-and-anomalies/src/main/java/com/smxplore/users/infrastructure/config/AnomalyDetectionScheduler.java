package com.smxplore.users.infrastructure.config;

import com.smxplore.users.application.port.in.DetectAnomaliesUseCase;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Ejecuta la detección cada 30 segundos. Se desactiva con {@code app.anomalies.detection.enabled=false} en pruebas y
 * entornos donde la detección la dispara un monitor externo.
 */
@Component
@ConditionalOnProperty(
        prefix = "app.anomalies.detection",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true)
public class AnomalyDetectionScheduler {

    private static final Logger LOGGER = LoggerFactory.getLogger(AnomalyDetectionScheduler.class);

    private final DetectAnomaliesUseCase detectAnomalies;

    public AnomalyDetectionScheduler(DetectAnomaliesUseCase detectAnomalies) {
        this.detectAnomalies = detectAnomalies;
    }

    @Scheduled(fixedDelayString = "${app.anomalies.detection.interval-ms:30000}")
    public void detect() {
        try {
            Integer detected = detectAnomalies.detect().block(Duration.ofSeconds(30));
            if (detected != null && detected > 0) {
                LOGGER.warn("Se detectaron {} anomalías nuevas", detected);
            }
        } catch (RuntimeException exception) {
            LOGGER.error("No se pudo ejecutar la detección de anomalías", exception);
        }
    }
}
