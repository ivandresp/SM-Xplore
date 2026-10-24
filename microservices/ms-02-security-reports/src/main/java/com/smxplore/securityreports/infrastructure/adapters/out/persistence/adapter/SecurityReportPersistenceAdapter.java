package com.smxplore.securityreports.infrastructure.adapters.out.persistence.adapter;

import com.smxplore.securityreports.domain.model.SecurityReport;
import com.smxplore.securityreports.domain.model.SecurityReportStatus;
import com.smxplore.securityreports.domain.ports.repository.SecurityReportRepositoryPort;
import com.smxplore.securityreports.infrastructure.adapters.out.persistence.document.SecurityReportDocument;
import com.smxplore.securityreports.infrastructure.adapters.out.persistence.mapper.SecurityReportPersistenceMapper;
import com.smxplore.securityreports.infrastructure.adapters.out.persistence.repository.SecurityReportMongoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.mongodb.core.ReactiveMongoOperations;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class SecurityReportPersistenceAdapter implements SecurityReportRepositoryPort {

    private final SecurityReportMongoRepository repository;
    private final SecurityReportPersistenceMapper mapper;
    private final ReactiveMongoOperations mongoOperations;

    @Override
    public Mono<SecurityReport> save(SecurityReport securityReport) {
        return repository.save(mapper.toDocument(securityReport))
                .map(mapper::toDomain);
    }

    @Override
    public Mono<SecurityReport> findById(UUID id) {
        return repository.findById(id.toString())
                .map(mapper::toDomain);
    }

    @Override
    public Flux<SecurityReport> findAllByStatus(SecurityReportStatus status) {
        return repository.findAllByStatusOrderByCreatedAtDesc(status)
                .map(mapper::toDomain);
    }

    @Override
    public Flux<SecurityReport> findAll() {
        return repository.findAllByOrderByCreatedAtDesc()
                .map(mapper::toDomain);
    }

    @Override
    public Mono<Void> changeContent(UUID reportId, String title, String description) {
        var query = Query.query(Criteria.where("_id").is(reportId.toString()));
        var update = new Update()
                .set("title", title)
                .set("description", description);
        return mongoOperations.updateFirst(query, update, SecurityReportDocument.class).then();
    }

    @Override
    public Mono<Void> changeStatus(UUID reportId, SecurityReportStatus status) {
        var query = Query.query(Criteria.where("_id").is(reportId.toString()));
        var update = new Update().set("status", status);
        return mongoOperations.updateFirst(query, update, SecurityReportDocument.class).then();
    }

    @Override
    public Flux<SecurityReport> findAllByUserId(UUID userId) {
        return repository.findAllByUser_UserIdOrderByCreatedAtDesc(userId.toString())
                .map(mapper::toDomain);
    }
}
