package com.smxplore.securityreports.infrastructure.adapters.out.persistence.mapper;

import com.smxplore.securityreports.domain.model.types.UserRef;
import com.smxplore.securityreports.infrastructure.adapters.out.persistence.document.UserRefDocument;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface UserRefPersistenceMapper {

    @Mapping(target = "userId", source = "id")
    UserRefDocument toDocument(UserRef user);

    @Mapping(target = "id", source = "userId")
    UserRef toDomain(UserRefDocument document);
}
