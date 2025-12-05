package dev.abstratium.abstrauth.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Represents a revoked JWT token.
 * Used to prevent authorization code replay attacks and support token revocation.
 */
@Entity
@Table(name = "T_revoked_tokens")
public class RevokedToken {

    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "token_jti", nullable = false, length = 255)
    private String tokenJti;

    @Column(name = "revoked_at", nullable = false)
    private LocalDateTime revokedAt;

    @Column(nullable = false, length = 100)
    private String reason;

    @Column(name = "authorization_code_id", length = 36)
    private String authorizationCodeId;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        if (id == null) {
            id = UUID.randomUUID().toString();
        }
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (revokedAt == null) {
            revokedAt = LocalDateTime.now();
        }
    }

    // Getters and setters
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTokenJti() {
        return tokenJti;
    }

    public void setTokenJti(String tokenJti) {
        this.tokenJti = tokenJti;
    }

    public LocalDateTime getRevokedAt() {
        return revokedAt;
    }

    public void setRevokedAt(LocalDateTime revokedAt) {
        this.revokedAt = revokedAt;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public String getAuthorizationCodeId() {
        return authorizationCodeId;
    }

    public void setAuthorizationCodeId(String authorizationCodeId) {
        this.authorizationCodeId = authorizationCodeId;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
