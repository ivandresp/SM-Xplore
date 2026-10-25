package com.smxplore.users.shared.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nimbusds.jose.jwk.RSAKey;
import com.smxplore.users.shared.infrastructure.config.JwtProperties;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JwtKeyStoreTest {

    private static final String ISSUER = "sm-xplore-ms-01";
    private static final String AUDIENCE = "sm-xplore-services";

    @TempDir
    Path tempDir;

    @Test
    void generatesTheKeyPairOnceAndReusesItOnLaterStarts() throws Exception {
        Path keyStore = tempDir.resolve("jwt/keys.pem");

        RSAKey first = JwtKeyStore.load(properties(null, null, keyStore, true));
        RSAKey second = JwtKeyStore.load(properties(null, null, keyStore, true));

        assertThat(Files.exists(keyStore)).isTrue();
        assertThat(first.getKeyID()).isNotBlank().isEqualTo(second.getKeyID());
        assertThat(second.toPublicJWK().toJSONString())
                .isEqualTo(first.toPublicJWK().toJSONString());
    }

    @Test
    void readsTheKeyPairFromConfiguredPemValues() throws Exception {
        Path keyStore = tempDir.resolve("keys.pem");
        RSAKey generated = JwtKeyStore.load(properties(null, null, keyStore, true));
        String pem = Files.readString(keyStore, StandardCharsets.UTF_8);

        RSAKey configured = JwtKeyStore.load(properties(pem, pem, tempDir.resolve("unused.pem"), false));

        assertThat(configured.getKeyID()).isEqualTo(generated.getKeyID());
        assertThat(configured.toPublicJWK().toJSONString())
                .isEqualTo(generated.toPublicJWK().toJSONString());
    }

    @Test
    void derivesTheKeyIdFromThePublicKeyThumbprint() throws Exception {
        RSAKey key = JwtKeyStore.load(properties(null, null, tempDir.resolve("keys.pem"), true));

        assertThat(key.getKeyID()).isEqualTo(key.computeThumbprint().toString());
    }

    @Test
    void failsWhenThereAreNoKeysAndGenerationIsDisabled() {
        JwtProperties properties = properties(null, null, tempDir.resolve("missing.pem"), false);

        assertThatThrownBy(() -> JwtKeyStore.load(properties))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No hay llaves JWT configuradas");
    }

    @Test
    void rejectsAKeyStoreWithoutBothPemBlocks() throws Exception {
        Path keyStore = tempDir.resolve("broken.pem");
        Files.writeString(keyStore, "-----BEGIN PRIVATE KEY-----\nAAAA\n-----END PRIVATE KEY-----\n");

        assertThatThrownBy(() -> JwtKeyStore.load(properties(null, null, keyStore, false)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("no contiene ambas llaves PEM");
    }

    private static JwtProperties properties(
            String privateKey, String publicKey, Path keyStore, boolean allowGenerated) {
        return new JwtProperties(
                ISSUER, AUDIENCE, Duration.ofMinutes(60), privateKey, publicKey, keyStore, allowGenerated);
    }
}
