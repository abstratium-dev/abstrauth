package dev.abstratium.abstrauth.boundary;

import dev.abstratium.abstrauth.service.Roles;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * Test-only endpoint that requires Roles.ADMIN but does NOT use @VerifyOrgMembership.
 * This is used to test that JWT audience verification alone blocks forged tokens
 * from the client ID collision attack, without the defense-in-depth org membership
 * interceptor masking the vulnerability.
 */
@Path("/api/test/admin-only")
public class AdminOnlyTestResource {

    @GET
    @Produces(MediaType.TEXT_PLAIN)
    @RolesAllowed(Roles.ADMIN)
    public Response adminOnly() {
        return Response.ok("admin-access-granted").build();
    }
}
