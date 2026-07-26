package dev.abstratium.abstrauth.non_multitenancy.service;

import java.util.Optional;

import dev.abstratium.abstrauth.entity.Account;
import dev.abstratium.abstrauth.entity.FederatedIdentity;
import dev.abstratium.abstrauth.service.AccountService;
import dev.abstratium.abstrauth.service.FederatedIdentityService;
import dev.abstratium.abstrauth.service.MicrosoftOAuthService;
import dev.abstratium.abstrauth.service.oauth.MicrosoftUserInfo;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

/**
 * Handles the Microsoft OAuth callback flow in a non-multitenancy context.
 *
 * This service uses {@link NonMultitenancyAuthorizationService} to decide whether
 * sign-up is allowed, because that decision must be made before the tenant
 * context is fully established during federated account creation.
 */
@ApplicationScoped
public class NonMultitenancyMicrosoftOAuthService {

    @Inject
    MicrosoftOAuthService microsoftOAuthService;

    @Inject
    AccountService accountService;

    @Inject
    FederatedIdentityService federatedIdentityService;

    @Inject
    NonMultitenancyAuthorizationService nonMultitenancyAuthorizationService;

    /**
     * Exchange a Microsoft authorization code for user info and create or link the account.
     *
     * @param code the authorization code returned by Microsoft
     * @return the linked or newly created account
     */
    @Transactional
    public Account handleMicrosoftCallback(String code) {
        MicrosoftOAuthService.MicrosoftAuthenticationResult result = microsoftOAuthService.getUserInfo(code);
        MicrosoftUserInfo userInfo = result.userInfo();
        Boolean emailVerifiedFromToken = result.emailVerifiedFromToken();

        // For Microsoft, default email_verified to true if not in ID token (trusted identity provider)
        Boolean emailVerified = emailVerifiedFromToken != null ? emailVerifiedFromToken : true;

        // Check if an account with this email already exists
        Optional<Account> existingAccount = accountService.findByEmail(userInfo.getEmail());

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
            if (!nonMultitenancyAuthorizationService.isSignupAllowed()) {
                throw new IllegalStateException("Signup is disabled");
            }

            // Create a new account for this Microsoft user
            account = accountService.createAccountFromFederatedProvider(
                    userInfo.getEmail(),
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
                    userInfo.getEmail()
            );
        }

        return account;
    }
}
