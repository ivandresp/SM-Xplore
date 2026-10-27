package com.smxplore.sitesandservices.infrastructure.adapters.out.mongo.document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.Instant;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "sites")
public class SiteDocument {

    @Id
    private String id;
    private String managerId;
    private String name;
    private String description;
    private String categoryId;
    private String categoryName;
    @Field("zone")
    private ZoneSnapshotDocument zone;
    private double latitude;
    private double longitude;
    private List<String> photosUrls;
    private String status;
    private int maxCapacity;
    private List<AvailabilitySlotDocument> availability;
    private Instant createdAt;
    private Instant updatedAt;
}
