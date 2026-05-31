package dev.abstratium.abstrauth.interceptor;

import org.eclipse.microprofile.jwt.JsonWebToken;

import dev.abstratium.abstrauth.service.OrganisationService;
import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.interceptor.AroundInvoke;
import jakarta.interceptor.Interceptor;
import jakarta.interceptor.InvocationContext;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;

/**
 * Interceptor that verifies the authenticated account is a member of the
 * organization specified in the JWT orgId claim.
 * 
 * This prevents security vulnerabilities where a forged JWT with an arbitrary
 * orgId claim could access data from organizations where the account is not
 * a member.
 */
@Interceptor
@VerifyOrgMembership
@Priority(0)
public class OrgMembershipInterceptor {

    @Inject
    JsonWebToken token;

    @Inject
    OrganisationService organisationService;

    @AroundInvoke
    public Object verifyMembership(InvocationContext context) throws Exception {
        String accountId = token.getSubject();
        String orgId = token.getClaim("orgId");

        // If either is missing, we can't verify - let the request proceed
        // and let the resource handle the missing data
        if (accountId == null || orgId == null) {
            return context.proceed();
        }

        // Verify the account is a member of the claimed organization
        if (!organisationService.isMember(orgId, accountId)) {
            // Return 403 Forbidden - account is not authorized for this org
            return Response.status(Status.FORBIDDEN)
                    .entity("Account is not a member of the specified organization")
                    .build();
        }

        return context.proceed();
    }
}
