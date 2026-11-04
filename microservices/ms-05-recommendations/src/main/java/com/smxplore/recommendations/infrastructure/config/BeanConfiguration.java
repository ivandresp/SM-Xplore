package com.smxplore.recommendations.infrastructure.config;

import com.smxplore.recommendations.application.interaction.RegisterViewService;
import com.smxplore.recommendations.application.preference.AddPreferenceService;
import com.smxplore.recommendations.application.preference.RemovePreferenceService;
import com.smxplore.recommendations.application.projection.ProjectionService;
import com.smxplore.recommendations.application.recommendation.DiversitySelector;
import com.smxplore.recommendations.application.retention.ProcessedEventRetentionService;
import com.smxplore.recommendations.application.recommendation.RecommendationScorer;
import com.smxplore.recommendations.application.recommendation.RecommendationService;
import com.smxplore.recommendations.application.reindex.ReindexService;
import com.smxplore.recommendations.domain.model.ranking.RecommendationPolicy;
import com.smxplore.recommendations.domain.ports.in.AddPreferenceUseCase;
import com.smxplore.recommendations.domain.ports.in.GetRecommendationsUseCase;
import com.smxplore.recommendations.domain.ports.in.HandleProjectionEventUseCase;
import com.smxplore.recommendations.domain.ports.in.PurgeProcessedEventsUseCase;
import com.smxplore.recommendations.domain.ports.in.RegisterViewUseCase;
import com.smxplore.recommendations.domain.ports.in.ReindexCatalogUseCase;
import com.smxplore.recommendations.domain.ports.in.RemovePreferenceUseCase;
import com.smxplore.recommendations.domain.ports.out.CatalogExportPort;
import com.smxplore.recommendations.domain.ports.out.EventProjectionPort;
import com.smxplore.recommendations.domain.ports.out.ProcessedEventRetentionPort;
import com.smxplore.recommendations.domain.ports.out.RecommendationGraphPort;
import com.smxplore.recommendations.infrastructure.adapters.in.kafka.ProjectionEventDecoder;
import com.smxplore.recommendations.infrastructure.adapters.out.web.CatalogExportClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;
import tools.jackson.databind.ObjectMapper;

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

    @Bean
    public HandleProjectionEventUseCase handleProjectionEventUseCase(EventProjectionPort projectionPort) {
        return new ProjectionService(projectionPort);
    }

    @Bean
    public PurgeProcessedEventsUseCase purgeProcessedEventsUseCase(
            ProcessedEventRetentionPort retentionPort
    ) {
        return new ProcessedEventRetentionService(retentionPort);
    }

    @Bean
    public CatalogExportPort catalogExportPort(
            ObjectMapper objectMapper,
            ProjectionEventDecoder decoder,
            ReindexProperties reindexProperties
    ) {
        return new CatalogExportClient(WebClient.builder(), objectMapper, decoder, reindexProperties);
    }

    @Bean
    public ReindexCatalogUseCase reindexCatalogUseCase(
            CatalogExportPort catalogExportPort,
            HandleProjectionEventUseCase handleProjectionEventUseCase,
            ReindexProperties reindexProperties
    ) {
        return new ReindexService(catalogExportPort, handleProjectionEventUseCase,
                reindexProperties.getMaxPages());
    }
}
