package com.smxplore.recommendations.infrastructure.config;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.proc.SecurityContext;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Instant;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

class JwtConfigurationTest {

    @Test
    void decodesAValidRs256Token() throws Exception {
        JwtProperties properties = new JwtProperties();
        properties.setPublicKeyLocation("classpath:keys/test-public.pem");
        properties.setIssuer("https://ms-05.local");

        ReactiveJwtDecoder decoder = new JwtConfiguration(properties, new DefaultResourceLoader()).jwtDecoder();

        RSAPublicKey publicKey = readPublicKey();
        RSAPrivateKey privateKey = readPrivateKey();
        RSAKey rsaKey = new RSAKey.Builder(publicKey)
                .privateKey(privateKey)
                .keyID("test-key")
                .build();
        JWKSet jwkSet = new JWKSet(rsaKey);
        JwtEncoder encoder = new NimbusJwtEncoder(new ImmutableJWKSet<SecurityContext>(jwkSet));

        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("https://ms-05.local")
                .subject("66666666-6666-6666-6666-666666666601")
                .issuedAt(now)
                .expiresAt(now.plusSeconds(300))
                .claim("role", "TOURIST")
                .build();
        JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).build();
        String token = encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();

        var jwt = decoder.decode(token).block();

        assertThat(jwt).isNotNull();
        assertThat(jwt.getSubject()).isEqualTo("66666666-6666-6666-6666-666666666601");
        assertThat(jwt.getClaimAsString("role")).isEqualTo("TOURIST");
    }

    @Test
    void rejectsMissingDecoderConfiguration() {
        JwtProperties properties = new JwtProperties();
        var configuration = new JwtConfiguration(properties, new DefaultResourceLoader());

        org.assertj.core.api.Assertions.assertThatThrownBy(configuration::jwtDecoder)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("RS256");
    }

    private static RSAPublicKey readPublicKey() throws Exception {
        String pem = readPem("classpath:keys/test-public.pem")
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replaceAll("\\s", "");
        byte[] decoded = Base64.getDecoder().decode(pem);
        return (RSAPublicKey) KeyFactory.getInstance("RSA")
                .generatePublic(new java.security.spec.X509EncodedKeySpec(decoded));
    }

    private static RSAPrivateKey readPrivateKey() throws Exception {
        String pem = readPem("classpath:keys/test-private.pem")
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s", "");
        byte[] decoded = Base64.getDecoder().decode(pem);
        return (RSAPrivateKey) KeyFactory.getInstance("RSA")
                .generatePrivate(new PKCS8EncodedKeySpec(decoded));
    }

    private static String readPem(String location) throws Exception {
        try (InputStream input = new DefaultResourceLoader().getResource(location).getInputStream()) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
