package com.smxplore.recommendations.application.preference;

import com.smxplore.recommendations.domain.exception.CategoryNotFoundException;
import com.smxplore.recommendations.domain.exception.InvalidPreferenceException;
import com.smxplore.recommendations.domain.model.preference.Preference;
import com.smxplore.recommendations.domain.model.preference.PreferenceSource;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AddPreferenceServiceTest {

    private static final UUID TOURIST = UUID.randomUUID();
    private static final UUID CATEGORY = UUID.randomUUID();
    private static final Instant NOW = Instant.parse("2026-10-26T12:00:00Z");

    @Mock
    private RecommendationGraphPort graphPort;

    private AddPreferenceService service;

    @BeforeEach
    void setUp() {
        service = new AddPreferenceService(graphPort, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void addsExplicitPreference() {
        when(graphPort.upsertPreference(eq(TOURIST), eq(CATEGORY), eq("es"), any(Instant.class)))
                .thenReturn(Mono.just(true));

        Preference preference = service.handle(TOURIST, CATEGORY, "es").block();

        assertThat(preference).isNotNull();
        assertThat(preference.source()).isEqualTo(PreferenceSource.EXPLICIT);
        assertThat(preference.weight()).isEqualTo(1.0);
        assertThat(preference.updatedAt()).isEqualTo(NOW);
    }

    @Test
    void rejectsUnknownCategory() {
        when(graphPort.upsertPreference(eq(TOURIST), eq(CATEGORY), eq("es"), any(Instant.class)))
                .thenReturn(Mono.just(false));

        StepVerifier.create(service.handle(TOURIST, CATEGORY, "es"))
                .expectError(CategoryNotFoundException.class)
                .verify();
    }

    @Test
    void rejectsMissingIds() {
        assertThatThrownBy(() -> service.handle(null, CATEGORY, "es"))
                .isInstanceOf(InvalidPreferenceException.class);
    }
}
