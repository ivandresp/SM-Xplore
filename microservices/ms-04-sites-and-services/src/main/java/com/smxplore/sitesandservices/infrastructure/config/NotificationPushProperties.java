package com.smxplore.sitesandservices.infrastructure.config;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "app.notifications.push")
public class NotificationPushProperties {

    /**
     * El canal push nace deshabilitado: todavía no hay proveedor externo contratado.
     * Al habilitarlo se registran los intentos de entrega sin enviar nada real.
     */
    private boolean enabled;
    private String provider = "noop";

    @PostConstruct
    public void validate() {
        if (provider == null || provider.isBlank()) {
            throw new IllegalStateException("app.notifications.push.provider is required");
        }
    }
}
