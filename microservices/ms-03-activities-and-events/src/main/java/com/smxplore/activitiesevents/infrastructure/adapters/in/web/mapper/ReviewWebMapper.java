package com.smxplore.activitiesevents.infrastructure.adapters.in.web.mapper;

import com.smxplore.activitiesevents.domain.model.review.Review;
import com.smxplore.activitiesevents.infrastructure.adapters.in.web.dto.ReviewResponse;
import org.springframework.stereotype.Component;

@Component
public class ReviewWebMapper {

    public ReviewResponse toResponse(Review review) {
        return new ReviewResponse(
                review.getId(),
                review.getItemId(),
                review.getItemType(),
                review.getUser() == null ? null
                        : new ReviewResponse.ReviewUserResponse(review.getUser().id(), review.getUser().name()),
                review.getRating(),
                review.getComment(),
                review.getReply() == null ? null
                        : new ReviewResponse.ReviewReplyResponse(review.getReply().message(),
                        review.getReply().responderId(), review.getReply().createdAt()),
                review.getCreatedAt()
        );
    }
}
