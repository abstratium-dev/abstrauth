package dev.abstratium.abstrauth.non_multitenancy.service;

import java.util.Optional;

import dev.abstratium.abstrauth.entity.Account;
import dev.abstratium.abstrauth.entity.FederatedIdentity;
import dev.abstratium.abstrauth.service.AccountService;
import dev.abstratium.abstrauth.service.FederatedIdentityService;
import dev.abstratium.abstrauth.service.GoogleOAuthService;
import dev.abstratium.abstrauth.service.oauth.GoogleUserInfo;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

/**
 * Handles the Google OAuth callback flow in a non-multitenancy context.
 *
 * This service uses {@link NonMultitenancyAuthorizationService} to decide whether
 * sign-up is allowed, because that decision must be made before the tenant
 * context is fully established during federated account creation.
 */
@ApplicationScoped
public class NonMultitenancyGoogleOAuthService {

    @Inject
    GoogleOAuthService googleOAuthService;

    @Inject
    AccountService accountService;

    @Inject
    FederatedIdentityService federatedIdentityService;

    @Inject
    NonMultitenancyAuthorizationService nonMultitenancyAuthorizationService;

    /**
     * Exchange a Google authorization code for user info and create or link the account.
     *
     * @param code the authorization code returned by Google
     * @return the linked or newly created account
     */
    @Transactional
    public Account handleGoogleCallback(String code) {
        GoogleOAuthService.GoogleAuthenticationResult result = googleOAuthService.getUserInfo(code);
        GoogleUserInfo userInfo = result.userInfo();
        Boolean emailVerifiedFromToken = result.emailVerifiedFromToken();

        // Check if an account with this email already exists
        Optional<Account> existingAccount = accountService.findByEmail(userInfo.getEmail());

        Account account;
        if (existingAccount.isPresent()) {
            // Link the Google identity to the existing account
            account = existingAccount.get();

            // Update account info from Google, but never overwrite a non-blank name with a blank one
            if (userInfo.getName() != null && !userInfo.getName().isBlank()) {
                account.setName(userInfo.getName());
            }
            account.setPicture(googleOAuthService.convertToProxyUrl(userInfo.getPicture()));
            // Use email_verified from ID token if available, otherwise fall back to userinfo
            Boolean emailVerified = emailVerifiedFromToken != null ? emailVerifiedFromToken : userInfo.getEmailVerified();
            if (Boolean.FALSE.equals(account.getEmailVerified()) && Boolean.TRUE.equals(emailVerified)) {
                account.setEmailVerified(true);
            }
            accountService.updateAccount(account);
        } else {
            // Check if signup is allowed before creating a new account
            if (!nonMultitenancyAuthorizationService.isSignupAllowed()) {
                throw new IllegalStateException("Signup is disabled");
            }

            // Use email_verified from ID token if available, otherwise fall back to userinfo
            Boolean emailVerified = emailVerifiedFromToken != null ? emailVerifiedFromToken : userInfo.getEmailVerified();

            // Create a new account for this Google user
            account = accountService.createAccountFromFederatedProvider(
                    userInfo.getEmail(),
                    userInfo.getName(),
                    googleOAuthService.convertToProxyUrl(userInfo.getPicture()),
                    emailVerified != null ? emailVerified : false,
                    AccountService.GOOGLE
            );
        }

        // Check if this Google account is already linked
        Optional<FederatedIdentity> existingIdentity =
                federatedIdentityService.findByProviderAndUserId(AccountService.GOOGLE, userInfo.getSub());

        if (!existingIdentity.isPresent()) {
            // Create the federated identity link
            federatedIdentityService.createFederatedIdentity(
                    account.getId(),
                    AccountService.GOOGLE,
                    userInfo.getSub(),
                    userInfo.getEmail()
            );
        }

        return account;
    }
}
