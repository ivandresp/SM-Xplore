package com.smxplore.activitiesevents.infrastructure.adapters.in.web.mapper;

import com.smxplore.activitiesevents.domain.model.reservation.Reservation;
import com.smxplore.activitiesevents.infrastructure.adapters.in.web.dto.ReservationRequest;
import com.smxplore.activitiesevents.infrastructure.adapters.in.web.dto.ReservationResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface ReservationWebMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "bookedAt", ignore = true)
    @Mapping(target = "total", ignore = true)
    @Mapping(target = "providerId", ignore = true)
    @Mapping(target = "guideId", ignore = true)
    @Mapping(target = "code", ignore = true)
    Reservation toDomain(ReservationRequest request);

    ReservationResponse toResponse(Reservation reservation);
}
