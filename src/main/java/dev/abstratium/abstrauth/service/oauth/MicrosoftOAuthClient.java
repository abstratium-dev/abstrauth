package dev.abstratium.abstrauth.service.oauth;

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

/**
 * REST client for Microsoft OAuth 2.0 token endpoint
 */
@RegisterRestClient(configKey = "microsoft-oauth")
public interface MicrosoftOAuthClient {

    @POST
    @Path("/oauth2/v2.0/token")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Produces(MediaType.APPLICATION_JSON)
    MicrosoftTokenResponse exchangeCodeForToken(
            @FormParam("code") String code,
            @FormParam("client_id") String clientId,
            @FormParam("client_secret") String clientSecret,
            @FormParam("redirect_uri") String redirectUri,
            @FormParam("grant_type") String grantType
    );
}
