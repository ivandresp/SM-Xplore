package com.smxplore.recommendations.infrastructure.config;

import com.smxplore.recommendations.application.interaction.RegisterViewService;
import com.smxplore.recommendations.application.preference.AddPreferenceService;
import com.smxplore.recommendations.application.preference.RemovePreferenceService;
import com.smxplore.recommendations.application.recommendation.DiversitySelector;
import com.smxplore.recommendations.application.recommendation.RecommendationScorer;
import com.smxplore.recommendations.application.recommendation.RecommendationService;
import com.smxplore.recommendations.domain.model.ranking.RecommendationPolicy;
import com.smxplore.recommendations.domain.ports.in.AddPreferenceUseCase;
import com.smxplore.recommendations.domain.ports.in.GetRecommendationsUseCase;
import com.smxplore.recommendations.domain.ports.in.RegisterViewUseCase;
import com.smxplore.recommendations.domain.ports.in.RemovePreferenceUseCase;
import com.smxplore.recommendations.domain.ports.out.RecommendationGraphPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration(proxyBeanMethods = false)
public class BeanConfiguration {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    public RecommendationPolicy recommendationPolicy(RecommendationProperties properties) {
        RecommendationProperties.Weights weights = properties.getWeights();
        RecommendationPolicy policy = RecommendationPolicy.builder()
                .defaultLimit(properties.getDefaultLimit())
                .maxLimit(properties.getMaxLimit())
                .candidateLimit(properties.getCandidateLimit())
                .categoryMaxDepth(properties.getCategoryMaxDepth())
                .proximityRadiusKm(properties.getProximityRadiusKm())
                .noveltyHalfLifeDays(properties.getNoveltyHalfLifeDays())
                .preferenceHalfLifeDays(properties.getPreferenceHalfLifeDays())
                .qualityPriorMean(properties.getQualityPriorMean())
                .qualityPriorCount(properties.getQualityPriorCount())
                .maxItemsPerSite(properties.getMaxItemsPerSite())
                .maxCategoryRatio(properties.getMaxCategoryRatio())
                .affinityWeight(weights.getAffinity())
                .qualityWeight(weights.getQuality())
                .proximityWeight(weights.getProximity())
                .noveltyWeight(weights.getNovelty())
                .build();
        policy.validate();
        return policy;
    }

    @Bean
    public RecommendationScorer recommendationScorer(RecommendationPolicy policy) {
        return new RecommendationScorer(policy);
    }

    @Bean
    public DiversitySelector diversitySelector(RecommendationPolicy policy) {
        return new DiversitySelector(policy);
    }

    @Bean
    public GetRecommendationsUseCase getRecommendationsUseCase(
            RecommendationGraphPort graphPort,
            RecommendationScorer scorer,
            DiversitySelector diversitySelector,
            RecommendationPolicy policy,
            Clock clock
    ) {
        return new RecommendationService(graphPort, scorer, diversitySelector, policy, clock);
    }

    @Bean
    public AddPreferenceUseCase addPreferenceUseCase(RecommendationGraphPort graphPort, Clock clock) {
        return new AddPreferenceService(graphPort, clock);
    }

    @Bean
    public RemovePreferenceUseCase removePreferenceUseCase(RecommendationGraphPort graphPort) {
        return new RemovePreferenceService(graphPort);
    }

    @Bean
    public RegisterViewUseCase registerViewUseCase(RecommendationGraphPort graphPort, Clock clock) {
        return new RegisterViewService(graphPort, clock);
    }
}
