package com.smxplore.securityreports.infrastructure.adapters.out.persistence.mapper;

import com.smxplore.securityreports.domain.model.types.Location;
import com.smxplore.securityreports.infrastructure.adapters.out.persistence.document.LocationDocument;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface LocationPersistenceMapper {

    LocationDocument toDocument(Location location);

    Location toDomain(LocationDocument document);
}
