package com.smxplore.sitesandservices.infrastructure.adapters.out.mongo.document;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Getter
@Setter
@Document(collection = "security_audit")
public class SecurityAuditDocument {

    @Id
    private String auditId;

    private Instant occurredAt;

    private String action;

    private String actorId;

    private String actorRole;

    private String targetId;

    private String detail;
}
