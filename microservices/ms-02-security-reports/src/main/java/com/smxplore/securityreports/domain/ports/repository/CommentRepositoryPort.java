package com.smxplore.securityreports.domain.ports.repository;

import com.smxplore.securityreports.domain.model.Comment;
import com.smxplore.securityreports.domain.model.types.Page;
import com.smxplore.securityreports.domain.model.types.PageRequest;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface CommentRepositoryPort {
    Mono<Comment> save(Comment comment);

    Mono<Page<Comment>> findAllByReportId(UUID reportId, PageRequest pageRequest);
}
