package com.smxplore.activitiesevents.infrastructure.adapters.out.persistence.sql.mapper;

import com.smxplore.activitiesevents.domain.model.reservation.Reservation;
import com.smxplore.activitiesevents.infrastructure.adapters.out.persistence.sql.entity.ReservationEntity;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface ReservationPersistenceMapper {

    ReservationEntity toEntity(Reservation reservation);

    Reservation toDomain(ReservationEntity entity);
}
