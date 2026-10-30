package com.smxplore.users.infrastructure.adapter.out.persistence.mongo;

import com.smxplore.users.application.dto.PageResult;
import com.smxplore.users.application.dto.ReviewStatistics;
import com.smxplore.users.application.port.out.ReviewRepository;
import com.smxplore.users.domain.exception.DuplicateReviewException;
import com.smxplore.users.domain.model.ReputationSummary;
import com.smxplore.users.domain.model.Review;
import com.smxplore.users.domain.model.ReviewModerationStatus;
import com.smxplore.users.domain.model.ReviewTargetType;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.annotation.Id;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

@Repository
public class ReviewRepositoryAdapter implements ReviewRepository {

    private static final String COLLECTION = "reviews";

    private final ReviewMongoRepository reviews;
    private final ReactiveMongoTemplate template;
    private final ReviewMongoMapper mapper = new ReviewMongoMapper();

    public ReviewRepositoryAdapter(ReviewMongoRepository reviews, ReactiveMongoTemplate template) {
        this.reviews = reviews;
        this.template = template;
    }

    @Override
    public Mono<Review> save(Review review) {
        return reviews.save(mapper.toDocument(review))
                .onErrorMap(DuplicateKeyException.class, exception -> new DuplicateReviewException())
                .map(mapper::toDomain);
    }

    @Override
    public Mono<Review> findById(String reviewId) {
        return reviews.findById(reviewId).map(mapper::toDomain);
    }

    @Override
    public Mono<Boolean> existsByTouristAndTarget(UUID touristId, ReviewTargetType targetType, UUID targetId) {
        return reviews.existsByTouristIdAndTargetTypeAndTargetId(touristId, targetType.code(), targetId);
    }

    @Override
    public Mono<PageResult<Review>> findVisible(ReviewTargetType targetType, UUID targetId, int page, int size) {
        Criteria criteria = Criteria.where("targetType")
                .is(targetType.code())
                .and("targetId")
                .is(targetId)
                .and("moderationStatus")
                .is(ReviewModerationStatus.VISIBLE.name());
        Query query = Query.query(criteria)
                .with(Sort.by(Sort.Direction.DESC, "createdAt"))
                .skip((long) page * size)
                .limit(size);

        Mono<Long> total = template.count(Query.query(criteria), ReviewDocument.class);
        Mono<java.util.List<Review>> content =
                template.find(query, ReviewDocument.class).map(mapper::toDomain).collectList();

        return Mono.zip(total, content).map(tuple -> PageResult.of(tuple.getT2(), page, size, tuple.getT1()));
    }

    @Override
    public Mono<PageResult<Review>> findReported(int page, int size) {
        Criteria criteria = Criteria.where("reported").is(true);
        Query query = Query.query(criteria)
                .with(Sort.by(Sort.Direction.DESC, "updatedAt"))
                .skip((long) page * size)
                .limit(size);

        Mono<Long> total = template.count(Query.query(criteria), ReviewDocument.class);
        Mono<java.util.List<Review>> content =
                template.find(query, ReviewDocument.class).map(mapper::toDomain).collectList();

        return Mono.zip(total, content).map(tuple -> PageResult.of(tuple.getT2(), page, size, tuple.getT1()));
    }

    @Override
    public Mono<PageResult<Review>> findAiFlagged(int page, int size) {
        Criteria criteria = Criteria.where("aiFlagged")
                .is(true)
                .and("moderationStatus")
                .in(ReviewModerationStatus.PENDIENTE_REVISION.name(), ReviewModerationStatus.HIDDEN.name());
        Query query = Query.query(criteria)
                .with(Sort.by(Sort.Direction.DESC, "createdAt"))
                .skip((long) page * size)
                .limit(size);

        Mono<Long> total = template.count(Query.query(criteria), ReviewDocument.class);
        Mono<java.util.List<Review>> content =
                template.find(query, ReviewDocument.class).map(mapper::toDomain).collectList();

        return Mono.zip(total, content).map(tuple -> PageResult.of(tuple.getT2(), page, size, tuple.getT1()));
    }

