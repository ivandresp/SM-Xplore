package com.smxplore.users.application.port.in;

import com.smxplore.users.application.dto.PageResult;
import com.smxplore.users.application.dto.ReviewView;
import com.smxplore.users.domain.model.ReviewTargetType;
import java.util.UUID;
import reactor.core.publisher.Mono;

public interface ListReviewsUseCase {

    Mono<PageResult<ReviewView>> list(ReviewTargetType targetType, UUID targetId, int page, int size);
}
