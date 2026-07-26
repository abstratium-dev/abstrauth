package dev.abstratium.abstrauth.non_multitenancy.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import dev.abstratium.abstrauth.entity.Account;
import dev.abstratium.abstrauth.entity.FederatedIdentity;
import dev.abstratium.abstrauth.service.AccountService;
import dev.abstratium.abstrauth.service.FederatedIdentityService;
import dev.abstratium.abstrauth.service.GoogleOAuthService;
import dev.abstratium.abstrauth.service.oauth.GoogleUserInfo;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;

@QuarkusTest
public class NonMultitenancyGoogleOAuthServiceTest {

    @Inject
    NonMultitenancyGoogleOAuthService service;

    @InjectMock
    GoogleOAuthService googleOAuthService;

    @InjectMock
    AccountService accountService;

    @InjectMock
    FederatedIdentityService federatedIdentityService;

    @InjectMock
    NonMultitenancyAuthorizationService authorizationService;

    private static final String GOOGLE_USER_ID = "google-user-123";
    private static final String EMAIL = "testuser@gmail.com";
    private static final String PROXY_PICTURE = "/api/profile-picture/google/abc";

    @Test
    public void testHandleGoogleCallback_existingAccount_updatesAndLinksIdentity() {
        GoogleUserInfo userInfo = new GoogleUserInfo();
        userInfo.setSub(GOOGLE_USER_ID);
        userInfo.setEmail(EMAIL);
        userInfo.setName("Google User");
        userInfo.setPicture("https://lh3.googleusercontent.com/a/abc");
        userInfo.setEmailVerified(true);

        when(googleOAuthService.getUserInfo("code"))
                .thenReturn(new GoogleOAuthService.GoogleAuthenticationResult(userInfo, true));
        when(googleOAuthService.convertToProxyUrl(userInfo.getPicture())).thenReturn(PROXY_PICTURE);

        Account existing = new Account();
        existing.setId("acc-1");
        existing.setEmail(EMAIL);
        existing.setName("Old Name");
        existing.setEmailVerified(false);
        existing.setPicture(null);

        when(accountService.findByEmail(EMAIL)).thenReturn(Optional.of(existing));
        when(accountService.updateAccount(existing)).thenReturn(existing);
        when(federatedIdentityService.findByProviderAndUserId(AccountService.GOOGLE, GOOGLE_USER_ID))
                .thenReturn(Optional.empty());

        Account result = service.handleGoogleCallback("code");

        assertEquals("acc-1", result.getId());
        assertEquals("Google User", result.getName());
        assertEquals(PROXY_PICTURE, result.getPicture());
        assertTrue(result.getEmailVerified());

        verify(accountService).updateAccount(existing);

        ArgumentCaptor<String> accountIdCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> providerCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> providerUserIdCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> emailCaptor = ArgumentCaptor.forClass(String.class);
        verify(federatedIdentityService).createFederatedIdentity(
                accountIdCaptor.capture(), providerCaptor.capture(), providerUserIdCaptor.capture(), emailCaptor.capture());

        assertEquals("acc-1", accountIdCaptor.getValue());
        assertEquals(AccountService.GOOGLE, providerCaptor.getValue());
        assertEquals(GOOGLE_USER_ID, providerUserIdCaptor.getValue());
        assertEquals(EMAIL, emailCaptor.getValue());
    }

    @Test
    public void testHandleGoogleCallback_existingAccount_blankName_keepsExistingName() {
        GoogleUserInfo userInfo = new GoogleUserInfo();
        userInfo.setSub(GOOGLE_USER_ID);
        userInfo.setEmail(EMAIL);
        userInfo.setName("   ");
        userInfo.setPicture(null);

        when(googleOAuthService.getUserInfo("code"))
                .thenReturn(new GoogleOAuthService.GoogleAuthenticationResult(userInfo, null));
        when(googleOAuthService.convertToProxyUrl(null)).thenReturn(null);

        Account existing = new Account();
        existing.setId("acc-1");
        existing.setEmail(EMAIL);
        existing.setName("Existing Name");
        existing.setEmailVerified(false);

        when(accountService.findByEmail(EMAIL)).thenReturn(Optional.of(existing));
        when(accountService.updateAccount(existing)).thenReturn(existing);
        when(federatedIdentityService.findByProviderAndUserId(AccountService.GOOGLE, GOOGLE_USER_ID))
                .thenReturn(Optional.empty());

        service.handleGoogleCallback("code");

        assertEquals("Existing Name", existing.getName());
        assertEquals(null, existing.getPicture());
        assertEquals(false, existing.getEmailVerified());
        verify(accountService).updateAccount(existing);
    }

