package com.smxplore.users.infrastructure.security;

import com.smxplore.users.infrastructure.config.MfaProperties;
import java.nio.file.Path;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;

/**
 * Resuelve la llave que cifra los secretos TOTP: {@code MFA_SECRET_KEY} en cualquier entorno, o una llave local
 * persistida en desarrollo y efímera en pruebas.
 */
@Configuration
public class MfaSecretCipherConfig {

    private static final Path DEVELOPMENT_KEY_PATH = Path.of(".local", "mfa", "secret.key");

    @Bean
    public MfaSecretCipher mfaSecretCipher(MfaProperties properties, Environment environment) {
        if (properties.secretKey() != null && !properties.secretKey().isBlank()) {
            return MfaSecretCipher.fromBase64Key(properties.secretKey());
        }
        if (environment.acceptsProfiles(Profiles.of("test"))) {
            return MfaSecretCipher.ephemeral();
        }
        if (environment.acceptsProfiles(Profiles.of("dev"))) {
            return MfaSecretCipher.developmentKey(DEVELOPMENT_KEY_PATH);
        }
        throw new IllegalStateException("MFA_SECRET_KEY es obligatorio fuera de desarrollo: genera una llave AES de "
                + "32 bytes en Base64 con 'openssl rand -base64 32'");
    }
}
