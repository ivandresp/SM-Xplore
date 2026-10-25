package com.smxplore.activitiesevents.infrastructure.adapters.in.web.mapper;

import com.smxplore.activitiesevents.domain.model.event.Event;
import com.smxplore.activitiesevents.infrastructure.adapters.in.web.dto.EventRequest;
import com.smxplore.activitiesevents.infrastructure.adapters.in.web.dto.EventResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface EventWebMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "expectedAttendance", ignore = true)
    Event toDomain(EventRequest request);

    EventResponse toResponse(Event event);
}
