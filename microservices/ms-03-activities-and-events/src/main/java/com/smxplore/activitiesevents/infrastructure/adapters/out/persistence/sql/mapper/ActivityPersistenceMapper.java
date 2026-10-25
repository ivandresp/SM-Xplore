package com.smxplore.activitiesevents.infrastructure.adapters.out.persistence.sql.mapper;

import com.smxplore.activitiesevents.domain.model.activity.Activity;
import com.smxplore.activitiesevents.domain.model.activity.ActivityStatus;
import com.smxplore.activitiesevents.infrastructure.adapters.out.persistence.sql.entity.ActivityEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.math.BigDecimal;

@Mapper(componentModel = "spring", imports = {ActivityStatus.class, BigDecimal.class})
public interface ActivityPersistenceMapper {

    ActivityEntity toEntity(Activity activity);

    @Mapping(target = "status", expression = "java(entity.status() == null ? ActivityStatus.DRAFT : ActivityStatus.valueOf(entity.status()))")
    @Mapping(target = "price", expression = "java(entity.price() == null ? BigDecimal.ZERO : entity.price())")
    Activity toDomain(ActivityEntity entity);
}
