package com.smxplore.recommendations.infrastructure.adapters.out.neo4j;

import com.smxplore.recommendations.domain.model.recommendation.CandidateOrigin;
import com.smxplore.recommendations.domain.model.geo.GeoLocation;
import com.smxplore.recommendations.domain.model.catalog.ItemType;
import com.smxplore.recommendations.domain.model.recommendation.RecommendationCandidate;
import com.smxplore.recommendations.domain.model.ranking.RecommendationPolicy;
import com.smxplore.recommendations.domain.ports.out.RecommendationGraphPort;
import org.neo4j.driver.Driver;
import org.neo4j.driver.reactivestreams.ReactiveSession;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Component
public class Neo4jRecommendationAdapter implements RecommendationGraphPort {

    private final Driver driver;
    private final RecommendationPolicy policy;
    private final String affinityCandidatesQuery;

    public Neo4jRecommendationAdapter(Driver driver, RecommendationPolicy policy) {
        this.driver = driver;
        this.policy = policy;
        this.affinityCandidatesQuery = Neo4jQueries.affinityCandidates(policy.categoryMaxDepth());
    }

    @Override
    public Flux<RecommendationCandidate> findCandidatesForTourist(
            UUID touristId,
            UUID siteId,
            GeoLocation location,
            int limit,
            Instant now
    ) {
        return queryCandidates(affinityCandidatesQuery, commonParams(touristId, siteId, limit, now),
                CandidateOrigin.AFFINITY);
    }

    @Override
    public Flux<RecommendationCandidate> findFallbackCandidates(
            UUID touristId,
            UUID siteId,
            GeoLocation location,
            int limit,
            Instant now
    ) {
        return queryCandidates(Neo4jQueries.FALLBACK_CANDIDATES, commonParams(touristId, siteId, limit, now),
                CandidateOrigin.FALLBACK);
    }

    @Override
    public Mono<Boolean> upsertPreference(UUID touristId, UUID categoryId, String language, Instant now) {
        Map<String, Object> params = new HashMap<>();
        params.put("touristId", touristId.toString());
        params.put("categoryId", categoryId.toString());
        params.put("language", language);
        params.put("now", now.toString());
        return writeReturningBoolean(Neo4jQueries.UPSERT_PREFERENCE, params);
    }

    @Override
    public Mono<Void> deletePreference(UUID touristId, UUID categoryId) {
        Map<String, Object> params = new HashMap<>();
        params.put("touristId", touristId.toString());
        params.put("categoryId", categoryId.toString());
        return writeVoid(Neo4jQueries.DELETE_PREFERENCE, params);
    }

    @Override
    public Mono<Boolean> recordView(UUID touristId, ItemType itemType, UUID itemId, String language, Instant now) {
        Map<String, Object> params = new HashMap<>();
        params.put("touristId", touristId.toString());
        params.put("itemType", itemType.name());
        params.put("itemId", itemId.toString());
        params.put("language", language);
        params.put("now", now.toString());
        return writeReturningBoolean(Neo4jQueries.RECORD_VIEW, params);
    }

    private Flux<RecommendationCandidate> queryCandidates(
            String query,
            Map<String, Object> params,
            CandidateOrigin origin
    ) {
        return Flux.usingWhen(
                Mono.fromSupplier(() -> driver.session(ReactiveSession.class)),
                session -> Flux.from(session.executeRead(tx ->
                        Flux.from(tx.run(query, params))
                                .flatMap(result -> Flux.from(result.records()))
                                .map(record -> Neo4jRecordMapper.mapCandidate(record, origin))
                )),
                session -> Flux.from(session.close())
        );
    }

    private Mono<Boolean> writeReturningBoolean(String query, Map<String, Object> params) {
        return Mono.usingWhen(
                Mono.fromSupplier(() -> driver.session(ReactiveSession.class)),
                session -> Flux.from(session.executeWrite(tx ->
                                Flux.from(tx.run(query, params))
                                        .flatMap(result -> Flux.from(result.records()).collectList())
                                        .map(records -> !records.isEmpty())
                        ))
                        .collectList()
                        .map(values -> !values.isEmpty() && values.getFirst()),
                session -> Flux.from(session.close())
        );
    }

    private Mono<Void> writeVoid(String query, Map<String, Object> params) {
        return Mono.usingWhen(
                Mono.fromSupplier(() -> driver.session(ReactiveSession.class)),
                session -> Flux.from(session.executeWrite(tx ->
                                Flux.from(tx.run(query, params))
                                        .flatMap(result -> Flux.from(result.consume()))
                                        .then()
                        ))
                        .then(),
                session -> Flux.from(session.close())
        );
    }

    private Map<String, Object> commonParams(UUID touristId, UUID siteId, int limit, Instant now) {
        Map<String, Object> params = new HashMap<>();
        params.put("touristId", touristId.toString());
        params.put("siteId", siteId == null ? null : siteId.toString());
        params.put("candidateLimit", limit);
        params.put("now", now.toString());
        params.put("preferenceHalfLifeDays", policy.preferenceHalfLifeDays());
        return params;
    }
}
