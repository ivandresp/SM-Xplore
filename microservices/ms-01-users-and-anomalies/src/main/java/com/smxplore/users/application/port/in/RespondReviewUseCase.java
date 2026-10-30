package com.smxplore.users.application.port.in;

import com.smxplore.users.application.dto.RespondReviewCommand;
import com.smxplore.users.application.dto.ReviewView;
import java.util.UUID;
import reactor.core.publisher.Mono;

public interface RespondReviewUseCase {

    Mono<ReviewView> respond(UUID responderId, String reviewId, RespondReviewCommand command);
}
