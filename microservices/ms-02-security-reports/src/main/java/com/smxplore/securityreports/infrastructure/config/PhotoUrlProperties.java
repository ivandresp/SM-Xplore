package com.smxplore.securityreports.infrastructure.config;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;
import java.util.Locale;

/**
 * Politica de URLs de fotos (MS02-07).
 *
 * <p>Por defecto solo se aceptan URLs {@code https} cuyo host este en la allowlist. {@code allow-any-host} y
 * {@code allow-http} existen para local/tests y se activan de forma explicita en {@code application-local.yaml} y
 * {@code application-test.yaml}; el guard de arranque exige una allowlist cuando el perfil no es local/dev/test.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "app.security.photos")
public class PhotoUrlProperties {

    /** Hosts permitidos; {@code *.example.com} acepta subdominios. */
    private List<String> allowedHosts = List.of();
    /** Permite URLs {@code http://}; desactivado por defecto. */
    private boolean allowHttp = false;
    /** Desactiva la allowlist (solo local/tests). */
    private boolean allowAnyHost = false;

    @PostConstruct
    public void normalize() {
        allowedHosts = allowedHosts == null
                ? List.of()
                : allowedHosts.stream()
                        .filter(host -> host != null && !host.isBlank())
                        .map(host -> host.trim().toLowerCase(Locale.ROOT))
                        .toList();
    }

    public boolean hasAllowedHosts() {
        return !allowedHosts.isEmpty();
    }
}
