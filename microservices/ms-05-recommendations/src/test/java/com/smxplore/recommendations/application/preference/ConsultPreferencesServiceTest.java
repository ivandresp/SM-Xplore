package com.smxplore.recommendations.application.preference;

import com.smxplore.recommendations.domain.exception.InvalidPreferenceException;
import com.smxplore.recommendations.domain.model.preference.Preference;
import com.smxplore.recommendations.domain.model.preference.PreferenceSource;
import com.smxplore.recommendations.domain.ports.out.RecommendationGraphPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConsultPreferencesServiceTest {

    private static final UUID TOURIST = UUID.randomUUID();
    private static final UUID CATEGORY = UUID.randomUUID();
    private static final Instant NOW = Instant.parse("2026-10-26T12:00:00Z");

    @Mock
    private RecommendationGraphPort graphPort;

    private ConsultPreferencesService service;

    @BeforeEach
    void setUp() {
        service = new ConsultPreferencesService(graphPort);
    }

    @Test
    void listsThePreferencesOfTheTourist() {
        Preference preference = Preference.builder()
                .touristId(TOURIST)
                .categoryId(CATEGORY)
                .weight(0.15)
                .source(PreferenceSource.INFERRED)
                .updatedAt(NOW)
                .build();
        when(graphPort.findPreferences(TOURIST)).thenReturn(Flux.just(preference));

        StepVerifier.create(service.handle(TOURIST))
                .assertNext(found -> assertThat(found).isEqualTo(preference))
                .verifyComplete();
    }

    @Test
    void rejectsMissingTourist() {
        assertThatThrownBy(() -> service.handle(null))
                .isInstanceOf(InvalidPreferenceException.class);
    }
}
