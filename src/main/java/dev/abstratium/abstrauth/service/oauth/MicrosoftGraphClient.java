package dev.abstratium.abstrauth.service.oauth;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

/**
 * REST client for Microsoft Graph API
 */
@RegisterRestClient(configKey = "microsoft-graph")
public interface MicrosoftGraphClient {

    @GET
    @Path("/me")
    @Produces(MediaType.APPLICATION_JSON)
    MicrosoftUserInfo getUserInfo(@HeaderParam("Authorization") String bearerToken);
}
