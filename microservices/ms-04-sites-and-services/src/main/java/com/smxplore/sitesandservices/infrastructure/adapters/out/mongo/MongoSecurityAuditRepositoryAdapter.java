package com.smxplore.sitesandservices.infrastructure.adapters.out.mongo;

import com.smxplore.sitesandservices.domain.model.audit.SecurityAuditEntry;
import com.smxplore.sitesandservices.domain.ports.out.SecurityAuditRepositoryPort;
import com.smxplore.sitesandservices.infrastructure.adapters.out.mongo.document.SecurityAuditDocument;
import lombok.RequiredArgsConstructor;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class MongoSecurityAuditRepositoryAdapter implements SecurityAuditRepositoryPort {

    private final ReactiveMongoTemplate mongoTemplate;

    @Override
    public Mono<Void> append(SecurityAuditEntry entry) {
        SecurityAuditDocument document = new SecurityAuditDocument();
        document.setAuditId(entry.auditId().toString());
        document.setOccurredAt(entry.occurredAt());
        document.setAction(entry.action());
        document.setActorId(entry.actorId() == null ? null : entry.actorId().toString());
        document.setActorRole(entry.actorRole());
        document.setTargetId(entry.targetId());
        document.setDetail(entry.detail());
        return mongoTemplate.insert(document).then();
    }
}
