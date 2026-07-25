package dev.abstratium.abstrauth.filter;

import dev.abstratium.abstrauth.service.CurrentOrgContext;
import dev.abstratium.abstrauth.service.JwtOrgResolver;
import io.quarkus.hibernate.orm.PersistenceUnitExtension;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Path("/api/test")
public class OrgIdResolutionTestResource {

    @Inject
    CurrentOrgContext currentOrgContext;

    @Inject
    @PersistenceUnitExtension
    JwtOrgResolver jwtOrgResolver;

    @GET
    @Path("/org-id")
    @Produces(MediaType.TEXT_PLAIN)
    public String orgId() {
        return String.valueOf(currentOrgContext.getOrgId());
    }

    @GET
    @Path("/jwt-org")
    @Produces(MediaType.APPLICATION_JSON)
    public String jwtOrg() {
        return "\"" + jwtOrgResolver.resolveTenantId() + "\"";
    }
}
