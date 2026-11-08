package com.smxplore.securityreports.infrastructure.adapters.out.persistence.mapper;

import com.smxplore.securityreports.domain.model.SecurityReport;
import com.smxplore.securityreports.domain.model.SecurityReportStatus;
import com.smxplore.securityreports.domain.model.types.StatusChange;
import com.smxplore.securityreports.infrastructure.adapters.out.persistence.document.SecurityReportDocument;
import com.smxplore.securityreports.infrastructure.adapters.out.persistence.document.StatusChangeDocument;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Mapper(componentModel = "spring",
        uses = {UserRefPersistenceMapper.class, LocationPersistenceMapper.class},
        unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface SecurityReportPersistenceMapper {

    @Mapping(target = "position", ignore = true)
    @Mapping(target = "statusChange", source = "lastStatusChange")
    SecurityReportDocument toDocument(SecurityReport report);

    @Mapping(target = "lastStatusChange", source = "statusChange")
    SecurityReport toDomain(SecurityReportDocument document);

    default List<String> mapPhotos(List<String> photosUrls) {
        return photosUrls == null ? new ArrayList<>() : new ArrayList<>(photosUrls);
    }

    default SecurityReportStatus mapStatus(SecurityReportStatus status) {
        return status == null ? SecurityReportStatus.ONGOING : status;
    }

    default Instant mapCreatedAt(Instant createdAt) {
        return createdAt == null ? Instant.EPOCH : createdAt;
    }

    /** MS02-15: the audit trail is only produced by this service, so a malformed actor is a bug, not legacy data. */
    default StatusChangeDocument toStatusChangeDocument(StatusChange statusChange) {
        if (statusChange == null) return null;
        return StatusChangeDocument.builder()
                .actorId(statusChange.actorId().toString())
                .from(statusChange.from())
                .to(statusChange.to())
                .at(statusChange.at())
                .correlationId(statusChange.correlationId())
                .build();
    }

    default StatusChange toStatusChange(StatusChangeDocument document) {
        if (document == null) return null;
        return new StatusChange(
                document.getActorId() == null ? null : UUID.fromString(document.getActorId()),
                document.getFrom(),
                document.getTo(),
                document.getAt(),
                document.getCorrelationId());
    }
}
