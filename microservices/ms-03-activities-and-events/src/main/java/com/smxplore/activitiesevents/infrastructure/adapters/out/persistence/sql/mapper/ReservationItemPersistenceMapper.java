package com.smxplore.activitiesevents.infrastructure.adapters.out.persistence.sql.mapper;

import com.smxplore.activitiesevents.domain.model.reservation.ReservationItem;
import com.smxplore.activitiesevents.infrastructure.adapters.out.persistence.sql.entity.ReservationItemEntity;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface ReservationItemPersistenceMapper {

    ReservationItemEntity toEntity(ReservationItem reservationItem);

    ReservationItem toDomain(ReservationItemEntity entity);
}
