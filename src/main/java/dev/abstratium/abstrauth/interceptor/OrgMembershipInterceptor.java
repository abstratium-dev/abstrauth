package dev.abstratium.abstrauth.interceptor;

import org.eclipse.microprofile.jwt.JsonWebToken;
import org.jboss.logging.Logger;

import dev.abstratium.abstrauth.service.OrganisationService;
import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.interceptor.AroundInvoke;
import jakarta.interceptor.Interceptor;
import jakarta.interceptor.InvocationContext;
import jakarta.ws.rs.ForbiddenException;

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

    private static final Logger log = Logger.getLogger(OrgMembershipInterceptor.class);

    @Inject
    JsonWebToken token;

    @Inject
    OrganisationService organisationService;

    @AroundInvoke
    public Object verifyMembership(InvocationContext context) throws Exception {
        String accountId = token.getSubject();
        String orgId = token.getClaim("orgId");

        log.debugv("Interceptor: accountId={0} orgId={1}", accountId, orgId);

        // Unauthenticated request - let @RolesAllowed handle it
        if (accountId == null) {
            log.infov("Interceptor: no accountId, proceeding");
            return context.proceed();
        }

        // Authenticated request to a tenant-scoped endpoint must have an orgId
        if (orgId == null) {
            log.infov("Interceptor: missing orgId, rejecting");
            throw new ForbiddenException("orgId claim is required");
        }

        boolean isMember = organisationService.isMember(orgId, accountId);
        log.infov("Interceptor: isMember={0} for orgId={1} accountId={2}", isMember, orgId, accountId);

        // Verify the account is a member of the claimed organization
        if (!isMember) {
            // Throw 403 Forbidden - account is not authorized for this org
            throw new ForbiddenException("Account is not a member of the specified organization");
        }

        return context.proceed();
    }
}
