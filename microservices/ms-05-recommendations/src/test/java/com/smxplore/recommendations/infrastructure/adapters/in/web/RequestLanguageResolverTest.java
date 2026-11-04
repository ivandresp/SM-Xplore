package com.smxplore.recommendations.infrastructure.adapters.in.web;

import com.smxplore.recommendations.infrastructure.adapters.in.web.language.RequestLanguageResolver;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RequestLanguageResolverTest {

    private final RequestLanguageResolver resolver = new RequestLanguageResolver();

    @Test
    void defaultsToSpanishWhenHeaderIsMissingOrInvalid() {
        assertThat(resolver.resolve(null)).isEqualTo("es");
        assertThat(resolver.resolve(" ")).isEqualTo("es");
        assertThat(resolver.resolve("not a language!!")).isEqualTo("es");
        assertThat(resolver.resolve("*")).isEqualTo("es");
    }

    @Test
    void takesTheFirstAcceptableLanguage() {
        assertThat(resolver.resolve("en-US,en;q=0.9,es;q=0.8")).isEqualTo("en-us");
        assertThat(resolver.resolve("fr")).isEqualTo("fr");
        assertThat(resolver.resolve("pt_BR")).isEqualTo("pt-br");
    }

    @Test
    void ignoresWildcardsAndFallsBackToTheNextValidTag() {
        assertThat(resolver.resolve("*, en;q=0.8")).isEqualTo("en");
    }
}
