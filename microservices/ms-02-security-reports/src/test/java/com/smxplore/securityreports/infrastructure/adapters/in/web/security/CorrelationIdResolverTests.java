package com.smxplore.securityreports.infrastructure.adapters.in.web.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * MS02-08: el correlation id es acotado; si el cliente envia algo invalido se genera uno nuevo.
 */
class CorrelationIdResolverTests {

    @Test
    void keepsValidIdentifiers() {
        assertThat(CorrelationIdResolver.resolve("abc-123.DEF_456:789")).isEqualTo("abc-123.DEF_456:789");
        assertThat(CorrelationIdResolver.resolve("a".repeat(128))).hasSize(128);
    }

    @Test
    void generatesANewIdentifierWhenTheHeaderIsMissing() {
        assertThat(CorrelationIdResolver.resolve(null)).hasSize(36);
        assertThat(CorrelationIdResolver.resolve("   ")).hasSize(36);
    }

    @Test
    void replacesOversizedIdentifiersInsteadOfPersistingThem() {
        String oversized = "a".repeat(129);

        assertThat(CorrelationIdResolver.resolve(oversized)).hasSize(36).isNotEqualTo(oversized);
    }

    @Test
    void replacesIdentifiersWithControlCharactersOrSeparators() {
        assertThat(CorrelationIdResolver.resolve("abc\n123")).hasSize(36);
        assertThat(CorrelationIdResolver.resolve("abc 123")).hasSize(36);
        assertThat(CorrelationIdResolver.resolve("abc<script>")).hasSize(36);
    }
}
