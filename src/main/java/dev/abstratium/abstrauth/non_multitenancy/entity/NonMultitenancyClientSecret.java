package dev.abstratium.abstrauth.non_multitenancy.entity;

import jakarta.persistence.*;
import org.hibernate.envers.Audited;
import java.time.Instant;

/**
 * Non-multitenancy version of ClientSecret entity.
 * 
 * This entity bypasses Hibernate's @TenantId discriminator to allow querying
 * client secrets across all organizations. This is required for the token
 * endpoint to authenticate confidential clients regardless of which org
 * the current JWT resolves to.
 * 
 * Client secrets are owned by the organization that created the client,
 * not the organization of the user requesting tokens.
 */
@Entity
@Table(name = "T_oauth_client_secrets")
@Audited
public class NonMultitenancyClientSecret {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "client_id", nullable = false)
    private String clientId;

    @Column(name = "secret_hash", nullable = false)
    private String secretHash;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "description")
    private String description;

    @Column(name = "first_warning_sent_at")
    private Instant firstWarningSentAt;

    @Column(name = "final_warning_sent_at")
    private Instant finalWarningSentAt;

    @Column(name = "expired_notice_sent_at")
    private Instant expiredNoticeSentAt;

    @Column(name = "org_id", length = 36)
    private String orgId;

    // Getters and setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public String getSecretHash() {
        return secretHash;
    }

    public void setSecretHash(String secretHash) {
        this.secretHash = secretHash;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Instant getFirstWarningSentAt() {
        return firstWarningSentAt;
    }

    public void setFirstWarningSentAt(Instant firstWarningSentAt) {
        this.firstWarningSentAt = firstWarningSentAt;
    }

    public Instant getFinalWarningSentAt() {
        return finalWarningSentAt;
    }

    public void setFinalWarningSentAt(Instant finalWarningSentAt) {
        this.finalWarningSentAt = finalWarningSentAt;
    }

    public Instant getExpiredNoticeSentAt() {
        return expiredNoticeSentAt;
    }

    public void setExpiredNoticeSentAt(Instant expiredNoticeSentAt) {
        this.expiredNoticeSentAt = expiredNoticeSentAt;
    }

    public String getOrgId() {
        return orgId;
    }

    public void setOrgId(String orgId) {
        this.orgId = orgId;
    }
}
