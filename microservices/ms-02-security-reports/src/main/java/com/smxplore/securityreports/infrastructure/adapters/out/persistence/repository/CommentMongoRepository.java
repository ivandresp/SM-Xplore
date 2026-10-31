package com.smxplore.securityreports.infrastructure.adapters.out.persistence.repository;

import com.smxplore.securityreports.infrastructure.adapters.out.persistence.document.CommentDocument;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;

public interface CommentMongoRepository extends ReactiveMongoRepository<CommentDocument, String> {
}
