package dev.abstratium.abstrauth.service;

import dev.abstratium.abstrauth.entity.Account;
import dev.abstratium.abstrauth.entity.FederatedIdentity;
import dev.abstratium.abstrauth.service.oauth.GoogleOAuthClient;
import dev.abstratium.abstrauth.service.oauth.GoogleTokenResponse;
import dev.abstratium.abstrauth.service.oauth.GoogleUserInfo;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import java.util.Optional;

@ApplicationScoped
public class GoogleOAuthService {

    @Inject
    @RestClient
    GoogleOAuthClient googleClient;

    @Inject
    AccountService accountService;

    @Inject
    FederatedIdentityService federatedIdentityService;

    @Inject
    AuthorizationService authorizationService;

    @ConfigProperty(name = "oauth.google.client-id")
    String clientId;

    @ConfigProperty(name = "oauth.google.client-secret")
    String clientSecret;

    @ConfigProperty(name = "oauth.google.redirect-uri")
    String redirectUri;

    /**
     * Exchange Google authorization code for tokens and user info
     * Creates or links account based on Google user info
     * 
     * @param code Authorization code from Google
     * @return The account linked to this Google identity
     */
    @Transactional
    public Account handleGoogleCallback(String code) {
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

        // Validate required fields from Google
        if (userInfo.getSub() == null || userInfo.getSub().isBlank()) {
            throw new IllegalStateException("Google user ID (sub) is missing from userinfo response");
        }
        if (userInfo.getEmail() == null || userInfo.getEmail().isBlank()) {
            throw new IllegalStateException("Email is missing from Google userinfo response");
        }

        // Check if this Google account is already linked
        Optional<FederatedIdentity> existingIdentity = 
                federatedIdentityService.findByProviderAndUserId("google", userInfo.getSub());

        if (existingIdentity.isPresent()) {
            // User already exists, return the linked account
            String accountId = existingIdentity.get().getAccountId();
            return accountService.findById(accountId)
                    .orElseThrow(() -> new IllegalStateException("Linked account not found"));
        }

        // Check if an account with this email already exists
        Optional<Account> existingAccount = accountService.findByEmail(userInfo.getEmail());

        Account account;
        if (existingAccount.isPresent()) {
            // Link the Google identity to the existing account
            account = existingAccount.get();
            
            // Update account info from Google if not set
            if (account.getName() == null || account.getName().isBlank()) {
                account.setName(userInfo.getName());
            }
            if (account.getPicture() == null || account.getPicture().isBlank()) {
                account.setPicture(userInfo.getPicture());
            }
            if (Boolean.FALSE.equals(account.getEmailVerified()) && Boolean.TRUE.equals(userInfo.getEmailVerified())) {
                account.setEmailVerified(true);
            }
            accountService.updateAccount(account);
        } else {
            // Check if signup is allowed before creating a new account
            if (!authorizationService.isSignupAllowed()) {
                throw new IllegalStateException("Signup is disabled");
            }
            
            // Create a new account for this Google user
            account = accountService.createFederatedAccount(
                    userInfo.getEmail(),
                    userInfo.getName(),
                    userInfo.getPicture(),
                    userInfo.getEmailVerified(),
                    "google"
            );
        }

        // Create the federated identity link
        federatedIdentityService.createFederatedIdentity(
                account.getId(),
                "google",
                userInfo.getSub(),
                userInfo.getEmail()
        );

        return account;
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
