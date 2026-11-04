package com.smxplore.activitiesevents.infrastructure.config;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtConfigurationTest {

    private static final String ISSUER = "sm-xplore-ms-01";
    private static final String AUDIENCE = "sm-xplore-services";
    private static final String USER_ID = UUID.randomUUID().toString();

    @Test
    void decodesAnRs256TokenWithTheConfiguredPublicKey(@TempDir Path directory) throws Exception {
        var keyPair = keyPair();
        var decoder = decoder(directory, keyPair);

        var decoded = decoder.decode(token(keyPair, claims().build())).block();

        assertThat(decoded).isNotNull();
        assertThat(decoded.getSubject()).isEqualTo(USER_ID);
        assertThat(decoded.getClaimAsString("role")).isEqualTo("TURISTA");
    }

    @Test
    void rejectsTokensCarryingTheMfaChallengeScope(@TempDir Path directory) throws Exception {
        var keyPair = keyPair();
        var decoder = decoder(directory, keyPair);

        var mfaToken = token(keyPair, claims().claim("scope", List.of("mfa")).build());

        assertThatThrownBy(() -> decoder.decode(mfaToken).block())
                .isInstanceOf(JwtException.class);
    }

    @Test
    void onlyAcceptsTheRs256Algorithm() {
        var validator = JwtConfiguration.requireRs256();
        var rs512 = Jwt.withTokenValue("token").header("alg", "RS512").subject("user").build();
        var rs256 = Jwt.withTokenValue("token").header("alg", "RS256").subject("user").build();

        assertThat(validator.validate(rs512).hasErrors()).isTrue();
        assertThat(validator.validate(rs256).hasErrors()).isFalse();
    }

    @Test
    void rejectsTokensSignedWithAnotherRsaAlgorithm(@TempDir Path directory) throws Exception {
        var keyPair = keyPair();
        var decoder = decoder(directory, keyPair);

        var rs512Token = token(keyPair, claims().build(), SignatureAlgorithm.RS512);

        assertThatThrownBy(() -> decoder.decode(rs512Token).block())
                .isInstanceOf(JwtException.class);
    }

    @Test
    void rejectsHs256Tokens(@TempDir Path directory) throws Exception {
        var keyPair = keyPair();
        var decoder = decoder(directory, keyPair);
        SecretKey secret = new SecretKeySpec(
                "01234567890123456789012345678901".getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        var encoder = new NimbusJwtEncoder(new ImmutableSecret<>(secret));
        var hsToken = encoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(), claims().build())).getTokenValue();

        assertThatThrownBy(() -> decoder.decode(hsToken).block())
                .isInstanceOf(JwtException.class);
    }

    @Test
    void requiresAnIssuer() {
        var properties = new JwtProperties();
        properties.setIssuer("  ");

        assertThatThrownBy(() -> new JwtConfiguration(properties, new DefaultResourceLoader()).jwtDecoder())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("issuer");
    }

    @Test
    void requiresAJwkSetUriOrAPublicKey() {
        var properties = new JwtProperties();

        assertThatThrownBy(() -> new JwtConfiguration(properties, new DefaultResourceLoader()).jwtDecoder())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("RS256");
    }

    private static ReactiveJwtDecoder decoder(Path directory, KeyPair keyPair) throws Exception {
        var pem = directory.resolve("public.pem");
        Files.writeString(pem, publicKeyPem((RSAPublicKey) keyPair.getPublic()));
        var properties = new JwtProperties();
        properties.setPublicKeyLocation(pem.toUri().toString());
        properties.setIssuer(ISSUER);
        properties.setAudience(AUDIENCE);
        return new JwtConfiguration(properties, new DefaultResourceLoader()).jwtDecoder();
    }

    private static JwtClaimsSet.Builder claims() {
        var now = Instant.now();
        return JwtClaimsSet.builder()
                .issuer(ISSUER)
                .audience(List.of(AUDIENCE))
                .subject(USER_ID)
                .issuedAt(now)
                .expiresAt(now.plusSeconds(300))
                .claim("role", "TURISTA");
    }

    private static String token(KeyPair keyPair, JwtClaimsSet claims) {
        return token(keyPair, claims, SignatureAlgorithm.RS256);
    }

    private static String token(KeyPair keyPair, JwtClaimsSet claims, SignatureAlgorithm algorithm) {
        var rsaKey = new RSAKey.Builder((RSAPublicKey) keyPair.getPublic())
                .privateKey((RSAPrivateKey) keyPair.getPrivate())
                .build();
        var encoder = new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(rsaKey)));
        return encoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(algorithm).build(), claims)).getTokenValue();
    }

    private static KeyPair keyPair() throws Exception {
        var generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        return generator.generateKeyPair();
    }

    private static String publicKeyPem(RSAPublicKey publicKey) {
        var encoded = Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.US_ASCII))
                .encodeToString(publicKey.getEncoded());
        return "-----BEGIN PUBLIC KEY-----\n" + encoded + "\n-----END PUBLIC KEY-----\n";
    }
}
