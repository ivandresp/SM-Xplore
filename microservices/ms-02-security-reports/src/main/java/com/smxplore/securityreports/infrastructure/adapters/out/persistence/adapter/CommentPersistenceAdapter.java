package com.smxplore.securityreports.infrastructure.adapters.out.persistence.adapter;

import com.smxplore.securityreports.domain.model.Comment;
import com.smxplore.securityreports.domain.model.types.Cursor;
import com.smxplore.securityreports.domain.model.types.Page;
import com.smxplore.securityreports.domain.model.types.PageRequest;
import com.smxplore.securityreports.domain.ports.repository.CommentRepositoryPort;
import com.smxplore.securityreports.infrastructure.adapters.out.persistence.document.CommentDocument;
import com.smxplore.securityreports.infrastructure.adapters.out.persistence.mapper.CommentPersistenceMapper;
import com.smxplore.securityreports.infrastructure.adapters.out.persistence.repository.CommentMongoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.ReactiveMongoOperations;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class CommentPersistenceAdapter implements CommentRepositoryPort {

    private static final Sort COMMENT_SORT = Sort.by(Sort.Direction.ASC, "createdAt", "_id");

    private final CommentMongoRepository repository;
    private final CommentPersistenceMapper mapper;
    private final ReactiveMongoOperations mongoOperations;

    @Override
    public Mono<Comment> save(Comment comment) {
        return repository.save(mapper.toDocument(comment))
                .map(mapper::toDomain);
    }

    @Override
    public Mono<Page<Comment>> findAllByReportId(UUID reportId, PageRequest pageRequest) {
        var query = Query.query(Criteria.where("reportId").is(reportId.toString()));
        CursorPaginationSupport.applyCursor(query, pageRequest.cursor(), Sort.Direction.ASC);
        query.with(COMMENT_SORT).limit(pageRequest.size() + 1);

        return mongoOperations.find(query, CommentDocument.class)
                .map(mapper::toDomain)
                .collectList()
                .map(comments -> CursorPaginationSupport.toPage(
                        comments,
                        pageRequest.size(),
                        comment -> new Cursor(comment.getCreatedAt(), comment.getId())
                ));
    }
}
