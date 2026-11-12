package com.smxplore.recommendations.application.preference;

import com.smxplore.recommendations.domain.exception.CategoryNotFoundException;
import com.smxplore.recommendations.domain.exception.InvalidPreferenceException;
import com.smxplore.recommendations.domain.exception.RateLimitExceededException;
import com.smxplore.recommendations.domain.model.preference.Preference;
import com.smxplore.recommendations.domain.model.preference.PreferenceSource;
import com.smxplore.recommendations.domain.ports.out.RecommendationGraphPort;
import com.smxplore.recommendations.domain.ports.out.RecommendationQuotaPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReplacePreferencesServiceTest {

    private static final UUID TOURIST = UUID.randomUUID();
    private static final UUID CATEGORY_A = UUID.randomUUID();
    private static final UUID CATEGORY_B = UUID.randomUUID();
    private static final Instant NOW = Instant.parse("2026-10-26T12:00:00Z");

    @Mock
    private RecommendationGraphPort graphPort;

    @Mock
    private RecommendationQuotaPort quotaPort;

    private ReplacePreferencesService service;

    @BeforeEach
    void setUp() {
        service = new ReplacePreferencesService(graphPort, quotaPort, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void replacesTheExplicitPreferencesInOneGraphCall() {
        List<Preference> stored = List.of(preference(CATEGORY_A), preference(CATEGORY_B));
        when(graphPort.replacePreferences(eq(TOURIST), anyList(), eq("es"), any(Instant.class)))
                .thenReturn(Mono.just(stored));

        List<Preference> result = service.handle(TOURIST, List.of(CATEGORY_A, CATEGORY_B), "es").block();

        assertThat(result).containsExactlyElementsOf(stored);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<UUID>> captor = ArgumentCaptor.forClass(List.class);
        verify(graphPort).replacePreferences(eq(TOURIST), captor.capture(), eq("es"), eq(NOW));
        assertThat(captor.getValue()).containsExactly(CATEGORY_A, CATEGORY_B);
        assertThat(captor.getValue()).isNotSameAs(List.of(CATEGORY_A, CATEGORY_B));
    }

    @Test
    void allowsAnEmptyListToClearTheExplicitPreferences() {
        when(graphPort.replacePreferences(eq(TOURIST), anyList(), eq("es"), any(Instant.class)))
                .thenReturn(Mono.just(List.of()));

        StepVerifier.create(service.handle(TOURIST, List.of(), "es"))
                .assertNext(preferences -> assertThat(preferences).isEmpty())
                .verifyComplete();
    }

    @Test
    void rejectsMoreThanTwentyCategories() {
        List<UUID> tooMany = IntStream.range(0, 21).mapToObj(ignored -> UUID.randomUUID()).toList();

        assertThatThrownBy(() -> service.handle(TOURIST, tooMany, "es"))
                .isInstanceOf(InvalidPreferenceException.class)
                .hasMessageContaining("20");
        verifyNoInteractions(graphPort, quotaPort);
    }

    @Test
    void rejectsDuplicateCategories() {
        assertThatThrownBy(() -> service.handle(TOURIST, List.of(CATEGORY_A, CATEGORY_A), "es"))
                .isInstanceOf(InvalidPreferenceException.class)
                .hasMessageContaining("duplicates");
        verifyNoInteractions(graphPort, quotaPort);
    }

    @Test
    void rejectsMissingIds() {
        List<UUID> withNull = new ArrayList<>();
        withNull.add(CATEGORY_A);
        withNull.add(null);

        assertThatThrownBy(() -> service.handle(null, List.of(CATEGORY_A), "es"))
                .isInstanceOf(InvalidPreferenceException.class);
        assertThatThrownBy(() -> service.handle(TOURIST, null, "es"))
                .isInstanceOf(InvalidPreferenceException.class);
        assertThatThrownBy(() -> service.handle(TOURIST, withNull, "es"))
                .isInstanceOf(InvalidPreferenceException.class);
        verifyNoInteractions(graphPort, quotaPort);
    }

    @Test
    void rejectsTheWriteWhenThePreferenceQuotaIsExceeded() {
        doThrow(new RateLimitExceededException("Preference quota exceeded", 12))
                .when(quotaPort).checkPreference(TOURIST);

        assertThatThrownBy(() -> service.handle(TOURIST, List.of(CATEGORY_A), "es"))
                .isInstanceOf(RateLimitExceededException.class);

        verifyNoInteractions(graphPort);
    }

    @Test
    void propagatesUnknownCategoriesReportedByTheGraph() {
        when(graphPort.replacePreferences(eq(TOURIST), anyList(), eq("es"), any(Instant.class)))
                .thenReturn(Mono.error(new CategoryNotFoundException(CATEGORY_A)));

        StepVerifier.create(service.handle(TOURIST, List.of(CATEGORY_A), "es"))
                .expectError(CategoryNotFoundException.class)
                .verify();
    }

    private static Preference preference(UUID categoryId) {
        return Preference.builder()
                .touristId(TOURIST)
                .categoryId(categoryId)
                .weight(1.0)
                .source(PreferenceSource.EXPLICIT)
                .updatedAt(NOW)
                .build();
    }
}
