package com.smxplore.securityreports.infrastructure.adapters.in.web.mapper;

import com.smxplore.securityreports.domain.model.Comment;
import com.smxplore.securityreports.domain.model.SecurityReport;
import com.smxplore.securityreports.domain.model.types.Location;
import com.smxplore.securityreports.domain.model.types.UserRef;
import com.smxplore.securityreports.infrastructure.adapters.in.web.dto.*;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface SecurityReportWebMapper {
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "user", ignore = true)
    SecurityReport toDomain(SecurityReportRequest request);

    SecurityReportResponse toResponse(SecurityReport securityReport);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "reportId", ignore = true)
    @Mapping(target = "user", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    Comment toDomain(CommentRequest request);

    CommentResponse toResponse(Comment comment);

    UserRefDto toDto(UserRef userRef);

    Location toDomain(LocationDto location);

    LocationDto toDto(Location location);
}
