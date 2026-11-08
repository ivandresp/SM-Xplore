package com.smxplore.users.application.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.smxplore.users.domain.model.Review;
import com.smxplore.users.domain.model.ReviewTargetType;
import java.lang.reflect.RecordComponent;
import java.time.Instant;
import java.util.Arrays;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Pruebas de regresión de MS01-10: la vista pública de una reseña no incluye el identificador del turista. */
class PublicReviewViewTest {

    private static final Instant NOW = Instant.parse("2026-01-01T10:00:00Z");
    private static final UUID TOURIST_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID TARGET_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @Test
    void doesNotExposeTheTouristIdentifier() {
        assertThat(Arrays.stream(PublicReviewView.class.getRecordComponents()).map(RecordComponent::getName))
                .doesNotContain("touristId");
    }

    @Test
    void mapsTheVisibleReviewData() {
        Review review = Review.create(TOURIST_ID, ReviewTargetType.GUIDE, TARGET_ID, 4, "Muy buen recorrido", NOW);

        PublicReviewView view = PublicReviewView.from(review);

        assertThat(view.id()).isEqualTo(review.id());
        assertThat(view.targetType()).isEqualTo("GUIDE");
        assertThat(view.targetId()).isEqualTo(TARGET_ID);
        assertThat(view.rating()).isEqualTo(4);
        assertThat(view.comment()).isEqualTo("Muy buen recorrido");
        assertThat(view.response()).isNull();
        assertThat(view.createdAt()).isEqualTo(NOW);
        assertThat(view.updatedAt()).isEqualTo(NOW);
    }
}
