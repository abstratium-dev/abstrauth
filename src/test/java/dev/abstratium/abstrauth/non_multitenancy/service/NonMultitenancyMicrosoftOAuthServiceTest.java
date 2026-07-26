package dev.abstratium.abstrauth.non_multitenancy.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
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
import dev.abstratium.abstrauth.service.MicrosoftOAuthService;
import dev.abstratium.abstrauth.service.oauth.MicrosoftUserInfo;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;

@QuarkusTest
public class NonMultitenancyMicrosoftOAuthServiceTest {

    @Inject
    NonMultitenancyMicrosoftOAuthService service;

    @InjectMock
    MicrosoftOAuthService microsoftOAuthService;

    @InjectMock
    AccountService accountService;

    @InjectMock
    FederatedIdentityService federatedIdentityService;

    @InjectMock
    NonMultitenancyAuthorizationService authorizationService;

    private static final String MICROSOFT_USER_ID = "microsoft-user-123";
    private static final String EMAIL = "testuser@example.com";

    @Test
    public void testHandleMicrosoftCallback_existingAccount_updatesAndLinksIdentity() {
        MicrosoftUserInfo userInfo = new MicrosoftUserInfo();
        userInfo.setId(MICROSOFT_USER_ID);
        userInfo.setDisplayName("Microsoft User");
        userInfo.setMail(EMAIL);

        when(microsoftOAuthService.getUserInfo("code"))
                .thenReturn(new MicrosoftOAuthService.MicrosoftAuthenticationResult(userInfo, true));

        Account existing = new Account();
        existing.setId("acc-1");
        existing.setEmail(EMAIL);
        existing.setName("Old Name");
        existing.setEmailVerified(false);
        existing.setPicture("existing-picture");

        when(accountService.findByEmail(EMAIL)).thenReturn(Optional.of(existing));
        when(accountService.updateAccount(existing)).thenReturn(existing);
        when(federatedIdentityService.findByProviderAndUserId(AccountService.MICROSOFT, MICROSOFT_USER_ID))
                .thenReturn(Optional.empty());

        Account result = service.handleMicrosoftCallback("code");

        assertEquals("acc-1", result.getId());
        assertEquals("Microsoft User", result.getName());
        assertNull(result.getPicture());
        assertTrue(result.getEmailVerified());

        verify(accountService).updateAccount(existing);

        ArgumentCaptor<String> accountIdCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> providerCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> providerUserIdCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> emailCaptor = ArgumentCaptor.forClass(String.class);
        verify(federatedIdentityService).createFederatedIdentity(
                accountIdCaptor.capture(), providerCaptor.capture(), providerUserIdCaptor.capture(), emailCaptor.capture());

        assertEquals("acc-1", accountIdCaptor.getValue());
        assertEquals(AccountService.MICROSOFT, providerCaptor.getValue());
        assertEquals(MICROSOFT_USER_ID, providerUserIdCaptor.getValue());
        assertEquals(EMAIL, emailCaptor.getValue());
    }

    @Test
    public void testHandleMicrosoftCallback_existingAccount_blankName_keepsExistingName() {
        MicrosoftUserInfo userInfo = new MicrosoftUserInfo();
        userInfo.setId(MICROSOFT_USER_ID);
        userInfo.setDisplayName("   ");
        userInfo.setMail(EMAIL);

        when(microsoftOAuthService.getUserInfo("code"))
                .thenReturn(new MicrosoftOAuthService.MicrosoftAuthenticationResult(userInfo, null));

        Account existing = new Account();
        existing.setId("acc-1");
        existing.setEmail(EMAIL);
        existing.setName("Existing Name");
        existing.setEmailVerified(false);

        when(accountService.findByEmail(EMAIL)).thenReturn(Optional.of(existing));
        when(accountService.updateAccount(existing)).thenReturn(existing);
        when(federatedIdentityService.findByProviderAndUserId(AccountService.MICROSOFT, MICROSOFT_USER_ID))
                .thenReturn(Optional.empty());

        service.handleMicrosoftCallback("code");

        assertEquals("Existing Name", existing.getName());
        assertNull(existing.getPicture());
        assertTrue(existing.getEmailVerified());
        verify(accountService).updateAccount(existing);
    }

