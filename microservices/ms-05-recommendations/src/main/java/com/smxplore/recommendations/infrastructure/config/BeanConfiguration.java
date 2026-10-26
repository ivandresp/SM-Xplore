package com.smxplore.recommendations.infrastructure.config;

import com.smxplore.recommendations.application.PreferenceService;
import com.smxplore.recommendations.application.RecommendationService;
import com.smxplore.recommendations.application.ViewService;
import com.smxplore.recommendations.domain.model.RecommendationPolicy;
import com.smxplore.recommendations.domain.ports.in.GetRecommendationsUseCase;
import com.smxplore.recommendations.domain.ports.in.ManagePreferencesUseCase;
import com.smxplore.recommendations.domain.ports.in.RegisterViewUseCase;
import com.smxplore.recommendations.domain.ports.out.RecommendationGraphPort;
import com.smxplore.recommendations.domain.service.DiversitySelector;
import com.smxplore.recommendations.domain.service.RecommendationScorer;
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
        return new RecommendationPolicy(
                properties.getDefaultLimit(),
                properties.getMaxLimit(),
                properties.getCandidateLimit(),
                properties.getCategoryMaxDepth(),
                properties.getProximityRadiusKm(),
                properties.getNoveltyHalfLifeDays(),
                properties.getPreferenceHalfLifeDays(),
                properties.getQualityPriorMean(),
                properties.getQualityPriorCount(),
                properties.getMaxItemsPerSite(),
                properties.getMaxCategoryRatio(),
                weights.getAffinity(),
                weights.getQuality(),
                weights.getProximity(),
                weights.getNovelty()
        );
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
    public ManagePreferencesUseCase managePreferencesUseCase(RecommendationGraphPort graphPort, Clock clock) {
        return new PreferenceService(graphPort, clock);
    }

    @Bean
    public RegisterViewUseCase registerViewUseCase(RecommendationGraphPort graphPort, Clock clock) {
        return new ViewService(graphPort, clock);
    }
}
