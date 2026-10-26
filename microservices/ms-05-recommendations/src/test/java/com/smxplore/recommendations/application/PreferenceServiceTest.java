package com.smxplore.recommendations.application;

import com.smxplore.recommendations.domain.exception.CategoryNotFoundException;
import com.smxplore.recommendations.domain.model.Preference;
import com.smxplore.recommendations.domain.model.PreferenceSource;
import com.smxplore.recommendations.domain.ports.out.RecommendationGraphPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PreferenceServiceTest {

    private static final UUID TOURIST = UUID.randomUUID();
    private static final UUID CATEGORY = UUID.randomUUID();
    private static final Instant NOW = Instant.parse("2026-10-26T12:00:00Z");

    @Mock
    private RecommendationGraphPort graphPort;

    private PreferenceService service;

    @BeforeEach
    void setUp() {
        service = new PreferenceService(graphPort, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void addsExplicitPreference() {
        when(graphPort.upsertPreference(eq(TOURIST), eq(CATEGORY), eq("es"), any(Instant.class)))
                .thenReturn(Mono.just(true));

        Preference preference = service.addPreference(TOURIST, CATEGORY, "es").block();

        assertThat(preference).isNotNull();
        assertThat(preference.source()).isEqualTo(PreferenceSource.EXPLICIT);
        assertThat(preference.weight()).isEqualTo(1.0);
        assertThat(preference.updatedAt()).isEqualTo(NOW);
    }

    @Test
    void rejectsUnknownCategory() {
        when(graphPort.upsertPreference(eq(TOURIST), eq(CATEGORY), eq("es"), any(Instant.class)))
                .thenReturn(Mono.just(false));

        StepVerifier.create(service.addPreference(TOURIST, CATEGORY, "es"))
                .expectError(CategoryNotFoundException.class)
                .verify();
    }

    @Test
    void removesPreference() {
        when(graphPort.deletePreference(TOURIST, CATEGORY)).thenReturn(Mono.empty());

        StepVerifier.create(service.removePreference(TOURIST, CATEGORY))
                .verifyComplete();

        verify(graphPort).deletePreference(TOURIST, CATEGORY);
    }
}
