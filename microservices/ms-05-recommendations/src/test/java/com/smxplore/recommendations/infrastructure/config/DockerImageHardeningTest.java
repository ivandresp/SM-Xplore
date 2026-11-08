package com.smxplore.recommendations.infrastructure.config;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * MS05-15: higiene de la imagen de ms-05.
 *
 * <p>La imagen ya está endurecida (bases pineadas por digest, usuario no root y sin
 * {@code apt-get upgrade}); este test lo fija para que una edición futura no lo deshaga. El
 * escaneo de dependencias/SBOM en CI queda como pendiente de plataforma y se documenta en el
 * README como riesgo aceptado.</p>
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
    void doesNotUpgradePackagesDuringTheBuild() {
        assertThat(dockerfile).doesNotContain("apt-get upgrade");
        assertThat(dockerfile).contains("apt-get install -y --no-install-recommends curl");
    }

    private static String dockerfile() {
        try {
            return Files.readString(Path.of("Dockerfile"));
        } catch (IOException exception) {
            throw new IllegalStateException("Dockerfile is not readable from the service directory", exception);
        }
    }
}
