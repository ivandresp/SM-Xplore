package com.smxplore.recommendations.application;

import com.smxplore.recommendations.domain.exception.InvalidLimitException;
import com.smxplore.recommendations.domain.exception.InvalidRequestException;
import com.smxplore.recommendations.domain.model.CandidateOrigin;
import com.smxplore.recommendations.domain.model.Recommendation;
import com.smxplore.recommendations.domain.model.RecommendationCandidate;
import com.smxplore.recommendations.domain.model.RecommendationItem;
import com.smxplore.recommendations.domain.model.RecommendationPolicy;
import com.smxplore.recommendations.domain.model.RecommendationQuery;
import com.smxplore.recommendations.domain.model.RecommendationSource;
import com.smxplore.recommendations.domain.model.ScoredCandidate;
import com.smxplore.recommendations.domain.model.ScoringContext;
import com.smxplore.recommendations.domain.ports.in.GetRecommendationsUseCase;
import com.smxplore.recommendations.domain.ports.out.RecommendationGraphPort;
import com.smxplore.recommendations.domain.service.DiversitySelector;
import com.smxplore.recommendations.domain.service.RecommendationScorer;
import reactor.core.publisher.Mono;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class RecommendationService implements GetRecommendationsUseCase {

    private final RecommendationGraphPort graphPort;
    private final RecommendationScorer scorer;
    private final DiversitySelector diversitySelector;
    private final RecommendationPolicy policy;
    private final Clock clock;

    public RecommendationService(
            RecommendationGraphPort graphPort,
            RecommendationScorer scorer,
            DiversitySelector diversitySelector,
            RecommendationPolicy policy,
            Clock clock
    ) {
        this.graphPort = graphPort;
        this.scorer = scorer;
        this.diversitySelector = diversitySelector;
        this.policy = policy;
        this.clock = clock;
    }

    @Override
    public Mono<Recommendation> handle(RecommendationQuery query) {
        validate(query);
        Instant now = clock.instant();
        int limit = query.limit();

        Mono<List<RecommendationCandidate>> affinityCandidates = graphPort
                .findCandidatesForTourist(query.touristId(), query.siteId(), query.location(),
                        policy.candidateLimit(), now)
                .collectList();

        Mono<List<RecommendationCandidate>> fallbackCandidates = graphPort
                .findFallbackCandidates(query.touristId(), query.siteId(), query.location(),
                        policy.candidateLimit(), now)
                .collectList();

        return affinityCandidates.flatMap(affinity -> {
            if (affinity.size() >= limit) {
                return Mono.just(buildRecommendation(query, affinity, List.of(), now, limit));
            }
            return fallbackCandidates.map(fallback -> buildRecommendation(query, affinity, fallback, now, limit));
        });
    }

    private Recommendation buildRecommendation(
            RecommendationQuery query,
            List<RecommendationCandidate> affinityCandidates,
            List<RecommendationCandidate> fallbackCandidates,
            Instant now,
            int limit
    ) {
        Set<java.util.UUID> seen = new HashSet<>();
        List<RecommendationCandidate> candidates = new ArrayList<>();

        affinityCandidates.forEach(candidate -> {
            if (seen.add(candidate.itemId())) {
                candidates.add(candidate);
            }
        });
        fallbackCandidates.forEach(candidate -> {
            if (seen.add(candidate.itemId())) {
                candidates.add(candidate);
            }
        });

        boolean hasAffinity = !affinityCandidates.isEmpty();
        double maxAffinity = affinityCandidates.stream()
                .mapToDouble(RecommendationCandidate::affinity)
                .max()
                .orElse(0.0);
        ScoringContext context = new ScoringContext(query.location(), maxAffinity, hasAffinity);

        List<ScoredCandidate> scored = candidates.stream()
                .map(candidate -> scorer.score(candidate, context, now))
                .toList();
        List<ScoredCandidate> selected = diversitySelector.select(scored, limit);

        boolean fromAffinity = selected.stream()
                .anyMatch(scoredCandidate -> scoredCandidate.candidate().origin() == CandidateOrigin.AFFINITY);
        boolean fromFallback = selected.stream()
                .anyMatch(scoredCandidate -> scoredCandidate.candidate().origin() == CandidateOrigin.FALLBACK);

        RecommendationSource source = source(query, fromAffinity, fromFallback);
        List<RecommendationItem> items = selected.stream()
                .map(this::toItem)
                .toList();

        return new Recommendation(query.touristId(), now, source, items);
    }

    private RecommendationSource source(RecommendationQuery query, boolean fromAffinity, boolean fromFallback) {
        if (fromAffinity && fromFallback) {
            return RecommendationSource.MIXED;
        }
        if (fromAffinity) {
            return RecommendationSource.AFFINITY;
        }
        if (fromFallback && query.location() != null) {
            return RecommendationSource.NEARBY;
        }
        if (fromFallback) {
            return RecommendationSource.FALLBACK;
        }
        return RecommendationSource.FALLBACK;
    }

    private RecommendationItem toItem(ScoredCandidate scored) {
        RecommendationCandidate candidate = scored.candidate();
        List<String> reasons = new ArrayList<>();

        if (scored.signals().affinity() > 0.0 && candidate.categoryName() != null) {
            reasons.add("Coincide con tu interés en " + candidate.categoryName());
        }
        if (candidate.ratingCount() > 0) {
            reasons.add(String.format(Locale.ROOT, "Bien valorado (%.1f/5, %d reseñas)",
                    candidate.ratingAvg(), candidate.ratingCount()));
        }
        if (scored.signals().distanceKm() != null) {
            reasons.add(String.format(Locale.ROOT, "A %.1f km", scored.signals().distanceKm()));
        }
        if (reasons.isEmpty()) {
            reasons.add("Recomendado para ti");
        }

        return new RecommendationItem(
                candidate.itemType(),
                candidate.itemId(),
                candidate.name(),
                candidate.categoryId(),
                candidate.siteId(),
                round(scored.score()),
                String.join(" · ", reasons),
                scored.signals()
        );
    }

    private void validate(RecommendationQuery query) {
        if (query == null || query.touristId() == null) {
            throw new InvalidRequestException("touristId is required");
        }
        if (query.limit() < 1 || query.limit() > policy.maxLimit()) {
            throw new InvalidLimitException("limit must be between 1 and " + policy.maxLimit());
        }
    }

    private static double round(double value) {
        return Math.round(value * 10_000.0) / 10_000.0;
    }
}
