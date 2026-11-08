package com.smxplore.users.infrastructure.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Define qué proxies inversos pueden informar la IP real del cliente mediante {@code X-Real-IP}. Fuera de esos proxies
 * cualquier encabezado de reenvío se ignora, de modo que un cliente no pueda elegir su propia IP y evadir el límite de
 * solicitudes.
 */
@ConfigurationProperties("app.rate-limit")
public record ClientAddressProperties(List<String> trustedProxies) {

    /** Rangos privados y de loopback: suficiente para el gateway y los servicios del compose del proyecto. */
    static final List<String> DEFAULT_TRUSTED_PROXIES =
            List.of("127.0.0.1/32", "::1/128", "172.16.0.0/12", "10.0.0.0/8", "192.168.0.0/16");

    public ClientAddressProperties {
        trustedProxies = trustedProxies == null || trustedProxies.isEmpty()
                ? DEFAULT_TRUSTED_PROXIES
                : List.copyOf(trustedProxies);
    }
}
