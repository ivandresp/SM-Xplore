package com.smxplore.activitiesevents.infrastructure.adapters.out.persistence.mongo.document;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.UUID;

@Document(collection = "activity_reviews")
@CompoundIndex(name = "activity_review_item_user", def = "{'itemId': 1, 'user.id': 1}", unique = true)
@CompoundIndex(name = "activity_review_item_created", def = "{'itemId': 1, 'createdAt': -1}")
public record ActivityReviewDocument(
        @Id UUID id,
        UUID itemId,
        ReviewUserDocument user,
        int rating,
        String comment,
        ReviewReplyDocument reply,
        Instant createdAt
) {
}
