package com.smxplore.securityreports.domain.ports.repository;

import com.smxplore.securityreports.domain.model.Comment;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface CommentRepositoryPort {
    Mono<Comment> save(Comment comment);
    Flux<Comment> findAllByReportId(UUID reportId);
}
