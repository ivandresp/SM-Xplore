package com.smxplore.securityreports.infrastructure.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "app.security.jwt")
public class JwtProperties {
    private String secret;
    private String issuer = "https://securityreports.local";
    private String audience;
    private String roleClaim = "role";
    private String nameClaim = "name";
}
