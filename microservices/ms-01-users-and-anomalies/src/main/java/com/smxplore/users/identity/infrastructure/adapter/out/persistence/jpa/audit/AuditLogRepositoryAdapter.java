package com.smxplore.users.identity.infrastructure.adapter.out.persistence.jpa.audit;

import com.smxplore.users.identity.application.dto.AuditSearchCriteria;
import com.smxplore.users.identity.application.dto.PageResult;
import com.smxplore.users.identity.application.port.out.AuditLogRepository;
import com.smxplore.users.identity.domain.model.AuditEntry;
import jakarta.persistence.criteria.Predicate;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.ObjectMapper;

@Repository
public class AuditLogRepositoryAdapter implements AuditLogRepository {

    private final AuditLogJpaRepository auditLogs;
    private final AuditLogPersistenceMapper mapper;

    public AuditLogRepositoryAdapter(AuditLogJpaRepository auditLogs, ObjectMapper objectMapper) {
        this.auditLogs = auditLogs;
        this.mapper = new AuditLogPersistenceMapper(objectMapper);
    }

    @Override
    public AuditEntry save(AuditEntry entry) {
        return mapper.toDomain(auditLogs.save(mapper.toEntity(entry)));
    }

    @Override
    public PageResult<AuditEntry> search(AuditSearchCriteria criteria) {
        Pageable pageable =
                PageRequest.of(criteria.page(), criteria.size(), Sort.by(Sort.Direction.DESC, "occurredAt"));
        Page<AuditLogJpaEntity> page = auditLogs.findAll(toSpecification(criteria), pageable);
        return PageResult.of(
                page.getContent().stream().map(mapper::toDomain).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements());
    }

    private static Specification<AuditLogJpaEntity> toSpecification(AuditSearchCriteria criteria) {
        return (root, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (criteria.actorId() != null) {
                predicates.add(builder.equal(
                        root.get("actorUserId"), criteria.actorId().value()));
            }
            if (criteria.action() != null) {
                predicates.add(builder.equal(root.get("action"), criteria.action()));
            }
            if (criteria.targetUserId() != null) {
                predicates.add(builder.equal(
                        root.get("targetUserId"), criteria.targetUserId().value()));
            }
            if (criteria.from() != null) {
                predicates.add(builder.greaterThanOrEqualTo(root.<Instant>get("occurredAt"), criteria.from()));
            }
            if (criteria.to() != null) {
                predicates.add(builder.lessThanOrEqualTo(root.<Instant>get("occurredAt"), criteria.to()));
            }
            return builder.and(predicates.toArray(Predicate[]::new));
        };
    }
}
