package com.smxplore.activitiesevents.infrastructure.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * RS256 resource-server configuration. The public key comes either from ms-01's
 * JWKS endpoint (production) or from a PEM file (local development and tests).
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.security.jwt")
public class JwtProperties {

    private String jwkSetUri;
    private String publicKeyLocation;
    private String issuer = "sm-xplore-ms-01";
    private String audience = "sm-xplore-services";
    private String roleClaim = "role";
    private String nameClaim = "name";
}
