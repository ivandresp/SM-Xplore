package com.smxplore.users.identity.infrastructure.adapter.out.persistence.jpa.audit;

import com.smxplore.users.identity.domain.model.AuditAction;
import com.smxplore.users.identity.domain.model.Role;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "audit_logs")
class AuditLogJpaEntity {

    @Id
    private UUID id;

    @Column(name = "actor_user_id")
    private UUID actorUserId;

    @Column(name = "actor_email", nullable = false, length = 150)
    private String actorEmail;

    @Enumerated(EnumType.STRING)
    @Column(name = "actor_role", nullable = false, length = 30)
    private Role actorRole;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 60)
    private AuditAction action;

    @Column(name = "target_user_id")
    private UUID targetUserId;

    @Column(columnDefinition = "text")
    private String details;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    protected AuditLogJpaEntity() {}

    AuditLogJpaEntity(
            UUID id,
            UUID actorUserId,
            String actorEmail,
            Role actorRole,
            AuditAction action,
            UUID targetUserId,
            String details,
            Instant occurredAt) {
        this.id = id;
        this.actorUserId = actorUserId;
        this.actorEmail = actorEmail;
        this.actorRole = actorRole;
        this.action = action;
        this.targetUserId = targetUserId;
        this.details = details;
        this.occurredAt = occurredAt;
    }

    UUID getId() {
        return id;
    }

    UUID getActorUserId() {
        return actorUserId;
    }

    String getActorEmail() {
        return actorEmail;
    }

    Role getActorRole() {
        return actorRole;
    }

    AuditAction getAction() {
        return action;
    }

    UUID getTargetUserId() {
        return targetUserId;
    }

    String getDetails() {
        return details;
    }

    Instant getOccurredAt() {
        return occurredAt;
    }
}
