package dev.abstratium.abstrauth.service;

import io.quarkus.oidc.IdToken;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.container.ContainerRequestContext;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.jboss.logging.Logger;

import dev.abstratium.abstrauth.util.ClientIpUtil;

@ApplicationScoped
public class SecurityProblemLogger {

    private static final Logger log = Logger.getLogger(SecurityProblemLogger.class); 


    @Inject
    ContainerRequestContext requestContext;

    @Inject
    @IdToken
    JsonWebToken idToken;

    public void warnf(String format, Object... args) {

        String path = requestContext.getUriInfo().getPath();
        String method = requestContext.getMethod();

        String formatted = String.format(format, args);
        String msg = String.format(
            "SECURITY-PROBLEM for method '%s' on path '%s' by account '%s' with email '%s' in org '%s' and IP '%s'. Problem is: %s",
            method,
            path,
            idToken.getSubject(),
            idToken.getClaim("upn"),
            idToken.getClaim("orgId"),
            ClientIpUtil.getClientIp(requestContext),
            formatted
        );

        log.warn(msg);
    }
}
