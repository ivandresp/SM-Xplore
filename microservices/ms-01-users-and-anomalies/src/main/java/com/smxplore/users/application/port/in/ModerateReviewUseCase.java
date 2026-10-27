package com.smxplore.users.application.port.in;

import com.smxplore.users.application.dto.ModerateReviewCommand;
import com.smxplore.users.application.dto.ReviewView;
import java.util.UUID;
import reactor.core.publisher.Mono;

public interface ModerateReviewUseCase {

    Mono<ReviewView> moderate(UUID adminId, String reviewId, ModerateReviewCommand command);
}
