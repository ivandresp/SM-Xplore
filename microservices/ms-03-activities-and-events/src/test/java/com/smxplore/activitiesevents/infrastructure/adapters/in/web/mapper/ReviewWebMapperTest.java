package com.smxplore.activitiesevents.infrastructure.adapters.in.web.mapper;

import com.smxplore.activitiesevents.domain.model.review.Review;
import com.smxplore.activitiesevents.domain.model.review.ReviewAiAssessment;
import com.smxplore.activitiesevents.domain.model.review.ReviewItemType;
import com.smxplore.activitiesevents.domain.model.review.ReviewStatus;
import com.smxplore.activitiesevents.domain.model.review.ReviewUser;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ReviewWebMapperTest {

    private final ReviewWebMapper mapper = new ReviewWebMapper();

    @Test
    void thePublicViewDoesNotLeakTheAiVerdict() {
        var review = review();

        var response = mapper.toResponse(review);

        assertThat(response.id()).isEqualTo(review.getId());
        assertThat(response.user().name()).isEqualTo("Turista");
        // El record publico no tiene campo ai: si se agrega de nuevo, este test no compila.
        assertThat(ReviewResponseFields.names()).doesNotContain("ai", "aiRetained");
    }

    @Test
    void theAdminViewCarriesTheAiVerdictAndTheRetentionFlag() {
        var review = review();

        var response = mapper.toAdminResponse(review);

        assertThat(response.ai()).isNotNull();
        assertThat(response.ai().flagged()).isTrue();
        assertThat(response.ai().score()).isEqualTo(0.91);
        assertThat(response.aiRetained()).isTrue();
    }

    private static Review review() {
        var assessment = ReviewAiAssessment.assessed(
                true, 0.91, List.of("SPAM"), "Campana coordinada", "littlellm", Instant.now());
        return Review.builder()
                .id(UUID.randomUUID())
                .itemId(UUID.randomUUID())
                .itemType(ReviewItemType.ACTIVITY)
                .user(new ReviewUser(UUID.randomUUID(), "Turista"))
                .rating(5)
                .comment("Comentario")
                .status(ReviewStatus.PENDING_REVIEW)
                .createdAt(Instant.now())
                .aiAssessment(assessment)
                .aiRetained(true)
                .build();
    }

    /** Nombres de los componentes del DTO publico, leidos por reflexion. */
    private static final class ReviewResponseFields {
        static List<String> names() {
            return java.util.Arrays.stream(
                            com.smxplore.activitiesevents.infrastructure.adapters.in.web.dto.ReviewResponse.class
                                    .getRecordComponents())
                    .map(java.lang.reflect.RecordComponent::getName)
                    .toList();
        }
    }
}
