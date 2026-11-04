package com.smxplore.sitesandservices.infrastructure.adapters.out.mongo.document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "outbox_event")
public class OutboxEventDocument {

    @Id
    private String id;
    private String eventType;
    private String aggregateId;
    private String eventJson;
    private Instant occurredAt;
    private String status;
    private int attempts;
    private Instant nextAttemptAt;
    private Instant createdAt;
    private Instant publishedAt;
    private String lastError;
    private String lockedBy;
    private Instant lockedUntil;
}
