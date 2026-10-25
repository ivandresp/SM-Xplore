package com.smxplore.activitiesevents.infrastructure.adapters.in.web.mapper;

import com.smxplore.activitiesevents.domain.model.reservation.ReservationItem;
import com.smxplore.activitiesevents.infrastructure.adapters.in.web.dto.ReservationItemRequest;
import com.smxplore.activitiesevents.infrastructure.adapters.in.web.dto.ReservationItemResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface ReservationItemWebMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "reservationId", ignore = true)
    ReservationItem toDomain(ReservationItemRequest request);

    List<ReservationItem> toDomainList(List<ReservationItemRequest> items);

    ReservationItemResponse toResponse(ReservationItem reservationItem);
}
