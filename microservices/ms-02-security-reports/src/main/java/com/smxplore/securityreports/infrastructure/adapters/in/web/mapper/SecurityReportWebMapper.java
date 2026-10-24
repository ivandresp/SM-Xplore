package com.smxplore.securityreports.infrastructure.adapters.in.web.mapper;

import com.smxplore.securityreports.domain.model.Comment;
import com.smxplore.securityreports.domain.model.SecurityReport;
import com.smxplore.securityreports.domain.model.types.Location;
import com.smxplore.securityreports.domain.model.types.UserRef;
import com.smxplore.securityreports.infrastructure.adapters.in.web.dto.*;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface SecurityReportWebMapper {
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    SecurityReport toDomain(SecurityReportRequest request);

    SecurityReportResponse toResponse(SecurityReport securityReport);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "reportId", ignore = true)
    Comment toDomain(CommentRequest request);

    CommentResponse toResponse(Comment comment);

    UserRef toDomain(UserRefDto userRef);

    UserRefDto toDto(UserRef userRef);

    Location toDomain(LocationDto location);

    LocationDto toDto(Location location);
}
