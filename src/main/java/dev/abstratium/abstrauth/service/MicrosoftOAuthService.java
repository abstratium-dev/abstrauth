package dev.abstratium.abstrauth.service;

import dev.abstratium.abstrauth.entity.Account;
import dev.abstratium.abstrauth.entity.FederatedIdentity;
import dev.abstratium.abstrauth.service.oauth.MicrosoftGraphClient;
import dev.abstratium.abstrauth.service.oauth.MicrosoftOAuthClient;
import dev.abstratium.abstrauth.service.oauth.MicrosoftTokenResponse;
import dev.abstratium.abstrauth.service.oauth.MicrosoftUserInfo;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import java.util.Optional;

@ApplicationScoped
public class MicrosoftOAuthService {

    @Inject
    @RestClient
    MicrosoftOAuthClient microsoftClient;

    @Inject
    @RestClient
    MicrosoftGraphClient microsoftGraphClient;

    @Inject
    AccountService accountService;

    @Inject
    FederatedIdentityService federatedIdentityService;

    @Inject
    AuthorizationService authorizationService;

    @ConfigProperty(name = "oauth.microsoft.client-id")
    String clientId;

    @ConfigProperty(name = "oauth.microsoft.client-secret")
    String clientSecret;

    @ConfigProperty(name = "oauth.microsoft.redirect-uri")
    String redirectUri;

    /**
     * Exchange Microsoft authorization code for tokens and user info
     * Creates or links account based on Microsoft user info
     *
     * @param code Authorization code from Microsoft
     * @return The account linked to this Microsoft identity
     */
    @Transactional
    public Account handleMicrosoftCallback(String code) {
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
        String email = userInfo.getEmail();
        if (email == null || email.isBlank()) {
            throw new IllegalStateException("Email is missing from Microsoft Graph API response");
        }

        // For Microsoft, default email_verified to true if not in ID token (trusted identity provider)
        Boolean emailVerified = emailVerifiedFromToken != null ? emailVerifiedFromToken : true;

        // Check if an account with this email already exists
        Optional<Account> existingAccount = accountService.findByEmail(email);

        Account account;
        if (existingAccount.isPresent()) {
            // Link the Microsoft identity to the existing account
            account = existingAccount.get();

            // Update account info from Microsoft, but never overwrite a non-blank name with a blank one
            if (userInfo.getName() != null && !userInfo.getName().isBlank()) {
                account.setName(userInfo.getName());
            }
            // Microsoft Graph does not return a picture URL from /me; photo requires a separate API call
            account.setPicture(null);
            if (Boolean.FALSE.equals(account.getEmailVerified()) && Boolean.TRUE.equals(emailVerified)) {
                account.setEmailVerified(true);
            }
            accountService.updateAccount(account);
        } else {
            // Check if signup is allowed before creating a new account
            if (!authorizationService.isSignupAllowed()) {
                throw new IllegalStateException("Signup is disabled");
            }

            // Create a new account for this Microsoft user
            account = accountService.createFederatedAccount(
                    email,
                    userInfo.getName(),
                    null, // No picture support for Microsoft in this version
                    emailVerified,
                    AccountService.MICROSOFT
            );
        }

        // Check if this Microsoft account is already linked
        Optional<FederatedIdentity> existingIdentity =
                federatedIdentityService.findByProviderAndUserId(AccountService.MICROSOFT, userInfo.getId());

        if (existingIdentity.isEmpty()) {
            // Create the federated identity link
            federatedIdentityService.createFederatedIdentity(
                    account.getId(),
                    AccountService.MICROSOFT,
                    userInfo.getId(),
                    email
            );
        }

        return account;
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
