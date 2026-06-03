package dev.abstratium.abstrauth;

import io.quarkus.oidc.IdToken;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.RequestScoped;
import jakarta.enterprise.inject.Alternative;
import jakarta.enterprise.inject.Produces;
import jakarta.enterprise.inject.spi.InjectionPoint;
import jakarta.interceptor.Interceptor;
import org.eclipse.microprofile.jwt.JsonWebToken;

import java.security.Principal;

/**
 * Test-only producer that makes {@code @IdToken JsonWebToken} resolvable
 * when {@code quarkus.oidc.enabled=false} in the test profile.
 *
 * <p>Quarkus OIDC normally provides the ID token via {@code @IdToken},
 * but that producer is absent when OIDC is disabled.  This alternative
 * simply maps the qualifier to the current MP-JWT principal so that
 * resources such as {@code AccountsResource} and {@code ClientsResource}
 * can still be deployed during {@code @QuarkusTest} runs.</p>
 */
@RequestScoped
public class TestIdTokenProducer {

    @Produces
    @IdToken
    @Alternative
    @Priority(Interceptor.Priority.APPLICATION + 100)
    public JsonWebToken produceIdToken(InjectionPoint ip, Principal principal) {
        if (principal instanceof JsonWebToken) {
            return (JsonWebToken) principal;
        }
        return null;
    }
}
