package com.smxplore.recommendations.infrastructure.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "app.security.jwt")
public class JwtProperties {

    private String jwkSetUri;
    private String publicKeyLocation;
    private String issuer = "sm-xplore-ms-01";
    private String audience = "sm-xplore-services";
    private String userIdClaim = "userId";
    private String roleClaim = "role";
    private String nameClaim = "name";
    /**
     * Solo desarrollo: acepta un JWKS por HTTP cuando el emisor no es loopback (por ejemplo
     * {@code http://ms-01:8081} dentro de Compose). En produccion debe quedar en false.
     */
    private boolean allowInsecureJwks = false;
}
