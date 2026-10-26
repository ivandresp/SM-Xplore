package com.smxplore.recommendations.application.recommendation;

import com.smxplore.recommendations.domain.exception.ForbiddenOperationException;
import com.smxplore.recommendations.domain.exception.InvalidLimitException;
import com.smxplore.recommendations.domain.exception.InvalidRecommendationQueryException;
import com.smxplore.recommendations.domain.model.geo.GeoLocation;
import com.smxplore.recommendations.domain.model.ranking.RecommendationPolicy;
import com.smxplore.recommendations.domain.model.ranking.ScoredCandidate;
import com.smxplore.recommendations.domain.model.ranking.ScoringContext;
import com.smxplore.recommendations.domain.model.recommendation.CandidateOrigin;
import com.smxplore.recommendations.domain.model.recommendation.Recommendation;
import com.smxplore.recommendations.domain.model.recommendation.RecommendationCandidate;
import com.smxplore.recommendations.domain.model.recommendation.RecommendationItem;
import com.smxplore.recommendations.domain.model.recommendation.RecommendationQuery;
import com.smxplore.recommendations.domain.model.recommendation.RecommendationRequest;
import com.smxplore.recommendations.domain.model.recommendation.RecommendationSource;
import com.smxplore.recommendations.domain.ports.in.GetRecommendationsUseCase;
import com.smxplore.recommendations.domain.ports.out.RecommendationGraphPort;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@RequiredArgsConstructor
public class RecommendationService implements GetRecommendationsUseCase {

    private final RecommendationGraphPort graphPort;
    private final RecommendationScorer scorer;
    private final DiversitySelector diversitySelector;
    private final RecommendationPolicy policy;
    private final Clock clock;

    @Override
    public Mono<Recommendation> handle(RecommendationRequest request) {
        RecommendationQuery query = buildQuery(request);
        Instant now = clock.instant();
        return loadCandidates(query, now)
                .map(candidates -> buildRecommendation(query, candidates, now));
    }

    private RecommendationQuery buildQuery(RecommendationRequest request) {
        validateRequest(request);
        RecommendationQuery query = new RecommendationQuery(
                resolveTouristId(request),
                request.siteId(),
                buildLocation(request),
                request.limit()
        );
        validatePolicyLimit(query);
        return query;
    }

    private UUID resolveTouristId(RecommendationRequest request) {
        if (request.requestedTouristId() == null) {
            return request.requesterId();
        }
        if (!request.admin()) {
            throw new ForbiddenOperationException("Only ADMIN can query recommendations for another tourist.");
        }
        return request.requestedTouristId();
    }

    private GeoLocation buildLocation(RecommendationRequest request) {
        if (!request.hasLocation()) {
            return null;
        }
        return new GeoLocation(request.latitude(), request.longitude());
    }

    private Mono<List<RecommendationCandidate>> loadCandidates(RecommendationQuery query, Instant now) {
        return graphPort
                .findCandidatesForTourist(query.touristId(), query.siteId(), query.location(),
                        policy.candidateLimit(), now)
                .collectList()
                .flatMap(affinity -> addFallbackIfNeeded(query, affinity, now));
    }

    private Mono<List<RecommendationCandidate>> addFallbackIfNeeded(
            RecommendationQuery query,
            List<RecommendationCandidate> affinity,
            Instant now
    ) {
        if (affinity.size() >= query.limit()) {
            return Mono.just(List.copyOf(affinity));
        }
        return graphPort
                .findFallbackCandidates(query.touristId(), query.siteId(), query.location(),
                        policy.candidateLimit(), now)
                .collectList()
                .map(fallback -> mergeCandidates(affinity, fallback));
    }

    private List<RecommendationCandidate> mergeCandidates(
            List<RecommendationCandidate> affinity,
            List<RecommendationCandidate> fallback
    ) {
        Map<UUID, RecommendationCandidate> unique = new LinkedHashMap<>();
        affinity.forEach(candidate -> unique.putIfAbsent(candidate.itemId(), candidate));
        fallback.forEach(candidate -> unique.putIfAbsent(candidate.itemId(), candidate));
        return List.copyOf(unique.values());
    }