    @Override
    public Mono<ReputationSummary> summary(ReviewTargetType targetType, UUID targetId) {
        Criteria criteria = Criteria.where("targetType")
                .is(targetType.code())
                .and("targetId")
                .is(targetId)
                .and("moderationStatus")
                .is(ReviewModerationStatus.VISIBLE.name());

        Aggregation summaryAggregation = Aggregation.newAggregation(
                Aggregation.match(criteria),
                Aggregation.group().avg("rating").as("average").count().as("count"));
        Mono<SummaryRow> summary = template.aggregate(summaryAggregation, COLLECTION, SummaryRow.class)
                .next()
                .defaultIfEmpty(new SummaryRow());

        Aggregation distributionAggregation = Aggregation.newAggregation(
                Aggregation.match(criteria), Aggregation.group("rating").count().as("count"));
        Mono<Map<Integer, Long>> distribution = template.aggregate(
                        distributionAggregation, COLLECTION, RatingCountRow.class)
                .collectList()
                .map(this::toDistribution);

        return Mono.zip(summary, distribution)
                .map(tuple -> new ReputationSummary(
                        tuple.getT1().getCount(), tuple.getT1().getAverage(), tuple.getT2()));
    }

    @Override
    public Mono<ReviewStatistics> statistics() {
        Criteria visibleCriteria = Criteria.where("moderationStatus").is(ReviewModerationStatus.VISIBLE.name());
        Mono<Long> total = template.count(new Query(), ReviewDocument.class);
        Mono<Long> visible = template.count(Query.query(visibleCriteria), ReviewDocument.class);
        Mono<Long> hidden = template.count(
                Query.query(Criteria.where("moderationStatus").is(ReviewModerationStatus.HIDDEN.name())),
                ReviewDocument.class);
        Mono<Long> reported =
                template.count(Query.query(Criteria.where("reported").is(true)), ReviewDocument.class);
        Mono<Long> pendingAi = template.count(
                Query.query(Criteria.where("moderationStatus").is(ReviewModerationStatus.PENDIENTE_REVISION.name())),
                ReviewDocument.class);

        Aggregation averageAggregation = Aggregation.newAggregation(
                Aggregation.match(visibleCriteria),
                Aggregation.group().avg("rating").as("average").count().as("count"));
        Mono<SummaryRow> summary = template.aggregate(averageAggregation, COLLECTION, SummaryRow.class)
                .next()
                .defaultIfEmpty(new SummaryRow());

        return Mono.zip(total, visible, hidden, reported, pendingAi, summary)
                .map(tuple -> new ReviewStatistics(
                        tuple.getT1(),
                        tuple.getT2(),
                        tuple.getT3(),
                        tuple.getT5(),
                        tuple.getT4(),
                        round(tuple.getT6().getAverage())));
    }

    private static double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private Map<Integer, Long> toDistribution(java.util.List<RatingCountRow> rows) {
        Map<Integer, Long> counts = new LinkedHashMap<>();
        for (int rating = 1; rating <= 5; rating++) {
            counts.put(rating, 0L);
        }
        for (RatingCountRow row : rows) {
            if (row.getId() != null) {
                counts.put(row.getId(), row.getCount());
            }
        }
        return counts;
    }

    static class SummaryRow {

        private double average;
        private long count;

        public double getAverage() {
            return average;
        }

        public void setAverage(double average) {
            this.average = average;
        }

        public long getCount() {
            return count;
        }

        public void setCount(long count) {
            this.count = count;
        }
    }

    static class RatingCountRow {

        @Id
        private Integer id;

        private long count;

        public Integer getId() {
            return id;
        }

        public void setId(Integer id) {
            this.id = id;
        }

        public long getCount() {
            return count;
        }

        public void setCount(long count) {
            this.count = count;
        }
    }
}
