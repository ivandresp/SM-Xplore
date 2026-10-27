package com.smxplore.sitesandservices.infrastructure.adapters.out.mongo.document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "reviews")
public class ReviewDocument {

    @Id
    private String id;
    private String touristId;
    private String touristName;
    private String itemType;
    private String itemId;
    private String itemName;
    private int rating;
    private String commentary;
    private List<String> photosUrls;
    private String response;
    private Instant respondedAt;
    private int helpfulReactions;
    private String status;
    private Instant createdAt;
    private Instant updatedAt;
}
