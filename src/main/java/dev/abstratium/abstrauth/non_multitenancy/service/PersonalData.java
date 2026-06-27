package dev.abstratium.abstrauth.non_multitenancy.service;

import java.util.List;

import io.quarkus.runtime.annotations.RegisterForReflection;

/**
 * Personal data package for the right of access (GDPR Art. 15 / revDSG).
 * Contains all information the system holds about a data subject.
 */
@RegisterForReflection
public class PersonalData {

    public AccountInfo account;
    public List<CredentialInfo> credentials;
    public List<FederatedIdentityInfo> federatedIdentities;
    public List<OrganisationMembershipInfo> organisationMemberships;
    public List<RoleInfo> roles;
    public String exportTimestamp;

    @RegisterForReflection
    public static class AccountInfo {
        public String id;
        public String email;
        public String name;
        public Boolean emailVerified;
        public String authProvider;
        public String picture;
        public String createdAt;

        public AccountInfo(String id, String email, String name, Boolean emailVerified,
                           String authProvider, String picture, String createdAt) {
            this.id = id;
            this.email = email;
            this.name = name;
            this.emailVerified = emailVerified;
            this.authProvider = authProvider;
            this.picture = picture;
            this.createdAt = createdAt;
        }
    }

    @RegisterForReflection
    public static class CredentialInfo {
        public String id;
        public String username;
        public Integer failedLoginAttempts;
        public String lockedUntil;
        public String createdAt;

        public CredentialInfo(String id, String username, Integer failedLoginAttempts,
                              String lockedUntil, String createdAt) {
            this.id = id;
            this.username = username;
            this.failedLoginAttempts = failedLoginAttempts;
            this.lockedUntil = lockedUntil;
            this.createdAt = createdAt;
        }
    }

    @RegisterForReflection
    public static class FederatedIdentityInfo {
        public String id;
        public String provider;
        public String providerUserId;
        public String email;
        public String connectedAt;

        public FederatedIdentityInfo(String id, String provider, String providerUserId,
                                     String email, String connectedAt) {
            this.id = id;
            this.provider = provider;
            this.providerUserId = providerUserId;
            this.email = email;
            this.connectedAt = connectedAt;
        }
    }

    @RegisterForReflection
    public static class OrganisationMembershipInfo {
        public String orgId;
        public String organisationName;
        public String role;
        public String addedAt;

        public OrganisationMembershipInfo(String orgId, String organisationName, String role, String addedAt) {
            this.orgId = orgId;
            this.organisationName = organisationName;
            this.role = role;
            this.addedAt = addedAt;
        }
    }

    @RegisterForReflection
    public static class RoleInfo {
        public String id;
        public String clientId;
        public String role;
        public String orgId;
        public String createdAt;

        public RoleInfo(String id, String clientId, String role, String orgId, String createdAt) {
            this.id = id;
            this.clientId = clientId;
            this.role = role;
            this.orgId = orgId;
            this.createdAt = createdAt;
        }
    }

    public PersonalData() {
    }

    public PersonalData(AccountInfo account, List<CredentialInfo> credentials,
                        List<FederatedIdentityInfo> federatedIdentities,
                        List<OrganisationMembershipInfo> organisationMemberships,
                        List<RoleInfo> roles, String exportTimestamp) {
        this.account = account;
        this.credentials = credentials;
        this.federatedIdentities = federatedIdentities;
        this.organisationMemberships = organisationMemberships;
        this.roles = roles;
        this.exportTimestamp = exportTimestamp;
    }
}
