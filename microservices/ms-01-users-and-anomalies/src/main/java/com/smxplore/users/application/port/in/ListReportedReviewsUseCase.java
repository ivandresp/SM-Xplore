package com.smxplore.users.application.port.in;

import com.smxplore.users.application.dto.PageResult;
import com.smxplore.users.application.dto.ReportedReviewView;
import reactor.core.publisher.Mono;

public interface ListReportedReviewsUseCase {

    Mono<PageResult<ReportedReviewView>> list(int page, int size);
}
