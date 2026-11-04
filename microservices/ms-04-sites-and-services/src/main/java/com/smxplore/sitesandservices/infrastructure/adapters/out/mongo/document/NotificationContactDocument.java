package com.smxplore.sitesandservices.infrastructure.adapters.out.mongo.document;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Getter
@Setter
@Document(collection = "notification_contacts")
public class NotificationContactDocument {

    @Id
    private String userId;

    private String email;

    private Instant updatedAt;
}
