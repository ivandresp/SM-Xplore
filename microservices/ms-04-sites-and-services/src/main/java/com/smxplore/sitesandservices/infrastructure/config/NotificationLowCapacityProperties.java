package com.smxplore.sitesandservices.infrastructure.config;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "app.notifications.low-capacity")
public class NotificationLowCapacityProperties {

    private boolean enabled = true;
    /**
     * Porcentaje de cupo restante por debajo del cual se avisa al proveedor y al gestor
     * del sitio. El aviso se emite una sola vez, al cruzar el umbral.
     */
    private int thresholdPercent = 20;

    @PostConstruct
    public void validate() {
        if (thresholdPercent < 1 || thresholdPercent > 100) {
            throw new IllegalStateException(
                    "app.notifications.low-capacity.threshold-percent must be between 1 and 100");
        }
    }
}
