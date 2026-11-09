package com.smxplore.recommendations.infrastructure.config;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * MS05-15: higiene de la imagen de ms-05.
 *
 * <p>La imagen está endurecida: bases pineadas por digest, usuario no root y sin paquetes del
 * sistema instalados durante el build. El healthcheck corre sobre la propia JVM, así que la imagen
 * no necesita {@code curl} ni {@code apt}; además se purgan para que un escaneo de vulnerabilidades
 * no los reporte. Este test fija esos invariantes.</p>
 */
class DockerImageHardeningTest {

    private final String dockerfile = dockerfile();

    @Test
    void pinsBothBaseImagesByDigest() {
        assertThat(dockerfile).contains("FROM eclipse-temurin:25-jdk@sha256:");
        assertThat(dockerfile).contains("FROM eclipse-temurin:25-jre@sha256:");
    }

    @Test
    void runsAsANonRootUser() {
        assertThat(dockerfile).contains("USER app");
        assertThat(dockerfile).contains("groupadd --system app");
        assertThat(dockerfile).contains("useradd --system --gid app --home-dir /app app");
    }

    @Test
    void doesNotInstallSystemPackagesDuringTheBuild() {
        assertThat(dockerfile).doesNotContain("apt-get");
        assertThat(dockerfile).doesNotContain("curl -fsS");
        assertThat(dockerfile).contains("dpkg --purge apt");
    }

    @Test
    void healthcheckRunsOnTheJvmWithoutExtraPackages() {
        assertThat(dockerfile).contains("RUN javac -d /workspace/healthcheck/classes /workspace/healthcheck/HealthCheck.java");
        assertThat(dockerfile).contains("\"java\", \"-cp\", \"/app/healthcheck\", \"HealthCheck\"");
    }

    private static String dockerfile() {
        try {
            return Files.readString(Path.of("Dockerfile"));
        } catch (IOException exception) {
            throw new IllegalStateException("Dockerfile is not readable from the service directory", exception);
        }
    }
}
