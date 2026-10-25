package com.smxplore.activitiesevents.infrastructure.adapters.out.persistence.mongo.document;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.UUID;

@Document(collection = "event_reviews")
@CompoundIndex(name = "event_review_item_user", def = "{'itemId': 1, 'user.id': 1}", unique = true)
@CompoundIndex(name = "event_review_item_created", def = "{'itemId': 1, 'createdAt': -1}")
public record EventReviewDocument(
        @Id UUID id,
        UUID itemId,
        ReviewUserDocument user,
        int rating,
        String comment,
        ReviewReplyDocument reply,
        boolean reported,
        String reportReason,
        Instant reportedAt,
        Instant createdAt
) {
}
