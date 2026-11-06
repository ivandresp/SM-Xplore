package com.smxplore.securityreports.domain.ports.usecases;

import com.smxplore.securityreports.domain.model.Comment;
import reactor.core.publisher.Mono;

public interface CommentReportUseCasePort {
    Mono<Comment> handle(Comment comment);
}
