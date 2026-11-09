package com.smxplore.users.infrastructure.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * MS01-22: cuota de reportes de reseñas por turista.
 *
 * <p>El valor por defecto (20 reportes cada 24 horas) acota el abuso del buzón de moderación sin impedir que un
 * turista reporte varias reseñas problemáticas en un mismo día.
 */
@ConfigurationProperties("app.reviews.report-quota")
public record ReviewReportProperties(int maxPerWindow, Duration window) {

    public static final int DEFAULT_MAX_PER_WINDOW = 20;
    public static final Duration DEFAULT_WINDOW = Duration.ofHours(24);

    public ReviewReportProperties {
        if (maxPerWindow < 1) {
            maxPerWindow = DEFAULT_MAX_PER_WINDOW;
        }
        if (window == null || window.isZero() || window.isNegative()) {
            window = DEFAULT_WINDOW;
        }
    }
}
