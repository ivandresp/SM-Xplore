package com.smxplore.securityreports.infrastructure.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * MS02-04: el actuator solo deja publicos health e info; metricas, Prometheus y env no.
 */
class SecurityConfigurationPublicPathsTests {

    @Test
    void onlyHealthAndInfoArePublic() {
        assertThat(SecurityConfiguration.PUBLIC_ACTUATOR_PATHS)
                .containsExactly("/actuator/health", "/actuator/health/**", "/actuator/info");
    }

    @Test
    void neverExposesMetricsPrometheusOrEnvironmentWithoutAToken() {
        assertThat(SecurityConfiguration.PUBLIC_ACTUATOR_PATHS)
                .noneMatch(path -> path.contains("metrics")
                        || path.contains("prometheus")
                        || path.contains("env")
                        || path.contains("beans")
                        || path.contains("configprops"));
    }
}
