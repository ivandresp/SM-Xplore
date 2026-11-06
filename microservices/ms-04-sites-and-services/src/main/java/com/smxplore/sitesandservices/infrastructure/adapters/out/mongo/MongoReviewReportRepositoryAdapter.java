package com.smxplore.sitesandservices.infrastructure.adapters.out.mongo;

import com.smxplore.sitesandservices.domain.model.common.PageResult;
import com.smxplore.sitesandservices.domain.model.review.ReviewReport;
import com.smxplore.sitesandservices.domain.model.review.ReviewReportFilter;
import com.smxplore.sitesandservices.domain.ports.out.ReviewReportRepositoryPort;
import com.smxplore.sitesandservices.infrastructure.adapters.out.mongo.document.ReviewReportDocument;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class MongoReviewReportRepositoryAdapter implements ReviewReportRepositoryPort {

    private static final String COLLECTION = "review_reports";

    private final ReactiveMongoTemplate mongoTemplate;
    private final MongoCatalogMapper mapper;

    @Override
    public Mono<ReviewReport> save(ReviewReport report) {
        return mongoTemplate.save(mapper.toDocument(report), COLLECTION)
                .map(mapper::toDomain);
    }

    @Override
    public Mono<ReviewReport> findById(UUID reportId) {
        return mongoTemplate.findById(reportId.toString(), ReviewReportDocument.class, COLLECTION)
                .map(mapper::toDomain);
    }

    @Override
    public Mono<ReviewReport> findByReviewAndReporter(UUID reviewId, UUID reportedBy) {
        Query query = Query.query(Criteria.where("reviewId").is(reviewId.toString())
                .and("reportedBy").is(reportedBy.toString()));
        return mongoTemplate.findOne(query, ReviewReportDocument.class, COLLECTION)
                .map(mapper::toDomain);
    }

    @Override
    public Mono<PageResult<ReviewReport>> findAll(ReviewReportFilter filter) {
        Query query = query(filter);
        Query pageQuery = Query.of(query)
                .with(PageRequest.of(filter.page(), filter.size(),
                        Sort.by(Sort.Direction.DESC, "createdAt")));

        Mono<Long> total = mongoTemplate.count(query, ReviewReportDocument.class, COLLECTION);
        Mono<List<ReviewReport>> content = mongoTemplate.find(pageQuery, ReviewReportDocument.class, COLLECTION)
                .map(mapper::toDomain)
                .collectList();

        return Mono.zip(total, content)
                .map(tuple -> new PageResult<>(tuple.getT2(), filter.page(), filter.size(), tuple.getT1()));
    }

    @Override
    public Mono<Long> countOpenByReview(UUID reviewId) {
        Query query = Query.query(Criteria.where("reviewId").is(reviewId.toString())
                .and("status").is("OPEN"));
        return mongoTemplate.count(query, ReviewReportDocument.class, COLLECTION);
    }

    private Query query(ReviewReportFilter filter) {
        Criteria criteria = new Criteria();
        if (filter.status() != null) {
            criteria.and("status").is(filter.status().name());
        }
        if (filter.reviewId() != null) {
            criteria.and("reviewId").is(filter.reviewId().toString());
        }
        return Query.query(criteria);
    }
}