    private Recommendation buildRecommendation(
            RecommendationQuery query,
            List<RecommendationCandidate> candidates,
            Instant now
    ) {
        ScoringContext context = buildScoringContext(query, candidates);
        List<ScoredCandidate> scored = scoreCandidates(candidates, context, now);
        List<ScoredCandidate> selected = diversitySelector.select(scored, query.limit());

        return new Recommendation(
                query.touristId(),
                now,
                determineSource(query, selected),
                toItems(selected)
        );
    }

    private ScoringContext buildScoringContext(
            RecommendationQuery query,
            List<RecommendationCandidate> candidates
    ) {
        boolean hasAffinity = candidates.stream()
                .anyMatch(candidate -> candidate.origin() == CandidateOrigin.AFFINITY);
        double maxAffinity = candidates.stream()
                .filter(candidate -> candidate.origin() == CandidateOrigin.AFFINITY)
                .mapToDouble(RecommendationCandidate::affinity)
                .max()
                .orElse(0.0);
        return new ScoringContext(query.location(), maxAffinity, hasAffinity);
    }

    private List<ScoredCandidate> scoreCandidates(
            List<RecommendationCandidate> candidates,
            ScoringContext context,
            Instant now
    ) {
        return candidates.stream()
                .map(candidate -> scorer.score(candidate, context, now))
                .toList();
    }

    private RecommendationSource determineSource(RecommendationQuery query, List<ScoredCandidate> selected) {
        boolean fromAffinity = selected.stream()
                .anyMatch(scored -> scored.candidate().origin() == CandidateOrigin.AFFINITY);
        boolean fromFallback = selected.stream()
                .anyMatch(scored -> scored.candidate().origin() == CandidateOrigin.FALLBACK);
        return resolveSource(query.location(), fromAffinity, fromFallback);
    }

    private RecommendationSource resolveSource(GeoLocation location, boolean fromAffinity, boolean fromFallback) {
        if (fromAffinity && fromFallback) {
            return RecommendationSource.MIXED;
        }
        if (fromAffinity) {
            return RecommendationSource.AFFINITY;
        }
        if (fromFallback && location != null) {
            return RecommendationSource.NEARBY;
        }
        return RecommendationSource.FALLBACK;
    }

    private List<RecommendationItem> toItems(List<ScoredCandidate> selected) {
        return selected.stream()
                .map(this::toItem)
                .toList();
    }

    private RecommendationItem toItem(ScoredCandidate scored) {
        RecommendationCandidate candidate = scored.candidate();
        RecommendationItem item = RecommendationItem.builder()
                .itemType(candidate.itemType())
                .itemId(candidate.itemId())
                .name(candidate.name())
                .categoryId(candidate.categoryId())
                .siteId(candidate.siteId())
                .score(round(scored.score()))
                .reason(buildReason(scored))
                .signals(scored.signals())
                .build();
        item.validate();
        return item;
    }

    private String buildReason(ScoredCandidate scored) {
        List<String> reasons = new ArrayList<>();
        addAffinityReason(reasons, scored);
        addQualityReason(reasons, scored);
        addDistanceReason(reasons, scored);
        return reasons.isEmpty() ? "Recomendado para ti" : String.join(" · ", reasons);
    }

    private void addAffinityReason(List<String> reasons, ScoredCandidate scored) {
        if (scored.signals().affinity() > 0.0 && scored.candidate().categoryName() != null) {
            reasons.add("Coincide con tu interés en " + scored.candidate().categoryName());
        }
    }

    private void addQualityReason(List<String> reasons, ScoredCandidate scored) {
        if (scored.candidate().ratingCount() > 0) {
            reasons.add(String.format(Locale.ROOT, "Bien valorado (%.1f/5, %d reseñas)",
                    scored.candidate().ratingAvg(), scored.candidate().ratingCount()));
        }
    }

    private void addDistanceReason(List<String> reasons, ScoredCandidate scored) {
        if (scored.signals().distanceKm() != null) {
            reasons.add(String.format(Locale.ROOT, "A %.1f km", scored.signals().distanceKm()));
        }
    }

    private void validateRequest(RecommendationRequest request) {
        if (request == null) {
            throw new InvalidRecommendationQueryException("recommendation request is required");
        }
        request.validate();
    }

    private void validatePolicyLimit(RecommendationQuery query) {
        if (query.limit() > policy.maxLimit()) {
            throw new InvalidLimitException("limit must be between 1 and " + policy.maxLimit());
        }
    }

    private static double round(double value) {
        return Math.round(value * 10_000.0) / 10_000.0;
    }
}
