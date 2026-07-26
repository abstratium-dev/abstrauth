package dev.abstratium.abstrauth.service;

import dev.abstratium.abstrauth.service.oauth.MicrosoftGraphClient;
import dev.abstratium.abstrauth.service.oauth.MicrosoftOAuthClient;
import dev.abstratium.abstrauth.service.oauth.MicrosoftTokenResponse;
import dev.abstratium.abstrauth.service.oauth.MicrosoftUserInfo;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.inject.RestClient;

@ApplicationScoped
public class MicrosoftOAuthService {

    @Inject
    @RestClient
    MicrosoftOAuthClient microsoftClient;

    @Inject
    @RestClient
    MicrosoftGraphClient microsoftGraphClient;


    @ConfigProperty(name = "oauth.microsoft.client-id")
    String clientId;

    @ConfigProperty(name = "oauth.microsoft.client-secret")
    String clientSecret;

    @ConfigProperty(name = "oauth.microsoft.redirect-uri")
    String redirectUri;

    /**
     * Result of exchanging a Microsoft authorization code for user information.
     */
    public record MicrosoftAuthenticationResult(MicrosoftUserInfo userInfo, Boolean emailVerifiedFromToken) {
    }

    /**
     * Exchange Microsoft authorization code for tokens and user info.
     *
     * @param code Authorization code from Microsoft
     * @return The user info and email_verified flag parsed from the ID token
     */
    public MicrosoftAuthenticationResult getUserInfo(String code) {
        // Exchange code for tokens
        MicrosoftTokenResponse tokenResponse = microsoftClient.exchangeCodeForToken(
                code,
                clientId,
                clientSecret,
                redirectUri,
                "authorization_code"
        );

        // Get user info from Microsoft Graph
        MicrosoftUserInfo userInfo = microsoftGraphClient.getUserInfo("Bearer " + tokenResponse.getAccessToken());

        // Parse email_verified from ID token if available
        Boolean emailVerifiedFromToken = parseEmailVerifiedFromIdToken(tokenResponse.getIdToken());

        // Validate required fields from Microsoft
        if (userInfo.getId() == null || userInfo.getId().isBlank()) {
            throw new IllegalStateException("Microsoft user ID is missing from Graph API response");
        }
        if (userInfo.getEmail() == null || userInfo.getEmail().isBlank()) {
            throw new IllegalStateException("Email is missing from Microsoft Graph API response");
        }

        return new MicrosoftAuthenticationResult(userInfo, emailVerifiedFromToken);
    }

    /**
     * Parse the ID token to extract claims
     * Microsoft ID tokens are JWTs that contain user information
     *
     * @param idToken The ID token from Microsoft
     * @return Boolean value of email_verified claim, or null if not present
     */
    private Boolean parseEmailVerifiedFromIdToken(String idToken) {
        if (idToken == null || idToken.isBlank()) {
            return null;
        }

        try {
            // ID token is a JWT with 3 parts: header.payload.signature
            // We only need the payload (middle part)
            String[] parts = idToken.split("\\.");
            if (parts.length != 3) {
                return null;
            }

            // Decode the payload (base64url encoded)
            String payload = new String(java.util.Base64.getUrlDecoder().decode(parts[1]));

            // Parse JSON to extract email_verified claim
            if (payload.contains("\"email_verified\"")) {
                int startIndex = payload.indexOf("\"email_verified\"");
                int colonIndex = payload.indexOf(":", startIndex);
                int commaIndex = payload.indexOf(",", colonIndex);
                int braceIndex = payload.indexOf("}", colonIndex);

                int endIndex = commaIndex > 0 && commaIndex < braceIndex ? commaIndex : braceIndex;
                String value = payload.substring(colonIndex + 1, endIndex).trim();

                return Boolean.parseBoolean(value);
            }

            return null;
        } catch (Exception e) {
            // If parsing fails, return null
            return null;
        }
    }

    /**
     * Generate the Microsoft OAuth authorization URL
     */
    public String getAuthorizationUrl(String state) {
        return "https://login.microsoftonline.com/common/oauth2/v2.0/authorize" +
                "?client_id=" + clientId +
                "&redirect_uri=" + redirectUri +
                "&response_type=code" +
                "&scope=openid%20email%20profile%20User.Read" +
                "&state=" + state;
    }
}
