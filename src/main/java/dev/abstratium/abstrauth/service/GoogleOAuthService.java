package dev.abstratium.abstrauth.service;

import dev.abstratium.abstrauth.service.oauth.GoogleOAuthClient;
import dev.abstratium.abstrauth.service.oauth.GoogleTokenResponse;
import dev.abstratium.abstrauth.service.oauth.GoogleUserInfo;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.inject.RestClient;

@ApplicationScoped
public class GoogleOAuthService {

    @Inject
    @RestClient
    GoogleOAuthClient googleClient;



    @ConfigProperty(name = "oauth.google.client-id")
    String clientId;

    @ConfigProperty(name = "oauth.google.client-secret")
    String clientSecret;

    @ConfigProperty(name = "oauth.google.redirect-uri")
    String redirectUri;

    /**
     * Result of exchanging a Google authorization code for user information.
     */
    public record GoogleAuthenticationResult(GoogleUserInfo userInfo, Boolean emailVerifiedFromToken) {
    }

    /**
     * Exchange Google authorization code for tokens and user info.
     *
     * @param code Authorization code from Google
     * @return The user info and email_verified flag parsed from the ID token
     */
    public GoogleAuthenticationResult getUserInfo(String code) {
        // Exchange code for tokens
        GoogleTokenResponse tokenResponse = googleClient.exchangeCodeForToken(
                code,
                clientId,
                clientSecret,
                redirectUri,
                "authorization_code"
        );

        // Get user info from Google
        GoogleUserInfo userInfo = googleClient.getUserInfo("Bearer " + tokenResponse.getAccessToken());

        // Extract email_verified from ID token (more reliable than userinfo endpoint)
        Boolean emailVerifiedFromToken = parseEmailVerifiedFromIdToken(tokenResponse.getIdToken());

        // Validate required fields from Google
        if (userInfo.getSub() == null || userInfo.getSub().isBlank()) {
            throw new IllegalStateException("Google user ID (sub) is missing from userinfo response");
        }
        if (userInfo.getEmail() == null || userInfo.getEmail().isBlank()) {
            throw new IllegalStateException("Email is missing from Google userinfo response");
        }

        return new GoogleAuthenticationResult(userInfo, emailVerifiedFromToken);
    }

    /**
     * Convert Google profile picture URL to use our proxy to avoid rate limiting
     */
    public String convertToProxyUrl(String googlePictureUrl) {
        if (googlePictureUrl == null || googlePictureUrl.isBlank()) {
            return null;
        }
        
        // Extract the image ID from Google's URL
        // Format: https://lh3.googleusercontent.com/a/ACg8ocKy8J07hRZZLnri1836Ze4_wd96YdPHERLsBiAJsbeYXm8WOA=s96-c
        if (googlePictureUrl.contains("googleusercontent.com/")) {
            int startIndex = googlePictureUrl.indexOf("googleusercontent.com/") + "googleusercontent.com/".length();
            String imageId = googlePictureUrl.substring(startIndex);
            // Remove size parameter if present (e.g., =s96-c)
            if (imageId.contains("=")) {
                imageId = imageId.substring(0, imageId.indexOf("="));
            }
            return "/api/profile-picture/google/" + imageId;
        }
        
        return googlePictureUrl;
    }

    /**
     * Parse the ID token to extract claims
     * Google ID tokens are JWTs that contain user information
     * 
     * @param idToken The ID token from Google
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
            // Simple parsing since we only need one field
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
     * Generate the Google OAuth authorization URL
     */
    public String getAuthorizationUrl(String state) {
        return "https://accounts.google.com/o/oauth2/v2/auth" +
                "?client_id=" + clientId +
                "&redirect_uri=" + redirectUri +
                "&response_type=code" +
                "&scope=openid%20email%20profile" +
                "&state=" + state;
    }
}
