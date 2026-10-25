package com.smxplore.activitiesevents.infrastructure.adapters.in.web.mapper;

import com.smxplore.activitiesevents.domain.model.activity.Activity;
import com.smxplore.activitiesevents.infrastructure.adapters.in.web.dto.ActivityRequest;
import com.smxplore.activitiesevents.infrastructure.adapters.in.web.dto.ActivityResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface ActivityWebMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "status", ignore = true)
    Activity toDomain(ActivityRequest request);

    ActivityResponse toResponse(Activity activity);
}
