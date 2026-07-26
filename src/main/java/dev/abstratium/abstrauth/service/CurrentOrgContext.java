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
    private String requestPath;
    private String requestMethod;
    private String contextDescription;
    private boolean ignore;

    public String getOrgId() {
        return orgId;
    }

    public void setOrgId(String orgId) {
        this.orgId = orgId;
    }

    public String getRequestPath() {
        return requestPath;
    }

    public void setRequestPath(String requestPath) {
        this.requestPath = requestPath;
    }

    public String getRequestMethod() {
        return requestMethod;
    }

    public void setRequestMethod(String requestMethod) {
        this.requestMethod = requestMethod;
    }

    public String getContextDescription() {
        return contextDescription;
    }

    public void setContextDescription(String contextDescription) {
        this.contextDescription = contextDescription;
    }

    public boolean isIgnore() {
        return ignore;
    }

    public void setIgnore(boolean ignore) {
        this.ignore = ignore;
    }

}
