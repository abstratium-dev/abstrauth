package dev.abstratium.abstrauth.service.oauth;

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

/**
 * REST client for Google OAuth 2.0 APIs
 */
@RegisterRestClient(configKey = "google-oauth")
public interface GoogleOAuthClient {

    @POST
    @Path("/oauth2/v4/token")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Produces(MediaType.APPLICATION_JSON)
    GoogleTokenResponse exchangeCodeForToken(
            @FormParam("code") String code,
            @FormParam("client_id") String clientId,
            @FormParam("client_secret") String clientSecret,
            @FormParam("redirect_uri") String redirectUri,
            @FormParam("grant_type") String grantType
    );

    @GET
    @Path("/oauth2/v1/userinfo")
    @Produces(MediaType.APPLICATION_JSON)
    GoogleUserInfo getUserInfo(@HeaderParam("Authorization") String bearerToken);
}
