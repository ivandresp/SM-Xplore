package com.smxplore.securityreports.infrastructure.adapters.out.persistence.adapter;

import com.smxplore.securityreports.domain.model.Comment;
import com.smxplore.securityreports.domain.ports.repository.CommentRepositoryPort;
import com.smxplore.securityreports.infrastructure.adapters.out.persistence.mapper.CommentPersistenceMapper;
import com.smxplore.securityreports.infrastructure.adapters.out.persistence.repository.CommentMongoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class CommentPersistenceAdapter implements CommentRepositoryPort {

    private final CommentMongoRepository repository;
    private final CommentPersistenceMapper mapper;

    @Override
    public Mono<Comment> save(Comment comment) {
        return repository.save(mapper.toDocument(comment))
                .map(mapper::toDomain);
    }

    @Override
    public Flux<Comment> findAllByReportId(UUID reportId) {
        return repository.findAllByReportIdOrderByCreatedAtAsc(reportId.toString())
                .map(mapper::toDomain);
    }
}
