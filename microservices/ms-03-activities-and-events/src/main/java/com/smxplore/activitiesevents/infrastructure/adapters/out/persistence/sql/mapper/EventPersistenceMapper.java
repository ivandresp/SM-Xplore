package com.smxplore.activitiesevents.infrastructure.adapters.out.persistence.sql.mapper;

import com.smxplore.activitiesevents.domain.model.event.Event;
import com.smxplore.activitiesevents.domain.model.event.EventStatus;
import com.smxplore.activitiesevents.infrastructure.adapters.out.persistence.sql.entity.EventEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.math.BigDecimal;

@Mapper(componentModel = "spring", imports = {EventStatus.class, BigDecimal.class})
public interface EventPersistenceMapper {

    EventEntity toEntity(Event event);

    @Mapping(target = "status", expression = "java(entity.status() == null ? EventStatus.DRAFT : EventStatus.valueOf(entity.status()))")
    @Mapping(target = "price", expression = "java(entity.price() == null ? BigDecimal.ZERO : entity.price())")
    Event toDomain(EventEntity entity);
}
