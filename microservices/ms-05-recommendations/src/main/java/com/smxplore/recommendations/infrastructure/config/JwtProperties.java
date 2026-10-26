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
    private String issuer;
    private String audience;
    private String roleClaim = "role";
    private String nameClaim = "name";
    private String languageClaim = "locale";
}