    @Test
    public void testHandleGoogleCallback_newAccount_signupAllowed_createsAccountAndLinksIdentity() {
        GoogleUserInfo userInfo = new GoogleUserInfo();
        userInfo.setSub(GOOGLE_USER_ID);
        userInfo.setEmail(EMAIL);
        userInfo.setName("Google User");
        userInfo.setPicture("https://lh3.googleusercontent.com/a/abc");

        when(googleOAuthService.getUserInfo("code"))
                .thenReturn(new GoogleOAuthService.GoogleAuthenticationResult(userInfo, true));
        when(googleOAuthService.convertToProxyUrl(userInfo.getPicture())).thenReturn(PROXY_PICTURE);

        when(accountService.findByEmail(EMAIL)).thenReturn(Optional.empty());
        when(authorizationService.isSignupAllowed()).thenReturn(true);

        Account created = new Account();
        created.setId("acc-2");

        when(accountService.createAccountFromFederatedProvider(
                EMAIL, "Google User", PROXY_PICTURE, true, AccountService.GOOGLE))
                .thenReturn(created);

        when(federatedIdentityService.findByProviderAndUserId(AccountService.GOOGLE, GOOGLE_USER_ID))
                .thenReturn(Optional.empty());

        Account result = service.handleGoogleCallback("code");

        assertEquals("acc-2", result.getId());

        ArgumentCaptor<String> accountIdCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> providerCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> providerUserIdCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> emailCaptor = ArgumentCaptor.forClass(String.class);
        verify(federatedIdentityService).createFederatedIdentity(
                accountIdCaptor.capture(), providerCaptor.capture(), providerUserIdCaptor.capture(), emailCaptor.capture());

        assertEquals("acc-2", accountIdCaptor.getValue());
        assertEquals(AccountService.GOOGLE, providerCaptor.getValue());
        assertEquals(GOOGLE_USER_ID, providerUserIdCaptor.getValue());
        assertEquals(EMAIL, emailCaptor.getValue());
    }

    @Test
    public void testHandleGoogleCallback_newAccount_signupDisabled_throws() {
        GoogleUserInfo userInfo = new GoogleUserInfo();
        userInfo.setSub(GOOGLE_USER_ID);
        userInfo.setEmail(EMAIL);
        userInfo.setName("Google User");

        when(googleOAuthService.getUserInfo("code"))
                .thenReturn(new GoogleOAuthService.GoogleAuthenticationResult(userInfo, null));

        when(accountService.findByEmail(EMAIL)).thenReturn(Optional.empty());
        when(authorizationService.isSignupAllowed()).thenReturn(false);

        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> {
            service.handleGoogleCallback("code");
        });

        assertEquals("Signup is disabled", exception.getMessage());
        verify(accountService, never()).createAccountFromFederatedProvider(any(), any(), any(), any(), any());
    }

    @Test
    public void testHandleGoogleCallback_existingIdentity_doesNotCreateDuplicate() {
        GoogleUserInfo userInfo = new GoogleUserInfo();
        userInfo.setSub(GOOGLE_USER_ID);
        userInfo.setEmail(EMAIL);
        userInfo.setName("Google User");

        when(googleOAuthService.getUserInfo("code"))
                .thenReturn(new GoogleOAuthService.GoogleAuthenticationResult(userInfo, true));
        when(googleOAuthService.convertToProxyUrl(any())).thenReturn(PROXY_PICTURE);

        Account existing = new Account();
        existing.setId("acc-1");
        existing.setEmail(EMAIL);

        when(accountService.findByEmail(EMAIL)).thenReturn(Optional.of(existing));
        when(accountService.updateAccount(existing)).thenReturn(existing);

        FederatedIdentity identity = new FederatedIdentity();
        identity.setId("fid-1");
        when(federatedIdentityService.findByProviderAndUserId(AccountService.GOOGLE, GOOGLE_USER_ID))
                .thenReturn(Optional.of(identity));

        Account result = service.handleGoogleCallback("code");

        assertEquals("acc-1", result.getId());
        verify(federatedIdentityService, never()).createFederatedIdentity(any(), any(), any(), any());
    }
}
