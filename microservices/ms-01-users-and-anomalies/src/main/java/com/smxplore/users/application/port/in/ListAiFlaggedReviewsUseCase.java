package com.smxplore.users.application.port.in;

import com.smxplore.users.application.dto.AiFlaggedReviewView;
import com.smxplore.users.application.dto.PageResult;
import reactor.core.publisher.Mono;

public interface ListAiFlaggedReviewsUseCase {

    Mono<PageResult<AiFlaggedReviewView>> list(int page, int size);
}
