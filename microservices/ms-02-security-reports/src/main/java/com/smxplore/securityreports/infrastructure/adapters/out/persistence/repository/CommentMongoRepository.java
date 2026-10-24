package com.smxplore.securityreports.infrastructure.adapters.out.persistence.repository;

import com.smxplore.securityreports.infrastructure.adapters.out.persistence.document.CommentDocument;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import reactor.core.publisher.Flux;

public interface CommentMongoRepository extends ReactiveMongoRepository<CommentDocument, String> {

    Flux<CommentDocument> findAllByReportIdOrderByCreatedAtAsc(String reportId);
}
