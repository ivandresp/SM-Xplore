package com.smxplore.users.application.port.in;

import com.smxplore.users.application.dto.ReportReviewCommand;
import java.util.UUID;
import reactor.core.publisher.Mono;

public interface ReportReviewUseCase {

    Mono<Void> report(UUID reporterId, String reviewId, ReportReviewCommand command);
}
