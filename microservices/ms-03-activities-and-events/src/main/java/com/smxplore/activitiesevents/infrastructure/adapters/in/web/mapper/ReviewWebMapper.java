package com.smxplore.activitiesevents.infrastructure.adapters.in.web.mapper;

import com.smxplore.activitiesevents.domain.model.review.Review;
import com.smxplore.activitiesevents.infrastructure.adapters.in.web.dto.AdminReviewResponse;
import com.smxplore.activitiesevents.infrastructure.adapters.in.web.dto.ReviewAiAssessmentResponse;
import com.smxplore.activitiesevents.infrastructure.adapters.in.web.dto.ReviewResponse;
import org.springframework.stereotype.Component;

@Component
public class ReviewWebMapper {

    /** Public catalog view: no AI assessment. */
    public ReviewResponse toResponse(Review review) {
        return new ReviewResponse(
                review.getId(),
                review.getItemId(),
                review.getItemType(),
                user(review),
                review.getRating(),
                review.getComment(),
                reply(review),
                review.getStatus(),
                review.getCreatedAt(),
                review.updatedAt()
        );
    }

    /** Administrative view: includes the AI assessment and the retention flag. */
    public AdminReviewResponse toAdminResponse(Review review) {
        return new AdminReviewResponse(
                review.getId(),
                review.getItemId(),
                review.getItemType(),
                user(review),
                review.getRating(),
                review.getComment(),
                reply(review),
                review.getStatus(),
                ReviewAiAssessmentResponse.from(review.getAiAssessment()),
                review.isAiRetained(),
                review.getCreatedAt(),
                review.updatedAt()
        );
    }

    private static ReviewResponse.ReviewUserResponse user(Review review) {
        return review.getUser() == null ? null
                : new ReviewResponse.ReviewUserResponse(review.getUser().name());
    }

    private static ReviewResponse.ReviewReplyResponse reply(Review review) {
        return review.getReply() == null ? null
                : new ReviewResponse.ReviewReplyResponse(review.getReply().message(),
                review.getReply().createdAt());
    }
}
