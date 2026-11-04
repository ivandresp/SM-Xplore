package com.smxplore.activitiesevents;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mongodb.MongoDBContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
class ApplicationContextTest {

    @Container
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

    @Container
    private static final MongoDBContainer MONGO = new MongoDBContainer("mongo:7.0");

    private static final Path PUBLIC_KEY = writePublicKey();

    @Autowired
    private Flyway flyway;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        var jdbcUrl = "jdbc:postgresql://%s:%d/%s".formatted(
                POSTGRES.getHost(), POSTGRES.getMappedPort(5432), POSTGRES.getDatabaseName());
        var r2dbcUrl = "r2dbc:postgresql://%s:%d/%s".formatted(
                POSTGRES.getHost(), POSTGRES.getMappedPort(5432), POSTGRES.getDatabaseName());
        registry.add("spring.r2dbc.url", () -> r2dbcUrl);
        registry.add("spring.r2dbc.username", POSTGRES::getUsername);
        registry.add("spring.r2dbc.password", POSTGRES::getPassword);
        registry.add("spring.flyway.url", () -> jdbcUrl);
        registry.add("spring.flyway.user", POSTGRES::getUsername);
        registry.add("spring.flyway.password", POSTGRES::getPassword);
        registry.add("spring.mongodb.uri", MONGO::getReplicaSetUrl);
        registry.add("spring.data.mongodb.auto-index-creation", () -> "false");
        registry.add("app.security.jwt.public-key-location", () -> PUBLIC_KEY.toUri().toString());
        registry.add("app.security.jwt.issuer", () -> "sm-xplore-ms-01");
        registry.add("app.security.jwt.audience", () -> "sm-xplore-services");
    }

    @Test
    void contextLoadsAndFlywayMigratesTheSchema() {
        assertThat(flyway.info().current().getVersion())
                .isGreaterThanOrEqualTo(MigrationVersion.fromVersion("2"));
    }

    private static Path writePublicKey() {
        try {
            var generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            var publicKey = (RSAPublicKey) generator.generateKeyPair().getPublic();
            var encoded = Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.US_ASCII))
                    .encodeToString(publicKey.getEncoded());
            var path = Files.createTempFile("ms-03-jwt-public-", ".pem");
            Files.writeString(path, "-----BEGIN PUBLIC KEY-----\n" + encoded
                    + "\n-----END PUBLIC KEY-----\n");
            return path;
        } catch (Exception exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }
}
