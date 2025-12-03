package dev.abstratium.abstrauth.entity;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for entity @PrePersist methods to ensure ID and timestamp generation works correctly.
 */
class EntityPrePersistTest {

    @Test
    void oAuthClientShouldGenerateIdWhenNull() {
        OAuthClient client = new OAuthClient();
        assertNull(client.getId());
        
        client.prePersist();
        
        assertNotNull(client.getId());
        assertTrue(client.getId().length() == 36); // UUID length
    }

    @Test
    void oAuthClientShouldNotOverrideExistingId() {
        OAuthClient client = new OAuthClient();
        String existingId = "existing-id-123";
        client.setId(existingId);
        
        client.prePersist();
        
        assertEquals(existingId, client.getId());
    }

    @Test
    void oAuthClientShouldGenerateCreatedAtWhenNull() {
        OAuthClient client = new OAuthClient();
        assertNull(client.getCreatedAt());
        
        LocalDateTime before = LocalDateTime.now();
        client.prePersist();
        LocalDateTime after = LocalDateTime.now();
        
        assertNotNull(client.getCreatedAt());
        assertTrue(client.getCreatedAt().isAfter(before.minusSeconds(1)));
        assertTrue(client.getCreatedAt().isBefore(after.plusSeconds(1)));
    }

    @Test
    void oAuthClientShouldNotOverrideExistingCreatedAt() {
        OAuthClient client = new OAuthClient();
        LocalDateTime existingTime = LocalDateTime.of(2024, 1, 1, 12, 0);
        client.setCreatedAt(existingTime);
        
        client.prePersist();
        
        assertEquals(existingTime, client.getCreatedAt());
    }

    @Test
    void authorizationCodeShouldGenerateIdWhenNull() {
        AuthorizationCode code = new AuthorizationCode();
        assertNull(code.getId());
        
        code.prePersist();
        
        assertNotNull(code.getId());
        assertTrue(code.getId().length() == 36);
    }

    @Test
    void authorizationCodeShouldNotOverrideExistingId() {
        AuthorizationCode code = new AuthorizationCode();
        String existingId = "existing-code-id";
        code.setId(existingId);
        
        code.prePersist();
        
        assertEquals(existingId, code.getId());
    }

    @Test
    void authorizationCodeShouldGenerateCreatedAtWhenNull() {
        AuthorizationCode code = new AuthorizationCode();
        assertNull(code.getCreatedAt());
        
        LocalDateTime before = LocalDateTime.now();
        code.prePersist();
        LocalDateTime after = LocalDateTime.now();
        
        assertNotNull(code.getCreatedAt());
        assertTrue(code.getCreatedAt().isAfter(before.minusSeconds(1)));
        assertTrue(code.getCreatedAt().isBefore(after.plusSeconds(1)));
    }

    @Test
    void authorizationCodeShouldNotOverrideExistingCreatedAt() {
        AuthorizationCode code = new AuthorizationCode();
        LocalDateTime existingTime = LocalDateTime.of(2024, 6, 15, 10, 30);
        code.setCreatedAt(existingTime);
        
        code.prePersist();
        
        assertEquals(existingTime, code.getCreatedAt());
    }

    @Test
    void credentialShouldGenerateIdWhenNull() {
        Credential credential = new Credential();
        assertNull(credential.getId());
        
        credential.prePersist();
        
        assertNotNull(credential.getId());
        assertTrue(credential.getId().length() == 36);
    }

    @Test
    void credentialShouldNotOverrideExistingId() {
        Credential credential = new Credential();
        String existingId = "existing-credential-id";
        credential.setId(existingId);
        
        credential.prePersist();
        
        assertEquals(existingId, credential.getId());
    }

    @Test
    void credentialShouldGenerateCreatedAtWhenNull() {
        Credential credential = new Credential();
        assertNull(credential.getCreatedAt());
        
        LocalDateTime before = LocalDateTime.now();
        credential.prePersist();
        LocalDateTime after = LocalDateTime.now();
        
        assertNotNull(credential.getCreatedAt());
        assertTrue(credential.getCreatedAt().isAfter(before.minusSeconds(1)));
        assertTrue(credential.getCreatedAt().isBefore(after.plusSeconds(1)));
    }

    @Test
    void credentialShouldNotOverrideExistingCreatedAt() {
        Credential credential = new Credential();
        LocalDateTime existingTime = LocalDateTime.of(2024, 3, 20, 14, 45);
        credential.setCreatedAt(existingTime);
        
        credential.prePersist();
        
        assertEquals(existingTime, credential.getCreatedAt());
    }

    @Test
    void federatedIdentityShouldGenerateIdWhenNull() {
        FederatedIdentity identity = new FederatedIdentity();
        assertNull(identity.getId());
        
        identity.prePersist();
        
        assertNotNull(identity.getId());
        assertTrue(identity.getId().length() == 36);
    }

    @Test
    void federatedIdentityShouldNotOverrideExistingId() {
        FederatedIdentity identity = new FederatedIdentity();
        String existingId = "existing-identity-id";
        identity.setId(existingId);
        
        identity.prePersist();
        
        assertEquals(existingId, identity.getId());
    }

