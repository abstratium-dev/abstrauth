package dev.abstratium.abstrauth.boundary.oauth;

import dev.abstratium.abstrauth.entity.Account;
import dev.abstratium.abstrauth.entity.AuthorizationRequest;
import dev.abstratium.abstrauth.entity.OAuthClient;
import dev.abstratium.abstrauth.service.AccountService;
import dev.abstratium.abstrauth.service.AuthorizationService;
import dev.abstratium.abstrauth.service.OAuthClientService;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the approve-authenticated endpoint that handles OAuth authorization
 * for users who are already authenticated via OIDC session.
 * 
 * Note: The endpoint is now at /api/oauth/approve-authenticated to ensure
 * OIDC BFF authentication applies (only /api/* paths are protected).
 */
@QuarkusTest
public class AuthorizationResourceAlreadyAuthenticatedTest {

    @Inject
    OAuthClientService clientService;

    @Inject
    AuthorizationService authorizationService;

    @Inject
    AccountService accountService;

    @Inject
    dev.abstratium.abstrauth.service.AccountRoleService accountRoleService;

    private OAuthClient testClient;
    private Account testAccount;

    @BeforeEach
    public void setup() {
        // Create test client
        testClient = clientService.findByClientId("abstratium-abstrauth")
                .orElseThrow(() -> new RuntimeException("Test client not found"));

        // Create or get test account
        Optional<Account> accountOpt = accountService.findByEmail("test-already-auth@example.com");
        if (accountOpt.isEmpty()) {
            testAccount = accountService.createAccount(
                    "test-already-auth@example.com",
                    "Test User Already Auth",
                    "test-already-auth",
                    "password123",
                    AccountService.NATIVE
            );
        } else {
            testAccount = accountOpt.get();
        }
    }

    @Test
    @TestSecurity(user = "test-already-auth@example.com", roles = {"abstratium-abstrauth_user"})
    public void testApproveAuthenticatedSuccess() {
        // Create an authorization request
        AuthorizationRequest authRequest = authorizationService.createAuthorizationRequest(
                testClient.getClientId(),
                "http://localhost:8080/api/auth/callback",
                "openid profile email",
                "test-state",
                "test-challenge",
                "S256"
        );

        // Verify request is pending
        assertEquals("pending", authRequest.getStatus());

        // Call approve-authenticated endpoint
        given()
                .queryParam("request_id", authRequest.getId())
                .when()
                .post("/api/oauth/approve-authenticated")
                .then()
                .statusCode(200)
                .body("name", equalTo(testAccount.getName()));

        // Verify request is now approved
        Optional<AuthorizationRequest> updatedRequest = authorizationService.findAuthorizationRequest(authRequest.getId());
        assertTrue(updatedRequest.isPresent());
        assertEquals("approved", updatedRequest.get().getStatus());
        assertEquals(testAccount.getId(), updatedRequest.get().getAccountId());
    }

    @Test
    public void testApproveAuthenticatedUnauthenticated() {
        // Create an authorization request
        AuthorizationRequest authRequest = authorizationService.createAuthorizationRequest(
                testClient.getClientId(),
                "http://localhost:8080/api/auth/callback",
                "openid profile email",
                "test-state",
                "test-challenge",
                "S256"
        );

        // Call without authentication should fail
        given()
                .queryParam("request_id", authRequest.getId())
                .when()
                .post("/api/oauth/approve-authenticated")
                .then()
                .statusCode(401);

        // Verify request is still pending
        Optional<AuthorizationRequest> updatedRequest = authorizationService.findAuthorizationRequest(authRequest.getId());
        assertTrue(updatedRequest.isPresent());
        assertEquals("pending", updatedRequest.get().getStatus());
    }

    @Test
    @TestSecurity(user = "test-already-auth@example.com", roles = {"abstratium-abstrauth_user"})
    public void testApproveAuthenticatedInvalidRequestId() {
        // Call with invalid request ID
        given()
                .queryParam("request_id", "invalid-request-id")
                .when()
                .post("/api/oauth/approve-authenticated")
                .then()
                .statusCode(400);
    }

    @Test
    @TestSecurity(user = "test-already-auth@example.com", roles = {"abstratium-abstrauth_user"})
    public void testApproveAuthenticatedAlreadyApprovedRequest() {
        // Create and approve an authorization request
        AuthorizationRequest authRequest = authorizationService.createAuthorizationRequest(
                testClient.getClientId(),
                "http://localhost:8080/api/auth/callback",
                "openid profile email",
                "test-state",
                "test-challenge",
                "S256"
        );

        authorizationService.approveAuthorizationRequest(
                authRequest.getId(),
                testAccount.getId(),
                AccountService.NATIVE
        );

        // Try to approve again should fail
        given()
                .queryParam("request_id", authRequest.getId())
                .when()
                .post("/api/oauth/approve-authenticated")
                .then()
                .statusCode(400);
    }

    @Test
    @TestSecurity(user = "test-no-roles-for-client@example.com", roles = {"abstratium-abstrauth_user"})
    public void testApproveAuthenticatedUserWithNoRolesForSpecificClient() {
        // Create a unique test client for this test to avoid flakiness
        String uniqueClientId = "test-client-no-roles-" + System.currentTimeMillis();
        OAuthClient testClientNoRoles = new OAuthClient();
        testClientNoRoles.setClientId(uniqueClientId);
        testClientNoRoles.setClientName("Test Client No Roles");
        testClientNoRoles.setClientType("confidential");
        testClientNoRoles.setRedirectUris("[\"http://localhost:3000/callback\"]");
        testClientNoRoles.setAllowedScopes("[\"openid\",\"profile\",\"email\"]");
        testClientNoRoles.setRequirePkce(true);
        testClientNoRoles = clientService.create(testClientNoRoles);

        // Create account - it will have roles for abstratium-abstrauth but NOT for our test client
        Optional<Account> accountOpt = accountService.findByEmail("test-no-roles-for-client@example.com");
        if (accountOpt.isEmpty()) {
            accountService.createAccount(
                    "test-no-roles-for-client@example.com",
                    "Test User No Roles For Client",
                    "test-no-roles-for-client",
                    "password123",
                    AccountService.NATIVE
            );
        }
        
        // Re-fetch account to ensure roles are loaded
        Account accountWithNoRolesForClient = accountService.findByEmail("test-no-roles-for-client@example.com")
                .orElseThrow(() -> new RuntimeException("Account not found after creation"));

        // Verify account has roles for abstratium-abstrauth but not for our test client
        assertTrue(accountWithNoRolesForClient.getRoles().stream()
                .anyMatch(role -> role.getClientId().equals("abstratium-abstrauth")),
                "Account should have roles for abstratium-abstrauth");
        assertFalse(accountWithNoRolesForClient.getRoles().stream()
                .anyMatch(role -> role.getClientId().equals(uniqueClientId)),
                "Account should NOT have roles for test client");

        // Create an authorization request for the test client
        AuthorizationRequest authRequest = authorizationService.createAuthorizationRequest(
                testClientNoRoles.getClientId(),
                "http://localhost:3000/callback",
                "openid profile email",
                "test-state",
                "test-challenge",
                "S256"
        );

        // Try to approve should fail with 403 because user has no roles for this client
        given()
                .queryParam("request_id", authRequest.getId())
                .when()
                .post("/api/oauth/approve-authenticated")
                .then()
                .statusCode(403)
                .body(containsString("You do not have any roles for this application"));

        // Verify request is still pending
        Optional<AuthorizationRequest> updatedRequest = authorizationService.findAuthorizationRequest(authRequest.getId());
        assertTrue(updatedRequest.isPresent());
        assertEquals("pending", updatedRequest.get().getStatus());
    }

}