    @Test
    public void testHandleMicrosoftCallback_newAccount_signupAllowed_createsAccountAndLinksIdentity() {
        MicrosoftUserInfo userInfo = new MicrosoftUserInfo();
        userInfo.setId(MICROSOFT_USER_ID);
        userInfo.setDisplayName("Microsoft User");
        userInfo.setMail(EMAIL);

        when(microsoftOAuthService.getUserInfo("code"))
                .thenReturn(new MicrosoftOAuthService.MicrosoftAuthenticationResult(userInfo, true));

        when(accountService.findByEmail(EMAIL)).thenReturn(Optional.empty());
        when(authorizationService.isSignupAllowed()).thenReturn(true);

        Account created = new Account();
        created.setId("acc-2");

        when(accountService.createAccountFromFederatedProvider(
                EMAIL, "Microsoft User", null, true, AccountService.MICROSOFT))
                .thenReturn(created);

        when(federatedIdentityService.findByProviderAndUserId(AccountService.MICROSOFT, MICROSOFT_USER_ID))
                .thenReturn(Optional.empty());

        Account result = service.handleMicrosoftCallback("code");

        assertEquals("acc-2", result.getId());

        ArgumentCaptor<String> accountIdCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> providerCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> providerUserIdCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> emailCaptor = ArgumentCaptor.forClass(String.class);
        verify(federatedIdentityService).createFederatedIdentity(
                accountIdCaptor.capture(), providerCaptor.capture(), providerUserIdCaptor.capture(), emailCaptor.capture());

        assertEquals("acc-2", accountIdCaptor.getValue());
        assertEquals(AccountService.MICROSOFT, providerCaptor.getValue());
        assertEquals(MICROSOFT_USER_ID, providerUserIdCaptor.getValue());
        assertEquals(EMAIL, emailCaptor.getValue());
    }

    @Test
    public void testHandleMicrosoftCallback_newAccount_signupDisabled_throws() {
        MicrosoftUserInfo userInfo = new MicrosoftUserInfo();
        userInfo.setId(MICROSOFT_USER_ID);
        userInfo.setMail(EMAIL);

        when(microsoftOAuthService.getUserInfo("code"))
                .thenReturn(new MicrosoftOAuthService.MicrosoftAuthenticationResult(userInfo, null));

        when(accountService.findByEmail(EMAIL)).thenReturn(Optional.empty());
        when(authorizationService.isSignupAllowed()).thenReturn(false);

        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> {
            service.handleMicrosoftCallback("code");
        });

        assertEquals("Signup is disabled", exception.getMessage());
        verify(accountService, never()).createAccountFromFederatedProvider(any(), any(), any(), any(), any());
    }

    @Test
    public void testHandleMicrosoftCallback_existingIdentity_doesNotCreateDuplicate() {
        MicrosoftUserInfo userInfo = new MicrosoftUserInfo();
        userInfo.setId(MICROSOFT_USER_ID);
        userInfo.setDisplayName("Microsoft User");
        userInfo.setMail(EMAIL);

        when(microsoftOAuthService.getUserInfo("code"))
                .thenReturn(new MicrosoftOAuthService.MicrosoftAuthenticationResult(userInfo, true));

        Account existing = new Account();
        existing.setId("acc-1");
        existing.setEmail(EMAIL);

        when(accountService.findByEmail(EMAIL)).thenReturn(Optional.of(existing));
        when(accountService.updateAccount(existing)).thenReturn(existing);

        FederatedIdentity identity = new FederatedIdentity();
        identity.setId("fid-1");
        when(federatedIdentityService.findByProviderAndUserId(AccountService.MICROSOFT, MICROSOFT_USER_ID))
                .thenReturn(Optional.of(identity));

        Account result = service.handleMicrosoftCallback("code");

        assertEquals("acc-1", result.getId());
        verify(federatedIdentityService, never()).createFederatedIdentity(any(), any(), any(), any());
    }
}
