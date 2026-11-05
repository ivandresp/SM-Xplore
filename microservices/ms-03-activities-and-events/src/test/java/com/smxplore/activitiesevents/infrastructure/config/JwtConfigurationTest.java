package com.smxplore.activitiesevents.infrastructure.config;

import com.nimbusds.jose.jwk.JWKSet;
import com.sun.net.httpserver.HttpServer;
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
import java.net.InetSocketAddress;
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
        assertThat(decoded.getClaimAsString("role")).isEqualTo("TOURIST");
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
    void rejectsMfaChallengeInTheScpClaim(@TempDir Path directory) throws Exception {
        var keyPair = keyPair();
        var decoder = decoder(directory, keyPair);

        var token = token(keyPair, claims().claim("scp", "mfa").build());

        assertThatThrownBy(() -> decoder.decode(token).block())
                .isInstanceOf(JwtException.class);
    }

    @Test
    void decodesWithTheRemoteJwksBranch() throws Exception {
        var keyPair = keyPair();
        var jwk = new RSAKey.Builder((RSAPublicKey) keyPair.getPublic())
                .keyID("test-key")
                .build()
                .toPublicJWK()
                .toJSONString();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/jwks", exchange -> {
            var body = ("{\"keys\":[" + jwk + "]}").getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (var output = exchange.getResponseBody()) {
                output.write(body);
            }
        });
        server.start();
        try {
            var properties = properties();
            properties.setJwkSetUri("http://127.0.0.1:" + server.getAddress().getPort() + "/jwks");
            var decoder = new JwtConfiguration(properties, new DefaultResourceLoader()).jwtDecoder();

            var decoded = decoder.decode(token(keyPair, claims().build())).block();

            assertThat(decoded).isNotNull();
            assertThat(decoded.getSubject()).isEqualTo(USER_ID);
        } finally {
            server.stop(0);
        }
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
        var properties = properties();
        properties.setIssuer("  ");

        assertThatThrownBy(() -> new JwtConfiguration(properties, new DefaultResourceLoader()).jwtDecoder())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("issuer");
    }

    @Test
    void requiresAnAudience() {
        var properties = properties();
        properties.setAudience("  ");

        assertThatThrownBy(() -> new JwtConfiguration(properties, new DefaultResourceLoader()).jwtDecoder())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("audience");
    }

    @Test
    void requiresAJwkSetUriOrAPublicKey() {
        var properties = properties();

        assertThatThrownBy(() -> new JwtConfiguration(properties, new DefaultResourceLoader()).jwtDecoder())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("RS256");
    }

    @Test
    void rejectsPlainHttpJwksForRemoteHosts() {
        var properties = properties();
        properties.setJwkSetUri("http://issuer.example/.well-known/jwks.json");

        assertThatThrownBy(() -> new JwtConfiguration(properties, new DefaultResourceLoader()).jwtDecoder())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("https");
    }

    @Test
    void acceptsPlainHttpJwksForANonLoopbackHostWhenExplicitlyAllowed() {
        var properties = properties();
        properties.setAllowInsecureJwks(true);
        properties.setJwkSetUri("http://ms-01:8081/.well-known/jwks.json");

        assertThat(new JwtConfiguration(properties, new DefaultResourceLoader()).jwtDecoder()).isNotNull();
    }

    @Test
    void acceptsHttpsJwksWithoutFetchingItAtStartup() {
        var properties = properties();
        properties.setJwkSetUri("https://issuer.example/.well-known/jwks.json");

        assertThat(new JwtConfiguration(properties, new DefaultResourceLoader()).jwtDecoder()).isNotNull();
    }

    private static JwtProperties properties() {
        var properties = new JwtProperties();
        properties.setIssuer(ISSUER);
        properties.setAudience(AUDIENCE);
        return properties;
    }

    private static ReactiveJwtDecoder decoder(Path directory, KeyPair keyPair) throws Exception {
        var pem = directory.resolve("public.pem");
        Files.writeString(pem, publicKeyPem((RSAPublicKey) keyPair.getPublic()));
        var properties = properties();
        properties.setPublicKeyLocation(pem.toUri().toString());
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
                .claim("role", "TOURIST");
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