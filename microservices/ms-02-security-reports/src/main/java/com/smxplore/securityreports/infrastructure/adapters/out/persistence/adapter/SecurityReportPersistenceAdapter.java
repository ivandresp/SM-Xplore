package com.smxplore.securityreports.infrastructure.adapters.out.persistence.adapter;

import com.smxplore.securityreports.domain.model.SecurityReport;
import com.smxplore.securityreports.domain.model.SecurityReportStatus;
import com.smxplore.securityreports.domain.model.types.Cursor;
import com.smxplore.securityreports.domain.model.types.Page;
import com.smxplore.securityreports.domain.model.types.PageRequest;
import com.smxplore.securityreports.domain.ports.repository.SecurityReportRepositoryPort;
import com.smxplore.securityreports.infrastructure.adapters.out.persistence.document.SecurityReportDocument;
import com.smxplore.securityreports.infrastructure.adapters.out.persistence.mapper.SecurityReportPersistenceMapper;
import com.smxplore.securityreports.infrastructure.adapters.out.persistence.repository.SecurityReportMongoRepository;
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
public class SecurityReportPersistenceAdapter implements SecurityReportRepositoryPort {

    private static final Sort REPORT_SORT = Sort.by(Sort.Direction.DESC, "createdAt", "_id");

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
    public Mono<Page<SecurityReport>> findAllByStatus(SecurityReportStatus status, PageRequest pageRequest) {
        var query = Query.query(Criteria.where("status").is(status));
        return findPage(query, pageRequest);
    }

    @Override
    public Mono<Page<SecurityReport>> findAllByUserId(UUID userId, PageRequest pageRequest) {
        var query = Query.query(Criteria.where("user.userId").is(userId.toString()));
        return findPage(query, pageRequest);
    }

    private Mono<Page<SecurityReport>> findPage(Query query, PageRequest pageRequest) {
        CursorPaginationSupport.applyCursor(query, pageRequest.cursor(), Sort.Direction.DESC);
        query.with(REPORT_SORT).limit(pageRequest.size() + 1);

        return mongoOperations.find(query, SecurityReportDocument.class)
                .map(mapper::toDomain)
                .collectList()
                .map(reports -> CursorPaginationSupport.toPage(
                        reports,
                        pageRequest.size(),
                        report -> new Cursor(report.getCreatedAt(), report.getId())
                ));
    }
}
