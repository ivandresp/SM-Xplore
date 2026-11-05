package com.smxplore.activitiesevents.infrastructure.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * RS256 resource-server configuration. The public key comes either from ms-01's
 * JWKS endpoint (production) or from a PEM file (local development and tests).
 * Defaults live only in application.yaml so a missing property fails loudly.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "app.security.jwt")
public class JwtProperties {

    private String jwkSetUri;
    private String publicKeyLocation;
    private String issuer;
    private String audience;
    private String roleClaim = "role";
    private String nameClaim = "name";
    /**
     * Solo desarrollo: permite un JWKS por HTTP cuando el emisor no es loopback (por
     * ejemplo, {@code http://ms-01:8081} dentro del stack de Compose). En produccion debe
     * quedar en false para exigir HTTPS.
     */
    private boolean allowInsecureJwks = false;
}