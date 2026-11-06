package com.smxplore.recommendations;

import com.nimbusds.jose.jwk.RSAKey;
import org.springframework.core.io.DefaultResourceLoader;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

public final class TestKeys {

    private TestKeys() {
    }

    public static RSAKey rsaKey() {
        return new RSAKey.Builder(publicKey())
                .privateKey(privateKey())
                .keyID("test-key")
                .build();
    }

    public static RSAPublicKey publicKey() {
        try {
            String pem = readPem("keys/test-public.pem")
                    .replace("-----BEGIN PUBLIC KEY-----", "")
                    .replace("-----END PUBLIC KEY-----", "")
                    .replaceAll("\\s", "");
            byte[] decoded = Base64.getDecoder().decode(pem);
            return (RSAPublicKey) KeyFactory.getInstance("RSA")
                    .generatePublic(new X509EncodedKeySpec(decoded));
        } catch (Exception exception) {
            throw new IllegalStateException("Could not read test public key", exception);
        }
    }

    public static RSAPrivateKey privateKey() {
        try {
            String pem = readPem("keys/test-private.pem")
                    .replace("-----BEGIN PRIVATE KEY-----", "")
                    .replace("-----END PRIVATE KEY-----", "")
                    .replaceAll("\\s", "");
            byte[] decoded = Base64.getDecoder().decode(pem);
            return (RSAPrivateKey) KeyFactory.getInstance("RSA")
                    .generatePrivate(new PKCS8EncodedKeySpec(decoded));
        } catch (Exception exception) {
            throw new IllegalStateException("Could not read test private key", exception);
        }
    }

    private static String readPem(String location) throws Exception {
        try (InputStream input = new DefaultResourceLoader().getResource("classpath:" + location).getInputStream()) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
