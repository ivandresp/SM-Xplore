package com.smxplore.recommendations.application.preference;

import com.smxplore.recommendations.domain.exception.InvalidPreferenceException;
import com.smxplore.recommendations.domain.exception.RateLimitExceededException;
import com.smxplore.recommendations.domain.ports.out.RecommendationGraphPort;
import com.smxplore.recommendations.domain.ports.out.RecommendationQuotaPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RemovePreferenceServiceTest {

    private static final UUID TOURIST = UUID.randomUUID();
    private static final UUID CATEGORY = UUID.randomUUID();

    @Mock
    private RecommendationGraphPort graphPort;

    @Mock
    private RecommendationQuotaPort quotaPort;

    private RemovePreferenceService service;

    @BeforeEach
    void setUp() {
        service = new RemovePreferenceService(graphPort, quotaPort);
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

    @Test
    void rejectsTheDeleteWhenThePreferenceQuotaIsExceeded() {
        doThrow(new RateLimitExceededException("Preference quota exceeded", 5))
                .when(quotaPort).checkPreference(TOURIST);

        assertThatThrownBy(() -> service.handle(TOURIST, CATEGORY))
                .isInstanceOf(RateLimitExceededException.class);

        verifyNoInteractions(graphPort);
    }
}
