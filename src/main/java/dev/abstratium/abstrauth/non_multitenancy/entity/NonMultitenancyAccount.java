package dev.abstratium.abstrauth.non_multitenancy.entity;

import jakarta.persistence.*;
import org.hibernate.envers.NotAudited;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Non-multitenancy version of Account entity.
 * Used for cross-tenant operations and cascade deletions.
 */
@Entity
@Table(name = "T_accounts")
public class NonMultitenancyAccount {

    @Id
    @Column(length = 36)
    private String id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "email_verified")
    private Boolean emailVerified = false;

    private String name;

    private String picture;

    @Column(name = "auth_provider")
    private String authProvider;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @OneToMany(fetch = FetchType.LAZY, cascade = CascadeType.REMOVE)
    @JoinColumn(name = "account_id", referencedColumnName = "id", insertable = false, updatable = false)
    @NotAudited
    private List<NonMultitenancyAccountRole> roles = new ArrayList<>();

    @OneToMany(fetch = FetchType.LAZY, cascade = CascadeType.REMOVE)
    @JoinColumn(name = "account_id", referencedColumnName = "id", insertable = false, updatable = false)
    @NotAudited
    private List<NonMultitenancyCredential> credentials = new ArrayList<>();

    @OneToMany(fetch = FetchType.LAZY, cascade = CascadeType.REMOVE)
    @JoinColumn(name = "account_id", referencedColumnName = "id", insertable = false, updatable = false)
    @NotAudited
    private List<NonMultitenancyFederatedIdentity> federatedIdentities = new ArrayList<>();

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

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public Boolean getEmailVerified() {
        return emailVerified;
    }

    public void setEmailVerified(Boolean emailVerified) {
        this.emailVerified = emailVerified;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public String getPicture() {
        return picture;
    }

    public void setPicture(String picture) {
        this.picture = picture;
    }

    public String getAuthProvider() {
        return authProvider;
    }

    public void setAuthProvider(String authProvider) {
        this.authProvider = authProvider;
    }

    public List<NonMultitenancyAccountRole> getRoles() {
        return roles;
    }

    public void setRoles(List<NonMultitenancyAccountRole> roles) {
        this.roles = roles;
    }

    public List<NonMultitenancyCredential> getCredentials() {
        return credentials;
    }

    public void setCredentials(List<NonMultitenancyCredential> credentials) {
        this.credentials = credentials;
    }

    public List<NonMultitenancyFederatedIdentity> getFederatedIdentities() {
        return federatedIdentities;
    }

    public void setFederatedIdentities(List<NonMultitenancyFederatedIdentity> federatedIdentities) {
        this.federatedIdentities = federatedIdentities;
    }
}
