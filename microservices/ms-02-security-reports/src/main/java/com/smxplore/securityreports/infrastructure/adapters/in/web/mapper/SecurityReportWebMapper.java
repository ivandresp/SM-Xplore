package com.smxplore.securityreports.infrastructure.adapters.in.web.mapper;

import com.smxplore.securityreports.domain.model.Comment;
import com.smxplore.securityreports.domain.model.SecurityReport;
import com.smxplore.securityreports.domain.model.types.Location;
import com.smxplore.securityreports.domain.model.types.UserRef;
import com.smxplore.securityreports.infrastructure.adapters.in.web.dto.*;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface SecurityReportWebMapper {
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
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

    /**
     * Maps a report to the anonymized nearby projection: no reporter, no description, no photos
     * and coordinates rounded to three decimals.
     */
    default NearbySecurityReportResponse toNearbyResponse(SecurityReport securityReport) {
        var location = securityReport.getLocation();
        return new NearbySecurityReportResponse(
                securityReport.getId(),
                securityReport.getTitle(),
                securityReport.getStatus(),
                roundCoordinate(location == null ? null : location.latitude()),
                roundCoordinate(location == null ? null : location.longitude()),
                securityReport.getCreatedAt(),
                securityReport.getUpdatedAt()
        );
    }

    private static Double roundCoordinate(Double value) {
        return value == null ? null : BigDecimal.valueOf(value)
                .setScale(3, RoundingMode.HALF_UP)
                .doubleValue();
    }
}
