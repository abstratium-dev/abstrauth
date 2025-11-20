package dev.abstratium.abstrauth.service;

import dev.abstratium.abstrauth.entity.Account;
import dev.abstratium.abstrauth.entity.Credential;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
public class AccountServiceTest {

    @Inject
    AccountService accountService;

    @Test
    @Transactional
    public void testCreateAccountSuccess() {
        String email = "servicetest_" + System.currentTimeMillis() + "@example.com";
        String username = "servicetest_" + System.currentTimeMillis();
        
        Account account = accountService.createAccount(email, "Service Test", username, "Password123");
        
        assertNotNull(account);
        assertNotNull(account.getId());
        assertEquals(email, account.getEmail());
        assertEquals("Service Test", account.getName());
        assertFalse(account.getEmailVerified());
        
        // Verify credential was created
        Optional<Credential> credential = accountService.findCredentialByUsername(username);
        assertTrue(credential.isPresent());
        assertEquals(account.getId(), credential.get().getAccountId());
    }

    @Test
    @Transactional
    public void testCreateAccountWithDuplicateEmail() {
        String email = "duplicate_" + System.currentTimeMillis() + "@example.com";
        String username1 = "user1_" + System.currentTimeMillis();
        String username2 = "user2_" + System.currentTimeMillis();
        
        accountService.createAccount(email, "User One", username1, "Password123");
        
        assertThrows(IllegalArgumentException.class, () -> {
            accountService.createAccount(email, "User Two", username2, "Password456");
        });
    }

    @Test
    @Transactional
    public void testCreateAccountWithDuplicateUsername() {
        String email1 = "email1_" + System.currentTimeMillis() + "@example.com";
        String email2 = "email2_" + System.currentTimeMillis() + "@example.com";
        String username = "dupuser_" + System.currentTimeMillis();
        
        accountService.createAccount(email1, "User One", username, "Password123");
        
        assertThrows(IllegalArgumentException.class, () -> {
            accountService.createAccount(email2, "User Two", username, "Password456");
        });
    }

    @Test
    @Transactional
    public void testAuthenticateSuccess() {
        String email = "auth_" + System.currentTimeMillis() + "@example.com";
        String username = "authuser_" + System.currentTimeMillis();
        String password = "SecurePassword123";
        
        Account created = accountService.createAccount(email, "Auth User", username, password);
        
        Optional<Account> authenticated = accountService.authenticate(username, password);
        
        assertTrue(authenticated.isPresent());
        assertEquals(created.getId(), authenticated.get().getId());
        assertEquals(email, authenticated.get().getEmail());
    }

    @Test
    @Transactional
    public void testAuthenticateWithWrongPassword() {
        String email = "wrongpw_" + System.currentTimeMillis() + "@example.com";
        String username = "wrongpwuser_" + System.currentTimeMillis();
        
        accountService.createAccount(email, "Wrong PW User", username, "CorrectPassword");
        
        Optional<Account> authenticated = accountService.authenticate(username, "WrongPassword");
        
        assertFalse(authenticated.isPresent());
        
        // Verify failed attempt was recorded
        Optional<Credential> credential = accountService.findCredentialByUsername(username);
        assertTrue(credential.isPresent());
        assertEquals(1, credential.get().getFailedLoginAttempts());
    }

    @Test
    @Transactional
    public void testAuthenticateWithNonExistentUser() {
        Optional<Account> authenticated = accountService.authenticate("nonexistent_user", "password");
        
        assertFalse(authenticated.isPresent());
    }

    @Test
    @Transactional
    public void testAccountLockingAfterFailedAttempts() {
        String email = "locktest_" + System.currentTimeMillis() + "@example.com";
        String username = "lockuser_" + System.currentTimeMillis();
        String password = "CorrectPassword";
        
        accountService.createAccount(email, "Lock Test User", username, password);
        
        // Make 5 failed attempts
        for (int i = 0; i < 5; i++) {
            accountService.authenticate(username, "WrongPassword");
        }
        
        // Verify account is locked
        Optional<Credential> credential = accountService.findCredentialByUsername(username);
        assertTrue(credential.isPresent());
        assertEquals(5, credential.get().getFailedLoginAttempts());
        assertNotNull(credential.get().getLockedUntil());
        
        // Even with correct password, authentication should fail
        Optional<Account> authenticated = accountService.authenticate(username, password);
        assertFalse(authenticated.isPresent());
    }

    @Test
    @Transactional
    public void testFailedAttemptsResetOnSuccessfulLogin() {
        String email = "resettest_" + System.currentTimeMillis() + "@example.com";
        String username = "resetuser_" + System.currentTimeMillis();
        String password = "CorrectPassword";
        
        accountService.createAccount(email, "Reset Test User", username, password);
        
        // Make 3 failed attempts
        for (int i = 0; i < 3; i++) {
            accountService.authenticate(username, "WrongPassword");
        }
        
        // Verify failed attempts were recorded
        Optional<Credential> credentialBefore = accountService.findCredentialByUsername(username);
        assertTrue(credentialBefore.isPresent());
        assertEquals(3, credentialBefore.get().getFailedLoginAttempts());
        
        // Successful login
        Optional<Account> authenticated = accountService.authenticate(username, password);
        assertTrue(authenticated.isPresent());
        
        // Verify failed attempts were reset
        Optional<Credential> credentialAfter = accountService.findCredentialByUsername(username);
        assertTrue(credentialAfter.isPresent());
        assertEquals(0, credentialAfter.get().getFailedLoginAttempts());
        assertNull(credentialAfter.get().getLockedUntil());
    }

    @Test
    @Transactional
    public void testFindByEmail() {
        String email = "findtest_" + System.currentTimeMillis() + "@example.com";
        String username = "finduser_" + System.currentTimeMillis();
        
        Account created = accountService.createAccount(email, "Find Test", username, "Password123");
        
        Optional<Account> found = accountService.findByEmail(email);
        
        assertTrue(found.isPresent());
        assertEquals(created.getId(), found.get().getId());
        assertEquals(email, found.get().getEmail());
    }

    @Test
    public void testFindByEmailNotFound() {
        Optional<Account> found = accountService.findByEmail("nonexistent@example.com");
        
        assertFalse(found.isPresent());
    }

    @Test
    @Transactional
    public void testFindById() {
        String email = "findbyid_" + System.currentTimeMillis() + "@example.com";
        String username = "findbyiduser_" + System.currentTimeMillis();
        
        Account created = accountService.createAccount(email, "Find By ID Test", username, "Password123");
        
        Optional<Account> found = accountService.findById(created.getId());
        
        assertTrue(found.isPresent());
        assertEquals(created.getId(), found.get().getId());
        assertEquals(email, found.get().getEmail());
    }

    @Test
    public void testFindByIdNotFound() {
        Optional<Account> found = accountService.findById("nonexistent-id");
        
        assertFalse(found.isPresent());
    }

    @Test
    @Transactional
    public void testFindCredentialByAccountId() {
        String email = "credtest_" + System.currentTimeMillis() + "@example.com";
        String username = "creduser_" + System.currentTimeMillis();
        
        Account account = accountService.createAccount(email, "Cred Test", username, "Password123");
        
        Optional<Credential> credential = accountService.findCredentialByAccountId(account.getId());
        
        assertTrue(credential.isPresent());
        assertEquals(account.getId(), credential.get().getAccountId());
        assertEquals(username, credential.get().getUsername());
    }
}
