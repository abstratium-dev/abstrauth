package dev.abstratium.abstrauth.non_multitenancy.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Non-multitenancy version of Organisation entity.
 * Used for cross-tenant operations and cascade deletions.
 */
@Entity
@Table(name = "T_organisations")
public class NonMultitenancyOrganisation {

    @Id
    @Column(length = 36)
    private String id;

    @Column(nullable = false)
    private String name;

    @Column(name = "created_by_account_id", length = 36)
    private String createdByAccountId;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @OneToMany(fetch = FetchType.LAZY, cascade = CascadeType.REMOVE)
    @JoinColumn(name = "org_id", referencedColumnName = "id", insertable = false, updatable = false)
    private List<NonMultitenancySubscription> subscriptions = new ArrayList<>();

    @PrePersist
    public void prePersist() {
        if (id == null) {
            id = UUID.randomUUID().toString();
        }
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    // Getters and setters
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getCreatedByAccountId() {
        return createdByAccountId;
    }

    public void setCreatedByAccountId(String createdByAccountId) {
        this.createdByAccountId = createdByAccountId;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public List<NonMultitenancySubscription> getSubscriptions() {
        return subscriptions;
    }

    public void setSubscriptions(List<NonMultitenancySubscription> subscriptions) {
        this.subscriptions = subscriptions;
    }
}
