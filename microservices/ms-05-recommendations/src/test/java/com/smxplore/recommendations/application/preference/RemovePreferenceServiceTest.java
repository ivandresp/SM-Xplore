package com.smxplore.recommendations.application.preference;

import com.smxplore.recommendations.domain.exception.InvalidPreferenceException;
import com.smxplore.recommendations.domain.ports.out.RecommendationGraphPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RemovePreferenceServiceTest {

    private static final UUID TOURIST = UUID.randomUUID();
    private static final UUID CATEGORY = UUID.randomUUID();

    @Mock
    private RecommendationGraphPort graphPort;

    private RemovePreferenceService service;

    @BeforeEach
    void setUp() {
        service = new RemovePreferenceService(graphPort);
    }

    @Test
    void removesPreference() {
        when(graphPort.deletePreference(TOURIST, CATEGORY)).thenReturn(Mono.empty());

        StepVerifier.create(service.handle(TOURIST, CATEGORY))
                .verifyComplete();

        verify(graphPort).deletePreference(TOURIST, CATEGORY);
    }

    @Test
    void rejectsMissingIds() {
        assertThatThrownBy(() -> service.handle(TOURIST, null))
                .isInstanceOf(InvalidPreferenceException.class);
    }
}
