package com.smxplore.users.infrastructure.config;

import java.util.Objects;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.frontend")
public record FrontendProperties(String baseUrl, String resetPasswordPath, String verifyEmailPath) {

    public FrontendProperties {
        Objects.requireNonNull(baseUrl, "La URL del frontend es obligatoria");
        resetPasswordPath =
                resetPasswordPath == null || resetPasswordPath.isBlank() ? "/reset-password" : resetPasswordPath;
        verifyEmailPath = verifyEmailPath == null || verifyEmailPath.isBlank() ? "/verificar-correo" : verifyEmailPath;
    }
}