    @Test
    void federatedIdentityShouldGenerateConnectedAtWhenNull() {
        FederatedIdentity identity = new FederatedIdentity();
        assertNull(identity.getConnectedAt());
        
        LocalDateTime before = LocalDateTime.now();
        identity.prePersist();
        LocalDateTime after = LocalDateTime.now();
        
        assertNotNull(identity.getConnectedAt());
        assertTrue(identity.getConnectedAt().isAfter(before.minusSeconds(1)));
        assertTrue(identity.getConnectedAt().isBefore(after.plusSeconds(1)));
    }

    @Test
    void federatedIdentityShouldNotOverrideExistingConnectedAt() {
        FederatedIdentity identity = new FederatedIdentity();
        LocalDateTime existingTime = LocalDateTime.of(2024, 7, 10, 9, 15);
        identity.setConnectedAt(existingTime);
        
        identity.prePersist();
        
        assertEquals(existingTime, identity.getConnectedAt());
    }

    @Test
    void accountRoleShouldGenerateIdWhenNull() {
        AccountRole role = new AccountRole();
        assertNull(role.getId());
        
        role.prePersist();
        
        assertNotNull(role.getId());
        assertTrue(role.getId().length() == 36);
    }

    @Test
    void accountRoleShouldNotOverrideExistingId() {
        AccountRole role = new AccountRole();
        String existingId = "existing-role-id";
        role.setId(existingId);
        
        role.prePersist();
        
        assertEquals(existingId, role.getId());
    }

    @Test
    void accountRoleShouldGenerateCreatedAtWhenNull() {
        AccountRole role = new AccountRole();
        assertNull(role.getCreatedAt());
        
        LocalDateTime before = LocalDateTime.now();
        role.prePersist();
        LocalDateTime after = LocalDateTime.now();
        
        assertNotNull(role.getCreatedAt());
        assertTrue(role.getCreatedAt().isAfter(before.minusSeconds(1)));
        assertTrue(role.getCreatedAt().isBefore(after.plusSeconds(1)));
    }

    @Test
    void accountRoleShouldNotOverrideExistingCreatedAt() {
        AccountRole role = new AccountRole();
        LocalDateTime existingTime = LocalDateTime.of(2024, 2, 28, 16, 20);
        role.setCreatedAt(existingTime);
        
        role.prePersist();
        
        assertEquals(existingTime, role.getCreatedAt());
    }

    @Test
    void accountShouldGenerateIdWhenNull() {
        Account account = new Account();
        assertNull(account.getId());
        
        account.prePersist();
        
        assertNotNull(account.getId());
        assertTrue(account.getId().length() == 36);
    }

    @Test
    void accountShouldNotOverrideExistingId() {
        Account account = new Account();
        String existingId = "existing-account-id";
        account.setId(existingId);
        
        account.prePersist();
        
        assertEquals(existingId, account.getId());
    }

    @Test
    void accountShouldGenerateCreatedAtWhenNull() {
        Account account = new Account();
        assertNull(account.getCreatedAt());
        
        LocalDateTime before = LocalDateTime.now();
        account.prePersist();
        LocalDateTime after = LocalDateTime.now();
        
        assertNotNull(account.getCreatedAt());
        assertTrue(account.getCreatedAt().isAfter(before.minusSeconds(1)));
        assertTrue(account.getCreatedAt().isBefore(after.plusSeconds(1)));
    }

    @Test
    void accountShouldNotOverrideExistingCreatedAt() {
        Account account = new Account();
        LocalDateTime existingTime = LocalDateTime.of(2024, 5, 5, 11, 30);
        account.setCreatedAt(existingTime);
        
        account.prePersist();
        
        assertEquals(existingTime, account.getCreatedAt());
    }

    @Test
    void authorizationRequestShouldGenerateIdWhenNull() {
        AuthorizationRequest request = new AuthorizationRequest();
        assertNull(request.getId());
        
        request.prePersist();
        
        assertNotNull(request.getId());
        assertTrue(request.getId().length() == 36);
    }

    @Test
    void authorizationRequestShouldNotOverrideExistingId() {
        AuthorizationRequest request = new AuthorizationRequest();
        String existingId = "existing-request-id";
        request.setId(existingId);
        
        request.prePersist();
        
        assertEquals(existingId, request.getId());
    }

    @Test
    void authorizationRequestShouldGenerateCreatedAtWhenNull() {
        AuthorizationRequest request = new AuthorizationRequest();
        assertNull(request.getCreatedAt());
        
        LocalDateTime before = LocalDateTime.now();
        request.prePersist();
        LocalDateTime after = LocalDateTime.now();
        
        assertNotNull(request.getCreatedAt());
        assertTrue(request.getCreatedAt().isAfter(before.minusSeconds(1)));
        assertTrue(request.getCreatedAt().isBefore(after.plusSeconds(1)));
    }

    @Test
    void authorizationRequestShouldNotOverrideExistingCreatedAt() {
        AuthorizationRequest request = new AuthorizationRequest();
        LocalDateTime existingTime = LocalDateTime.of(2024, 8, 12, 13, 45);
        request.setCreatedAt(existingTime);
        
        request.prePersist();
        
        assertEquals(existingTime, request.getCreatedAt());
    }
}
