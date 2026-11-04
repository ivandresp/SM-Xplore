package com.smxplore.users.application.port.in;

import com.smxplore.users.application.dto.CreateReviewCommand;
import com.smxplore.users.application.dto.ReviewView;
import java.util.UUID;
import reactor.core.publisher.Mono;

public interface CreateReviewUseCase {

    Mono<ReviewView> create(UUID touristId, CreateReviewCommand command);
}
