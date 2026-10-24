package com.smxplore.users.identity.infrastructure.adapter.out.persistence.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "destination_manager_profiles")
class DestinationManagerProfileJpaEntity {

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Column(nullable = false, length = 30)
    private String nit;

    @Column(name = "start_operation_date")
    private LocalDate startOperationDate;

    protected DestinationManagerProfileJpaEntity() {}

    DestinationManagerProfileJpaEntity(UUID userId, String nit, LocalDate startOperationDate) {
        this.userId = userId;
        this.nit = nit;
        this.startOperationDate = startOperationDate;
    }

    void updateFrom(DestinationManagerProfileJpaEntity source) {
        this.nit = source.nit;
        this.startOperationDate = source.startOperationDate;
    }

    UUID getUserId() {
        return userId;
    }

    String getNit() {
        return nit;
    }

    LocalDate getStartOperationDate() {
        return startOperationDate;
    }
}
