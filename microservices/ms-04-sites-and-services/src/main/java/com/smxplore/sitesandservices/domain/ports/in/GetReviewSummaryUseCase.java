package com.smxplore.sitesandservices.domain.ports.in;

import com.smxplore.sitesandservices.domain.model.common.ItemType;
import com.smxplore.sitesandservices.domain.model.review.ReviewSummary;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface GetReviewSummaryUseCase {

    Mono<ReviewSummary> handle(ItemType itemType, UUID itemId);
}
