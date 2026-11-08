package com.smxplore.recommendations.infrastructure.config;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression tests for MS05-06, MS05-07 and MS05-08: the defaults shipped in the configuration
 * must be the secure ones, and every relaxation must live in an explicit profile.
 *
 * <p>The assertions read the raw YAML on purpose: resolving the properties would hide exactly the
 * placeholder default this test is pinning.</p>
 */
class SecurityDefaultsTest {

    private static String resource(String name) throws Exception {
        return new ClassPathResource(name).getContentAsString(StandardCharsets.UTF_8);
    }

    @Test
    void enablesTheAuthVersionCheckByDefault() throws Exception {
        // MS05-06: a stolen token is only safe if ms-01 is asked for the current version.
        assertThat(resource("application.yaml")).contains("${AUTH_VERSION_ENABLED:true}");
    }

    @Test
    void keepsMetricsPrometheusAndApiDocsClosedByDefault() throws Exception {
        // MS05-07: only health and info are exposed; docs are opt-in.
        String base = resource("application.yaml");
        assertThat(base).contains("${MANAGEMENT_ENDPOINTS:health,info}");
        assertThat(base).contains("${SECURITY_DOCS_PUBLIC:false}");
        assertThat(base).contains("${SPRINGDOC_API_DOCS_ENABLED:false}");
        assertThat(base).contains("${SPRINGDOC_SWAGGER_UI_ENABLED:false}");
    }

    @Test
    void doesNotRunSchemaDdlUnlessItIsExplicitlyEnabled() throws Exception {
        // MS05-08: the application user does not need DDL privileges by default.
        assertThat(resource("application.yaml")).contains("${NEO4J_INITIALIZE_SCHEMA:false}");
    }

    @Test
    void namespacesTheCacheVersionKeyUnderTheServicePrefix() throws Exception {
        // MS05-05: another workload sharing Redis cannot invalidate the cache with a generic key.
        assertThat(resource("application.yaml"))
                .contains("${RECOMMENDATIONS_CACHE_VERSION_KEY:ms05:recommendations:graph:version}");
    }

    @Test
    void relaxesOnlyTheDocumentedValuesInTheLocalProfile() throws Exception {
        String local = resource("application-local.yaml");
        assertThat(local).contains("docs-public: true");
        assertThat(local).contains("initialize-schema: true");
        assertThat(local).contains("health,info,metrics,prometheus");
        // Local development has no ms-01 available, so the revocation check is explicitly off.
        assertThat(local).contains("enabled: false");
    }

    @Test
    void disablesTheAuthVersionCheckOnlyInTheTestProfile() throws Exception {
        String test = resource("application-test.yaml");
        assertThat(test).contains("auth-version:");
        assertThat(test).contains("enabled: false");
        assertThat(test).contains("docs-public: false");
    }
}
