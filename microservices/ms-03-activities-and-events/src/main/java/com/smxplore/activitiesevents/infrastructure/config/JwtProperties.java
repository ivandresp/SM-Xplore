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
}
