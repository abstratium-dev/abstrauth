package dev.abstratium.abstrauth.service;

import jakarta.enterprise.context.RequestScoped;

/**
 * Request-scoped holder for the resolved organisation ID (orgId).
 * Populated by {@link dev.abstratium.abstrauth.filter.OrgIdResolutionFilter}
 * after the security layer has extracted JWT tokens (from OIDC cookies or
 * Bearer headers), and consumed by {@link JwtOrgResolver} to determine the
 * Hibernate discriminator tenant.
 */
@RequestScoped
public class CurrentOrgContext {

    private String orgId;

    public String getOrgId() {
        return orgId;
    }

    public void setOrgId(String orgId) {
        this.orgId = orgId;
    }
}
