package com.smxplore.securityreports.infrastructure.adapters.out.policy;

import com.smxplore.securityreports.domain.exceptions.InvalidReportRequestException;
import com.smxplore.securityreports.infrastructure.config.PhotoUrlProperties;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * MS02-07: URL de fotos con allowlist de hosts y https obligatorio.
 */
class PhotoUrlPolicyAdapterTests {

    @Test
    void acceptsHttpsUrlsOnAllowedHostsAndSubdomains() {
        var policy = policy(List.of("cdn.example.com", "*.images.example.com"));

        assertThatCode(() -> policy.validate("https://cdn.example.com/a.jpg")).doesNotThrowAnyException();
        assertThatCode(() -> policy.validate("https://img.images.example.com/b.jpg")).doesNotThrowAnyException();
    }

    @Test
    void rejectsHttpByDefault() {
        var policy = policy(List.of("cdn.example.com"));

        assertThatThrownBy(() -> policy.validate("http://cdn.example.com/a.jpg"))
                .isInstanceOf(InvalidReportRequestException.class)
                .hasMessageContaining("https");
    }

    @Test
    void rejectsHostsOutsideTheAllowlist() {
        var policy = policy(List.of("cdn.example.com"));

        assertThatThrownBy(() -> policy.validate("https://attacker.example/a.jpg"))
                .isInstanceOf(InvalidReportRequestException.class)
                .hasMessageContaining("not allowed");
    }

    @Test
    void rejectsEveryUrlWhenNoAllowlistIsConfigured() {
        var policy = policy(List.of());

        assertThatThrownBy(() -> policy.validate("https://cdn.example.com/a.jpg"))
                .isInstanceOf(InvalidReportRequestException.class)
                .hasMessageContaining("not allowed");
    }

    @Test
    void rejectsNonHttpSchemes() {
        var policy = policy(List.of("cdn.example.com"));

        assertThatThrownBy(() -> policy.validate("javascript:alert(1)"))
                .isInstanceOf(InvalidReportRequestException.class);
    }

    @Test
    void allowAnyHostIsAnExplicitDevelopmentRelaxation() {
        var properties = new PhotoUrlProperties();
        properties.setAllowAnyHost(true);
        properties.normalize();
        var policy = new PhotoUrlPolicyAdapter(properties);

        assertThatCode(() -> policy.validate("https://anywhere.example/a.jpg")).doesNotThrowAnyException();
    }

    private static PhotoUrlPolicyAdapter policy(List<String> hosts) {
        var properties = new PhotoUrlProperties();
        properties.setAllowedHosts(hosts);
        properties.normalize();
        return new PhotoUrlPolicyAdapter(properties);
    }
}
