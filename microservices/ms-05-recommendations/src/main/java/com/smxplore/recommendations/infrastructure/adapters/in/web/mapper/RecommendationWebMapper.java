package com.smxplore.recommendations.infrastructure.adapters.in.web.mapper;

import com.smxplore.recommendations.domain.model.preference.Preference;
import com.smxplore.recommendations.domain.model.recommendation.Recommendation;
import com.smxplore.recommendations.domain.model.recommendation.RecommendationItem;
import com.smxplore.recommendations.domain.model.recommendation.RecommendationSignals;
import com.smxplore.recommendations.infrastructure.adapters.in.web.dto.PreferenceResponse;
import com.smxplore.recommendations.infrastructure.adapters.in.web.dto.RecommendationItemResponse;
import com.smxplore.recommendations.infrastructure.adapters.in.web.dto.RecommendationResponse;
import com.smxplore.recommendations.infrastructure.adapters.in.web.dto.RecommendationSignalsResponse;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface RecommendationWebMapper {

    RecommendationResponse toResponse(Recommendation recommendation);

    RecommendationItemResponse toItemResponse(RecommendationItem item);

    RecommendationSignalsResponse toSignalsResponse(RecommendationSignals signals);

    PreferenceResponse toPreferenceResponse(Preference preference);
}
