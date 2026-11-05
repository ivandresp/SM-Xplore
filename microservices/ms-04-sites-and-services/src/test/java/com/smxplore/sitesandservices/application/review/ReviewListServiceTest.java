package com.smxplore.sitesandservices.application.review;

import com.smxplore.sitesandservices.domain.exception.ForbiddenOperationException;
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

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReviewListServiceTest {

    private static final UUID ITEM_ID = UUID.randomUUID();
    private static final UUID TOURIST_ID = UUID.randomUUID();
    private static final UUID OTHER_TOURIST_ID = UUID.randomUUID();

    private final CapturingRepository repository = new CapturingRepository();
    private final ReviewListService service = new ReviewListService(repository);

    @Test
    void nonAdminCannotListHiddenReviews() {
        var filter = filter(null, ReviewStatus.HIDDEN);

        assertThatThrownBy(() -> service.handle(filter, tourist()).block())
                .isInstanceOf(ForbiddenOperationException.class)
                .hasMessageContaining("status");
    }

    @Test
    void nonAdminCannotListAnotherTouristsReviews() {
        var filter = filter(OTHER_TOURIST_ID, null);

        assertThatThrownBy(() -> service.handle(filter, tourist()).block())
                .isInstanceOf(ForbiddenOperationException.class)
                .hasMessageContaining("tourist");
    }

    @Test
    void nonAdminOnlySeesPublishedReviews() {
        service.handle(filter(null, null), tourist()).block();

        assertThat(repository.lastFilter.status()).isEqualTo(ReviewStatus.PUBLISHED);
    }

    @Test
    void touristCanListOwnPublishedReviews() {
        service.handle(filter(TOURIST_ID, null), tourist()).block();

        assertThat(repository.lastFilter.touristId()).isEqualTo(TOURIST_ID);
        assertThat(repository.lastFilter.status()).isEqualTo(ReviewStatus.PUBLISHED);
    }

    @Test
    void touristCanListOwnHiddenReviews() {
        service.handle(filter(TOURIST_ID, ReviewStatus.HIDDEN), tourist()).block();

        assertThat(repository.lastFilter.touristId()).isEqualTo(TOURIST_ID);
        assertThat(repository.lastFilter.status()).isEqualTo(ReviewStatus.HIDDEN);
    }

    @Test
    void managerCanFilterPublishedReviewsByTourist() {
        service.handle(filter(OTHER_TOURIST_ID, null), manager()).block();

        assertThat(repository.lastFilter.touristId()).isEqualTo(OTHER_TOURIST_ID);
        assertThat(repository.lastFilter.status()).isEqualTo(ReviewStatus.PUBLISHED);
    }

    @Test
    void managerCannotListHiddenReviews() {
        assertThatThrownBy(() -> service.handle(filter(OTHER_TOURIST_ID, ReviewStatus.HIDDEN), manager()).block())
                .isInstanceOf(ForbiddenOperationException.class)
                .hasMessageContaining("status");
    }

    @Test
    void adminCanListAnotherTouristsReviews() {
        service.handle(filter(OTHER_TOURIST_ID, null), admin()).block();

        assertThat(repository.lastFilter.touristId()).isEqualTo(OTHER_TOURIST_ID);
        assertThat(repository.lastFilter.status()).isEqualTo(ReviewStatus.PUBLISHED);
    }

    @Test
    void adminCanFilterByHiddenStatus() {
        service.handle(filter(null, ReviewStatus.HIDDEN), admin()).block();

        assertThat(repository.lastFilter.status()).isEqualTo(ReviewStatus.HIDDEN);
    }

    private static ReviewFilter filter(UUID touristId, ReviewStatus status) {
        return new ReviewFilter(ItemType.SITE, ITEM_ID, touristId, status, 0, 20);
    }

    private static Actor tourist() {
        return new Actor(TOURIST_ID, UserRole.TOURIST, "Turista");
    }

    private static Actor admin() {
        return new Actor(UUID.randomUUID(), UserRole.ADMIN, "Admin");
    }

    private static Actor manager() {
        return new Actor(UUID.randomUUID(), UserRole.DESTINATION_MANAGER, "Gestora");
    }

    private static final class CapturingRepository implements ReviewRepositoryPort {

        private ReviewFilter lastFilter;

        @Override
        public Mono<PageResult<Review>> findAll(ReviewFilter filter) {
            this.lastFilter = filter;
            return Mono.just(new PageResult<>(List.of(), filter.page(), filter.size(), 0));
        }

        @Override
        public Mono<Review> save(Review review) {
            return Mono.empty();
        }

        @Override
        public Mono<Review> findById(UUID reviewId) {
            return Mono.empty();
        }

        @Override
        public Mono<Review> findByTouristAndItem(UUID touristId, ItemType itemType, UUID itemId) {
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
