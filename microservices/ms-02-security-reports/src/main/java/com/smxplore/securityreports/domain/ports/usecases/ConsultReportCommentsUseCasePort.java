package com.smxplore.securityreports.domain.ports.usecases;

import com.smxplore.securityreports.domain.model.Comment;
import reactor.core.publisher.Flux;

import java.util.UUID;

public interface ConsultReportCommentsUseCasePort {
    Flux<Comment> handle(UUID reportId);
}
