package dev.abstratium.abstrauth.non_multitenancy.entity;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import org.hibernate.envers.NotAudited;

@Entity
@Table(name = "T_oauth_clients")
public class NonMultitenancyOAuthClient {

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

    @Column(name = "org_id", length = 36)
    private String orgId;

    @OneToMany(fetch = FetchType.LAZY, cascade = CascadeType.REMOVE)
    @JoinColumn(name = "client_id", referencedColumnName = "client_id", insertable = false, updatable = false)
    @NotAudited
    private List<NonMultitenancyAccountRole> accountRoles = new ArrayList<>();

    @OneToMany(fetch = FetchType.LAZY, cascade = CascadeType.REMOVE)
    @JoinColumn(name = "client_id", referencedColumnName = "client_id", insertable = false, updatable = false)
    @NotAudited
    private List<NonMultitenancyClientSecret> clientSecrets = new ArrayList<>();

    @OneToMany(fetch = FetchType.LAZY, cascade = CascadeType.REMOVE)
    @JoinColumn(name = "client_id", referencedColumnName = "client_id", insertable = false, updatable = false)
    @NotAudited
    private List<NonMultitenancyClientAllowedRole> clientAllowedRoles = new ArrayList<>();

    @OneToMany(fetch = FetchType.LAZY, cascade = CascadeType.REMOVE)
    @JoinColumn(name = "src_client_id", referencedColumnName = "client_id", insertable = false, updatable = false)
    @NotAudited
    private List<NonMultitenancyClientRole> srcClientRoles = new ArrayList<>();

    @OneToMany(fetch = FetchType.LAZY, cascade = CascadeType.REMOVE)
    @JoinColumn(name = "target_client_id", referencedColumnName = "client_id", insertable = false, updatable = false)
    @NotAudited
    private List<NonMultitenancyClientRole> targetClientRoles = new ArrayList<>();

    @OneToMany(fetch = FetchType.LAZY, cascade = CascadeType.REMOVE)
    @JoinColumn(name = "client_id", referencedColumnName = "client_id", insertable = false, updatable = false)
    @NotAudited
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

    public List<NonMultitenancyAccountRole> getAccountRoles() {
        return accountRoles;
    }

    public void setAccountRoles(List<NonMultitenancyAccountRole> accountRoles) {
        this.accountRoles = accountRoles;
    }

    public List<NonMultitenancyClientSecret> getClientSecrets() {
        return clientSecrets;
    }

    public void setClientSecrets(List<NonMultitenancyClientSecret> clientSecrets) {
        this.clientSecrets = clientSecrets;
    }

    public List<NonMultitenancyClientAllowedRole> getClientAllowedRoles() {
        return clientAllowedRoles;
    }

    public void setClientAllowedRoles(List<NonMultitenancyClientAllowedRole> clientAllowedRoles) {
        this.clientAllowedRoles = clientAllowedRoles;
    }

    public List<NonMultitenancyClientRole> getSrcClientRoles() {
        return srcClientRoles;
    }

    public void setSrcClientRoles(List<NonMultitenancyClientRole> srcClientRoles) {
        this.srcClientRoles = srcClientRoles;
    }

    public List<NonMultitenancyClientRole> getTargetClientRoles() {
        return targetClientRoles;
    }

    public void setTargetClientRoles(List<NonMultitenancyClientRole> targetClientRoles) {
        this.targetClientRoles = targetClientRoles;
    }

    public List<NonMultitenancySubscription> getSubscriptions() {
        return subscriptions;
    }

    public void setSubscriptions(List<NonMultitenancySubscription> subscriptions) {
        this.subscriptions = subscriptions;
    }
}
