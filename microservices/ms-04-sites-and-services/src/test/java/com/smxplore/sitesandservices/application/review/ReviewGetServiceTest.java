package com.smxplore.sitesandservices.application.review;

import com.smxplore.sitesandservices.application.support.CatalogLookup;
import com.smxplore.sitesandservices.domain.exception.ResourceNotFoundException;
import com.smxplore.sitesandservices.domain.model.common.Actor;
import com.smxplore.sitesandservices.domain.model.common.ItemType;
import com.smxplore.sitesandservices.domain.model.common.PageResult;
import com.smxplore.sitesandservices.domain.model.common.UserRole;
import com.smxplore.sitesandservices.domain.model.review.ReputationMetrics;
import com.smxplore.sitesandservices.domain.model.review.Review;
import com.smxplore.sitesandservices.domain.model.review.ReviewFilter;
import com.smxplore.sitesandservices.domain.model.review.ReviewStatus;
import com.smxplore.sitesandservices.domain.model.review.ReviewSummary;
import com.smxplore.sitesandservices.domain.ports.out.ReviewRepositoryPort;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReviewGetServiceTest {

    private static final UUID REVIEW_ID = UUID.randomUUID();
    private static final UUID TOURIST_ID = UUID.randomUUID();
    private static final UUID OTHER_ID = UUID.randomUUID();

    private final StubReviewRepository repository = new StubReviewRepository();
    private final ReviewGetService service = new ReviewGetService(
            new CatalogLookup(null, null, null, null, repository, null));

    @Test
    void publishedReviewIsVisibleToAnyone() {
        repository.review = review(ReviewStatus.PUBLISHED);

        assertThat(service.handle(REVIEW_ID, tourist(OTHER_ID)).block()).isNotNull();
    }

    @Test
    void hiddenReviewIsVisibleToItsAuthor() {
        repository.review = review(ReviewStatus.HIDDEN);

        assertThat(service.handle(REVIEW_ID, tourist(TOURIST_ID)).block()).isNotNull();
    }

    @Test
    void hiddenReviewIsHiddenFromOtherUsers() {
        repository.review = review(ReviewStatus.HIDDEN);

        assertThatThrownBy(() -> service.handle(REVIEW_ID, tourist(OTHER_ID)).block())
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void hiddenReviewIsVisibleToAdmin() {
        repository.review = review(ReviewStatus.HIDDEN);

        assertThat(service.handle(REVIEW_ID, admin()).block()).isNotNull();
    }

    @Test
    void deletedReviewIsHiddenFromOtherUsers() {
        repository.review = review(ReviewStatus.DELETED);

        assertThatThrownBy(() -> service.handle(REVIEW_ID, tourist(OTHER_ID)).block())
                .isInstanceOf(ResourceNotFoundException.class);
    }

    private static Review review(ReviewStatus status) {
        return Review.builder()
                .reviewId(REVIEW_ID)
                .touristId(TOURIST_ID)
                .touristName("Turista")
                .itemType(ItemType.SITE)
                .itemId(UUID.randomUUID())
                .itemName("Sitio")
                .rating(5)
                .commentary("Excelente")
                .status(status)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    private static Actor tourist(UUID id) {
        return new Actor(id, UserRole.TOURIST, "Turista");
    }

    private static Actor admin() {
        return new Actor(UUID.randomUUID(), UserRole.ADMIN, "Admin");
    }

    private static final class StubReviewRepository implements ReviewRepositoryPort {

        private Review review;

        @Override
        public Mono<Review> findById(UUID reviewId) {
            return Mono.justOrEmpty(review);
        }

        @Override
        public Mono<Review> save(Review review) {
            return Mono.empty();
        }

        @Override
        public Mono<Review> findByTouristAndItem(UUID touristId, ItemType itemType, UUID itemId) {
            return Mono.empty();
        }

        @Override
        public Mono<PageResult<Review>> findAll(ReviewFilter filter) {
            return Mono.empty();
        }

        @Override
        public Mono<PageResult<Review>> findAiFlagged(int page, int size) {
            return Mono.empty();
        }

        @Override
        public Mono<ReviewSummary> summarize(ItemType itemType, UUID itemId) {
            return Mono.empty();
        }

        @Override
        public Mono<ReputationMetrics> reputation(ItemType itemType, UUID itemId) {
            return Mono.empty();
        }
    }
}
