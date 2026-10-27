package com.smxplore.sitesandservices.infrastructure.adapters.out.mongo.document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "services")
public class ServiceDocument {

    @Id
    private String id;
    @Field("provider")
    private ProviderSnapshotDocument provider;
    @Field("site")
    private SiteSnapshotDocument site;
    @Field("zone")
    private ZoneSnapshotDocument zone;
    private String categoryId;
    private String categoryName;
    private String name;
    private String description;
    private String type;
    private double latitude;
    private double longitude;
    private BigDecimal price;
    private String status;
    private int maxCapacity;
    private List<AvailabilitySlotDocument> availability;
    private List<String> photosUrls;
    private String cancellationPolicy;
    private Instant createdAt;
    private Instant updatedAt;
}
