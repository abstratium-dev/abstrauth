package dev.abstratium.abstrauth.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "T_oauth_client_secrets")
public class ClientSecret {
    
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
}
