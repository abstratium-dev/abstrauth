package dev.abstratium.abstrauth.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Links an Account to an external identity provider (Google, Microsoft, GitHub, etc.)
 * Allows users to link multiple providers to the same account
 */
@Entity
@Table(name = "T_federated_identities", 
       uniqueConstraints = @UniqueConstraint(name = "I_federated_provider_user", 
                                            columnNames = {"provider", "provider_user_id"}))
public class FederatedIdentity {

    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "account_id", nullable = false, length = 36)
    private String accountId;

    @Column(nullable = false, length = 50)
    private String provider; // "google", etc.

    @Column(name = "provider_user_id", nullable = false, length = 255)
    private String providerUserId; // The user ID from the external provider

    @Column(length = 255)
    private String email; // Email from the provider (may differ from account email)

    @Column(name = "connected_at")
    private LocalDateTime connectedAt;

    @PrePersist
    public void prePersist() {
        if (id == null) {
            id = UUID.randomUUID().toString();
        }
        if (connectedAt == null) {
            connectedAt = LocalDateTime.now();
        }
    }

    // Getters and setters
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getAccountId() {
        return accountId;
    }

    public void setAccountId(String accountId) {
        this.accountId = accountId;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getProviderUserId() {
        return providerUserId;
    }

    public void setProviderUserId(String providerUserId) {
        this.providerUserId = providerUserId;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public LocalDateTime getConnectedAt() {
        return connectedAt;
    }

    public void setConnectedAt(LocalDateTime connectedAt) {
        this.connectedAt = connectedAt;
    }
}
