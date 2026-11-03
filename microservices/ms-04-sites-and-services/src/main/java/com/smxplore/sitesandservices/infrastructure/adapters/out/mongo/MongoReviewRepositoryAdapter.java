package com.smxplore.sitesandservices.infrastructure.adapters.out.mongo;

import com.smxplore.sitesandservices.domain.model.common.ItemType;
import com.smxplore.sitesandservices.domain.model.common.PageResult;
import com.smxplore.sitesandservices.domain.model.review.ReputationMetrics;
import com.smxplore.sitesandservices.domain.model.review.Review;
import com.smxplore.sitesandservices.domain.model.review.ReviewFilter;
import com.smxplore.sitesandservices.domain.model.review.ReviewStatus;
import com.smxplore.sitesandservices.domain.model.review.ReviewSummary;
import com.smxplore.sitesandservices.domain.ports.out.ReviewRepositoryPort;
import com.smxplore.sitesandservices.infrastructure.adapters.out.mongo.document.ReputationAggregation;
import com.smxplore.sitesandservices.infrastructure.adapters.out.mongo.document.ReviewDocument;
import com.smxplore.sitesandservices.infrastructure.adapters.out.mongo.document.ReviewSummaryAggregation;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.ConditionalOperators;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class MongoReviewRepositoryAdapter implements ReviewRepositoryPort {

    private static final String COLLECTION = "reviews";

    private final ReactiveMongoTemplate mongoTemplate;
    private final MongoCatalogMapper mapper;

    @Override
    public Mono<Review> save(Review review) {
        return mongoTemplate.save(mapper.toDocument(review))
                .map(mapper::toDomain);
    }

    @Override
    public Mono<Review> findById(UUID reviewId) {
        return mongoTemplate.findById(reviewId.toString(), ReviewDocument.class)
                .map(mapper::toDomain);
    }

    @Override
    public Mono<Review> findByTouristAndItem(UUID touristId, ItemType itemType, UUID itemId) {
        Query query = Query.query(Criteria.where("touristId").is(touristId.toString())
                .and("itemType").is(itemType.name())
                .and("itemId").is(itemId.toString()));
        return mongoTemplate.findOne(query, ReviewDocument.class)
                .map(mapper::toDomain);
    }

    @Override
    public Mono<PageResult<Review>> findAll(ReviewFilter filter) {
        Query query = query(filter);
        Query pageQuery = Query.of(query)
                .with(PageRequest.of(filter.page(), filter.size(),
                        Sort.by(Sort.Direction.DESC, "createdAt")));

        Mono<Long> total = mongoTemplate.count(query, ReviewDocument.class);
        Mono<List<Review>> content = mongoTemplate.find(pageQuery, ReviewDocument.class)
                .map(mapper::toDomain)
                .collectList();

        return Mono.zip(total, content)
                .map(tuple -> new PageResult<>(tuple.getT2(), filter.page(), filter.size(), tuple.getT1()));
    }

    @Override
    public Mono<ReviewSummary> summarize(ItemType itemType, UUID itemId) {
        Aggregation aggregation = Aggregation.newAggregation(
                Aggregation.match(Criteria.where("itemType").is(itemType.name())
                        .and("itemId").is(itemId.toString())
                        .and("status").is(ReviewStatus.PUBLISHED.name())),
                Aggregation.group()
                        .avg("rating").as("averageRating")
                        .count().as("reviewCount")
        );

        return mongoTemplate.aggregate(aggregation, COLLECTION, ReviewSummaryAggregation.class)
                .next()
                .map(result -> new ReviewSummary(
                        itemType,
                        itemId,
                        result.getAverageRating(),
                        result.getReviewCount()
                ))
                .defaultIfEmpty(new ReviewSummary(itemType, itemId, 0.0, 0));
    }

    @Override
    public Mono<ReputationMetrics> reputation(ItemType itemType, UUID itemId) {
        Aggregation aggregation = Aggregation.newAggregation(
                Aggregation.match(criteriaFor(itemType, itemId)
                        .and("status").is(ReviewStatus.PUBLISHED.name())),
                Aggregation.group()
                        .avg("rating").as("averageRating")
                        .count().as("reviewCount")
                        .sum(oneWhen(Criteria.where("rating").is(1))).as("rating1")
                        .sum(oneWhen(Criteria.where("rating").is(2))).as("rating2")
                        .sum(oneWhen(Criteria.where("rating").is(3))).as("rating3")
                        .sum(oneWhen(Criteria.where("rating").is(4))).as("rating4")
                        .sum(oneWhen(Criteria.where("rating").is(5))).as("rating5")
                        .max("createdAt").as("lastReviewAt")
        );

        Mono<ReputationAggregation> published = mongoTemplate
                .aggregate(aggregation, COLLECTION, ReputationAggregation.class)
                .next()
                .defaultIfEmpty(new ReputationAggregation());
        Mono<Long> hidden = mongoTemplate.count(
                Query.query(criteriaFor(itemType, itemId)
                        .and("status").is(ReviewStatus.HIDDEN.name())),
                ReviewDocument.class);
        Mono<Long> answered = mongoTemplate.count(
                Query.query(criteriaFor(itemType, itemId)
                        .and("status").is(ReviewStatus.PUBLISHED.name())
                        .and("respondedAt").ne(null)),
                ReviewDocument.class);

        return Mono.zip(published, hidden, answered)
                .map(tuple -> metrics(
                        itemType,
                        itemId,
                        tuple.getT1(),
                        tuple.getT2(),
                        tuple.getT3()));
    }

    private static Criteria criteriaFor(ItemType itemType, UUID itemId) {
        return Criteria.where("itemType").is(itemType.name())
                .and("itemId").is(itemId.toString());
    }

    private static ConditionalOperators.Cond oneWhen(Criteria criteria) {
        return ConditionalOperators.when(criteria).then(1).otherwise(0);
    }

    private static ReputationMetrics metrics(
            ItemType itemType,
            UUID itemId,
            ReputationAggregation aggregation,
            long hiddenCount,
            long responseCount
    ) {
        Map<Integer, Long> distribution = new LinkedHashMap<>();
        distribution.put(1, aggregation.getRating1());
        distribution.put(2, aggregation.getRating2());
        distribution.put(3, aggregation.getRating3());
        distribution.put(4, aggregation.getRating4());
        distribution.put(5, aggregation.getRating5());
        return new ReputationMetrics(
                itemType,
                itemId,
                aggregation.getAverageRating(),
                aggregation.getReviewCount(),
                responseCount,
                hiddenCount,
                distribution,
                aggregation.getLastReviewAt()
        );
    }

    private Query query(ReviewFilter filter) {
        Criteria criteria = new Criteria();
        if (filter.itemType() != null) {
            criteria.and("itemType").is(filter.itemType().name());
        }
        if (filter.itemId() != null) {
            criteria.and("itemId").is(filter.itemId().toString());
        }
        if (filter.touristId() != null) {
            criteria.and("touristId").is(filter.touristId().toString());
        }
        if (filter.status() != null) {
            criteria.and("status").is(filter.status().name());
        }
        return Query.query(criteria);
    }
}
