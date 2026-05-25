package dev.abstratium.abstrauth.service;

import io.quarkus.hibernate.orm.PersistenceUnitExtension;
import io.quarkus.hibernate.orm.runtime.tenant.TenantResolver;
import jakarta.enterprise.context.RequestScoped;

/**
 * Tenant resolver for discriminator-based multitenancy.
 * Returns the hard-coded default org ID for all requests in Feature 4.
 * Will be updated in Feature 10 to extract orgId from JWT.
 */
@PersistenceUnitExtension
@RequestScoped
public class JwtOrgResolver implements TenantResolver {

    // Default organisation ID from V01.021__migrate_existing_data_to_default_org.sql
    public static final String DEFAULT_ORG_ID = "00000000-0000-0000-0000-000000000000";

    @Override
    public String getDefaultTenantId() {
        return DEFAULT_ORG_ID;
    }

    @Override
    public String resolveTenantId() {
        // For now, return the hard-coded default tenant ID
        // This will be updated in Feature 10 to extract from JWT
        return DEFAULT_ORG_ID;
    }
}
