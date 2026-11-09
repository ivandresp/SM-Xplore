package com.smxplore.securityreports.infrastructure.adapters.out.persistence.document;

import com.smxplore.securityreports.domain.model.SecurityReportStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.geo.GeoJsonPoint;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.GeoSpatialIndexType;
import org.springframework.data.mongodb.core.index.GeoSpatialIndexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "security_reports")
@CompoundIndexes({
        @CompoundIndex(name = "idx_security_reports_status_created_at_v2",
                def = "{'status': 1, 'createdAt': -1, '_id': -1}"),
        @CompoundIndex(name = "idx_security_reports_user_created_at_v2",
                def = "{'user.userId': 1, 'createdAt': -1, '_id': -1}")
})
public class SecurityReportDocument {
    @Id
    private String id;
    private UserRefDocument user;
    private String title;
    private String description;
    private LocationDocument location;

    @GeoSpatialIndexed(name = "idx_security_reports_position", type = GeoSpatialIndexType.GEO_2DSPHERE)
    private GeoJsonPoint position;

    @Builder.Default
    private List<String> photosUrls = new ArrayList<>();

    private SecurityReportStatus status;
    private Instant createdAt;
    private Instant updatedAt;

    @Version
    private Long version;

    /** Embedded audit trail of the last close/solve transition (MS02-15). */
    private StatusChangeDocument statusChange;
}
