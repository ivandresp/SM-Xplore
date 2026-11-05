package com.smxplore.activitiesevents.infrastructure.adapters.out.persistence.mongo.document;

import com.smxplore.activitiesevents.domain.model.review.ReviewStatus;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.UUID;

@Document(collection = "event_reviews")
@CompoundIndex(name = "event_review_item_user", def = "{'itemId': 1, 'user.id': 1}", unique = true)
@CompoundIndex(name = "event_review_item_created", def = "{'itemId': 1, 'createdAt': -1}")
// La cola de moderacion ordena por updatedAt filtrando por estado: sin este indice Mongo hace
// collection scan + sort en memoria y el endpoint falla al superar el limite de 32 MB.
@CompoundIndex(name = "event_review_status_updated", def = "{'status': 1, 'updatedAt': -1}")
public record EventReviewDocument(
        @Id UUID id,
        UUID itemId,
        ReviewUserDocument user,
        int rating,
        String comment,
        ReviewReplyDocument reply,
        Instant createdAt,
        ReviewStatus status,
        Instant updatedAt,
        ReviewAiAssessmentDocument aiAssessment,
        boolean aiRetained
) {
}
