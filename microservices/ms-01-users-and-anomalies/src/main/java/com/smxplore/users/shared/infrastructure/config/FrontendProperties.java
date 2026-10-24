package com.smxplore.users.shared.infrastructure.config;

import java.util.Objects;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.frontend")
public record FrontendProperties(String baseUrl, String resetPasswordPath) {

    public FrontendProperties {
        Objects.requireNonNull(baseUrl, "La URL del frontend es obligatoria");
        resetPasswordPath =
                resetPasswordPath == null || resetPasswordPath.isBlank() ? "/reset-password" : resetPasswordPath;
    }
}
