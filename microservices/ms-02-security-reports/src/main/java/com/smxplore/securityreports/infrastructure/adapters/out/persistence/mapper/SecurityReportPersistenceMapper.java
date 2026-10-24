package com.smxplore.securityreports.infrastructure.adapters.out.persistence.mapper;

import com.smxplore.securityreports.domain.model.SecurityReport;
import com.smxplore.securityreports.domain.model.SecurityReportStatus;
import com.smxplore.securityreports.infrastructure.adapters.out.persistence.document.SecurityReportDocument;
import org.mapstruct.Mapper;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Mapper(componentModel = "spring", uses = {UserRefPersistenceMapper.class, LocationPersistenceMapper.class})
public interface SecurityReportPersistenceMapper {

    SecurityReportDocument toDocument(SecurityReport report);

    SecurityReport toDomain(SecurityReportDocument document);

    default List<String> mapPhotos(List<String> photosUrls) {
        return photosUrls == null ? new ArrayList<>() : new ArrayList<>(photosUrls);
    }

    default SecurityReportStatus mapStatus(SecurityReportStatus status) {
        return status == null ? SecurityReportStatus.ONGOING : status;
    }

    default Instant mapCreatedAt(Instant createdAt) {
        return createdAt == null ? Instant.now() : createdAt;
    }
}
