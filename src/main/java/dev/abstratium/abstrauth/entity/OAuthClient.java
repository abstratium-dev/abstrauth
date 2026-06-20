package dev.abstratium.abstrauth.entity;

import java.time.LocalDateTime;
import java.util.UUID;

import org.hibernate.annotations.TenantId;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

@Entity
@Table(name = "T_oauth_clients")
public class OAuthClient {

    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "client_id", nullable = false, unique = true)
    private String clientId;

    @Column(name = "client_name", nullable = false)
    private String clientName;

    @Column(name = "client_type", nullable = false, length = 20)
    private String clientType; // 'confidential' only (BFF pattern required)

    @Column(name = "redirect_uris", nullable = false, columnDefinition = "TEXT")
    private String redirectUris; // JSON array

    @Column(name = "allowed_scopes", columnDefinition = "TEXT")
    private String allowedScopes; // JSON array

    @Column(name = "require_pkce")
    private Boolean requirePkce = true;

    @Column(name = "auto_subscribe")
    private Boolean autoSubscribe = false;

    @Column(name = "publik")
    private Boolean publik = false;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @TenantId
    @Column(name = "org_id", length = 36)
    private String orgId;

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

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public String getClientName() {
        return clientName;
    }

    public void setClientName(String clientName) {
        this.clientName = clientName;
    }

    public String getClientType() {
        return clientType;
    }

    public void setClientType(String clientType) {
        this.clientType = clientType;
    }

    public String getRedirectUris() {
        return redirectUris;
    }

    public void setRedirectUris(String redirectUris) {
        this.redirectUris = redirectUris;
    }

    public String getAllowedScopes() {
        return allowedScopes;
    }

    public void setAllowedScopes(String allowedScopes) {
        this.allowedScopes = allowedScopes;
    }

    public Boolean getRequirePkce() {
        return requirePkce;
    }

    public void setRequirePkce(Boolean requirePkce) {
        this.requirePkce = requirePkce;
    }

    public Boolean getAutoSubscribe() {
        return autoSubscribe;
    }

    public void setAutoSubscribe(Boolean autoSubscribe) {
        this.autoSubscribe = autoSubscribe;
    }

    public Boolean getPublik() {
        return publik;
    }

    public void setPublik(Boolean publik) {
        this.publik = publik;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public String getOrgId() {
        return orgId;
    }

    public void setOrgId(String orgId) {
        this.orgId = orgId;
    }
}
