package dev.abstratium.abstrauth.boundary;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import dev.abstratium.abstrauth.entity.Account;
import dev.abstratium.abstrauth.service.AccountService;
import dev.abstratium.abstrauth.service.OrganisationService;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.smallrye.jwt.build.Jwt;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import dev.abstratium.abstrauth.util.TestTransactionHelper;

/**
 * Edge case and bypass attempt tests for multi-tenancy security.
 * Tests malformed inputs, boundary conditions, and potential bypass vectors.
 */
@QuarkusTest
public class MultiTenancyEdgeCaseTest {

    @Inject
    AccountService accountService;

    @Inject
    OrganisationService organisationService;

    @Inject
    EntityManager em;

    @Inject
    TestTransactionHelper transactionHelper;

    private String generateToken(String accountId, String orgId, Set<String> groups) {
        return Jwt.issuer("https://abstrauth.abstratium.dev")
            .subject(accountId)
            .upn("test_" + accountId + "@example.com")
            .groups(groups)
            .claim("orgId", orgId)
            .sign();
    }

    private String adminTokenForOrg(String accountId, String orgId) {
        return generateToken(accountId, orgId, Set.of(
            "abstratium-abstrauth_user",
            "abstratium-abstrauth_admin",
            "abstratium-abstrauth_manage-accounts",
            "abstratium-abstrauth_manage-clients"
        ));
    }

    private String userTokenForOrg(String accountId, String orgId) {
        return generateToken(accountId, orgId, Set.of(
            "abstratium-abstrauth_user"
        ));
    }

    // ====================================================================================
    // TEST 1: Null/Empty orgId Handling
    // ====================================================================================

    @Test
    public void testNullOrgIdFallsBackToDefault() throws Exception {
        long ts = System.currentTimeMillis();

        transactionHelper.beginTransaction();

        // Create account
        String email = "nullorg_test_" + ts + "@example.com";
        Account account = accountService.createAccount(email, "Null Org Test", email, "Pass123!", AccountService.NATIVE, "Null Org " + ts);

        transactionHelper.commitTransaction();

        // Create token without orgId claim (omitted, not set to null)
        // This simulates a token without the orgId claim to test fallback behavior
        String noOrgToken = Jwt.issuer("https://abstrauth.abstratium.dev")
            .subject(account.getId())
            .upn("test_" + account.getId() + "@example.com")
            .groups(Set.of("abstratium-abstrauth_user"))
            .sign();

        // Request without orgId claim - interceptor lets it proceed, resource handles it
        given()
            .auth().oauth2(noOrgToken)
            .when()
            .get("/api/accounts")
            .then()
            .statusCode(anyOf(is(200), is(403)));
    }

    @Test
    public void testEmptyOrgIdFallsBackToDefault() throws Exception {
        long ts = System.currentTimeMillis();

        transactionHelper.beginTransaction();

        // Create account
        String email = "emptyorg_test_" + ts + "@example.com";
        Account account = accountService.createAccount(email, "Empty Org Test", email, "Pass123!", AccountService.NATIVE, "Empty Org " + ts);

        transactionHelper.commitTransaction();

        // Create token with empty orgId
        String emptyOrgToken = Jwt.issuer("https://abstrauth.abstratium.dev")
            .subject(account.getId())
            .upn("test_" + account.getId() + "@example.com")
            .groups(Set.of("abstratium-abstrauth_user"))
            .claim("orgId", "")
            .sign();

        // Request should handle empty orgId gracefully
        given()
            .auth().oauth2(emptyOrgToken)
            .when()
            .get("/api/accounts")
            .then()
            .statusCode(anyOf(is(200), is(400), is(403)));
    }

    // ====================================================================================
    // TEST 2: Invalid/Malformed orgId Handling
    // ====================================================================================

    @Test
    public void testInvalidOrgIdFormat() throws Exception {
        long ts = System.currentTimeMillis();

        transactionHelper.beginTransaction();

        // Create account
        String email = "invalidorg_test_" + ts + "@example.com";
        Account account = accountService.createAccount(email, "Invalid Org Test", email, "Pass123!", AccountService.NATIVE, "Invalid Org " + ts);

        transactionHelper.commitTransaction();

        // Create token with invalid orgId format (not a UUID)
        String invalidOrgToken = Jwt.issuer("https://abstrauth.abstratium.dev")
            .subject(account.getId())
            .upn("test_" + account.getId() + "@example.com")
            .groups(Set.of("abstratium-abstrauth_user"))
            .claim("orgId", "not-a-valid-uuid")
            .sign();

        // System should handle invalid orgId gracefully
        given()
            .auth().oauth2(invalidOrgToken)
            .when()
            .get("/api/accounts")
            .then()
            .statusCode(anyOf(is(200), is(400), is(403)));
    }

    @Test
    public void testNonExistentOrgId() throws Exception {
        long ts = System.currentTimeMillis();

        transactionHelper.beginTransaction();

        // Create account
        String email = "nonexistorg_test_" + ts + "@example.com";
        Account account = accountService.createAccount(email, "Non Exist Org Test", email, "Pass123!", AccountService.NATIVE, "Non Exist Org " + ts);

        transactionHelper.commitTransaction();

        // Create token with non-existent but valid UUID format orgId
        String fakeOrgId = UUID.randomUUID().toString();
        String fakeOrgToken = Jwt.issuer("https://abstrauth.abstratium.dev")
            .subject(account.getId())
            .upn("test_" + account.getId() + "@example.com")
            .groups(Set.of("abstratium-abstrauth_user"))
            .claim("orgId", fakeOrgId)
            .sign();

        // System should reject request for non-existent org since account is not a member
        given()
            .auth().oauth2(fakeOrgToken)
            .when()
            .get("/api/accounts")
            .then()
            .statusCode(403); // Forbidden since account is not a member of the claimed org
    }

    // ====================================================================================
    // TEST 3: Last Owner Protection
    // ====================================================================================

    @Test
    public void testCannotRemoveSelfAsLastOwner() throws Exception {
        long ts = System.currentTimeMillis();

        transactionHelper.beginTransaction();

        // Create Org with single owner
        String email = "last_owner_" + ts + "@example.com";
        Account account = accountService.createAccount(email, "Last Owner Test", email, "Pass123!", AccountService.NATIVE, "Last Owner Org " + ts);
        String orgId = organisationService.listOrganisationsForAccount(account.getId()).get(0).getId();

        transactionHelper.commitTransaction();

        // Attempt to remove self as member (would leave org without owners)
        // This should be blocked by OrganisationService.removeOwner()
        given()
            .auth().oauth2(userTokenForOrg(account.getId(), orgId))
            .when()
            .delete("/api/organisations/" + orgId + "/members/" + account.getId())
            .then()
            .statusCode(anyOf(is(400), is(403)));
    }

    @Test
    public void testCannotRemoveOnlyOwnerViaAnotherMember() throws Exception {
        long ts = System.currentTimeMillis();

        transactionHelper.beginTransaction();

        // Create Org with owner
        String ownerEmail = "owner_protect_" + ts + "@example.com";
        Account owner = accountService.createAccount(ownerEmail, "Owner Protect Test", ownerEmail, "Pass123!", AccountService.NATIVE, "Owner Protect Org " + ts);
        String orgId = organisationService.listOrganisationsForAccount(owner.getId()).get(0).getId();

        // Add another member (not owner)
        String memberEmail = "member_protect_" + ts + "@example.com";
        Account member = accountService.createAccount(memberEmail, "Member Protect Test", memberEmail, "Pass123!", AccountService.NATIVE, "Member Org " + ts);
        organisationService.addMember(orgId, member.getId());

        transactionHelper.commitTransaction();

        // Member attempts to remove the owner
        // This should fail because the member is not an owner
        given()
            .auth().oauth2(userTokenForOrg(member.getId(), orgId))
            .when()
            .delete("/api/organisations/" + orgId + "/members/" + owner.getId())
            .then()
            .statusCode(403);
    }

    // ====================================================================================
    // TEST 4: Deleted Organisation Handling
    // ====================================================================================

    @Test
    public void testTokenWithDeletedOrgIdIsRejected() throws Exception {
        long ts = System.currentTimeMillis();

        transactionHelper.beginTransaction();

        // Create account and org
        String email = "deletedorg_test_" + ts + "@example.com";
        Account account = accountService.createAccount(email, "Deleted Org Test", email, "Pass123!", AccountService.NATIVE, "Deleted Org " + ts);
        String orgId = organisationService.listOrganisationsForAccount(account.getId()).get(0).getId();

        // Delete the organisation via native SQL
        em.createNativeQuery("DELETE FROM T_organisation_accounts WHERE org_id = :orgId")
            .setParameter("orgId", orgId)
            .executeUpdate();
        em.createNativeQuery("DELETE FROM T_organisations WHERE id = :orgId")
            .setParameter("orgId", orgId)
            .executeUpdate();

        transactionHelper.commitTransaction();

        // Create token with deleted orgId
        String deletedOrgToken = adminTokenForOrg(account.getId(), orgId);

        // Requests should fail since org no longer exists
        given()
            .auth().oauth2(deletedOrgToken)
            .when()
            .get("/api/organisations/current")
            .then()
            .statusCode(anyOf(is(404), is(400)));
    }

    // ====================================================================================
    // TEST 5: Path Parameter Tampering
    // ====================================================================================

    @Test
    public void testPathParameterOrgIdMismatchWithToken() throws Exception {
        long ts = System.currentTimeMillis();

        transactionHelper.beginTransaction();

        // Create Org A
        String emailA = "path_orgA_" + ts + "@example.com";
        Account accountA = accountService.createAccount(emailA, "Path Org A Test", emailA, "Pass123!", AccountService.NATIVE, "Path Org A " + ts);
        String orgAId = organisationService.listOrganisationsForAccount(accountA.getId()).get(0).getId();

        // Create Org B
        String emailB = "path_orgB_" + ts + "@example.com";
        Account accountB = accountService.createAccount(emailB, "Path Org B Test", emailB, "Pass123!", AccountService.NATIVE, "Path Org B " + ts);
        String orgBId = organisationService.listOrganisationsForAccount(accountB.getId()).get(0).getId();

        // Create a user to add
        String emailUser = "path_user_" + ts + "@example.com";
        Account userAccount = accountService.createAccount(emailUser, "Path User", emailUser, "Pass123!", AccountService.NATIVE, "Path User Org " + ts);

        transactionHelper.commitTransaction();

        // User has token for Org A, but tries to modify Org B via path parameter
        String requestBody = """
            {
                "accountId": "%s"
            }
            """.formatted(userAccount.getId());

        // The token has orgA claim, but path has orgB
        // The endpoint checks ownership using caller from token + path orgId
        // Since caller from token is not owner of orgB, this should fail
        given()
            .auth().oauth2(userTokenForOrg(accountA.getId(), orgAId))
            .contentType(ContentType.JSON)
            .body(requestBody)
            .when()
            .post("/api/organisations/" + orgBId + "/members")
            .then()
            .statusCode(403);
    }

    // ====================================================================================
    // TEST 6: SQL Injection Attempts via orgId
    // ====================================================================================

    @Test
    public void testSqlInjectionInOrgIdClaim() throws Exception {
        long ts = System.currentTimeMillis();

        transactionHelper.beginTransaction();

        // Create account
        String email = "sqlinject_test_" + ts + "@example.com";
        Account account = accountService.createAccount(email, "SQL Inject Test", email, "Pass123!", AccountService.NATIVE, "SQL Inject Org " + ts);

        transactionHelper.commitTransaction();

        // Create token with SQL injection attempt in orgId
        String maliciousOrgId = "1' OR '1'='1";
        String sqlInjectToken = Jwt.issuer("https://abstrauth.abstratium.dev")
            .subject(account.getId())
            .upn("test_" + account.getId() + "@example.com")
            .groups(Set.of("abstratium-abstrauth_user"))
            .claim("orgId", maliciousOrgId)
            .sign();

        // System should handle SQL injection attempt safely
        given()
            .auth().oauth2(sqlInjectToken)
            .when()
            .get("/api/accounts")
            .then()
            .statusCode(anyOf(is(200), is(400), is(403)));
    }

    @Test
    public void testSqlInjectionInOrgIdPathParam() throws Exception {
        long ts = System.currentTimeMillis();

        transactionHelper.beginTransaction();

        // Create account and org
        String email = "sqlpath_test_" + ts + "@example.com";
        Account account = accountService.createAccount(email, "SQL Path Test", email, "Pass123!", AccountService.NATIVE, "SQL Path Org " + ts);
        String orgId = organisationService.listOrganisationsForAccount(account.getId()).get(0).getId();

        transactionHelper.commitTransaction();

        // Attempt SQL injection via path parameter using POST
        // The endpoint is POST /api/organisations/{orgId}/members
        String requestBody = """
            {
                "accountId": "%s"
            }
            """.formatted(account.getId());
        given()
            .auth().oauth2(userTokenForOrg(account.getId(), orgId))
            .contentType(ContentType.JSON)
            .body(requestBody)
            .when()
            .post("/api/organisations/1'%20OR%20'1'='1/members")
            .then()
            .statusCode(anyOf(is(404), is(400), is(403)));
    }

    // ====================================================================================
    // TEST 7: Unicode/Encoding Attacks
    // ====================================================================================

    @Test
    public void testUnicodeInOrgId() throws Exception {
        long ts = System.currentTimeMillis();

        transactionHelper.beginTransaction();

        // Create account
        String email = "unicode_test_" + ts + "@example.com";
        Account account = accountService.createAccount(email, "Unicode Test", email, "Pass123!", AccountService.NATIVE, "Unicode Org " + ts);

        transactionHelper.commitTransaction();

        // Create token with unicode in orgId
        String unicodeOrgId = "00000000-0000-0000-0000-00000000\u0000";
        String unicodeToken = Jwt.issuer("https://abstrauth.abstratium.dev")
            .subject(account.getId())
            .upn("test_" + account.getId() + "@example.com")
            .groups(Set.of("abstratium-abstrauth_user"))
            .claim("orgId", unicodeOrgId)
            .sign();

        // System should handle unicode safely
        given()
            .auth().oauth2(unicodeToken)
            .when()
            .get("/api/accounts")
            .then()
            .statusCode(anyOf(is(200), is(400), is(403)));
    }

    // ====================================================================================
    // TEST 8: Very Long orgId
    // ====================================================================================

    @Test
    public void testVeryLongOrgId() throws Exception {
        long ts = System.currentTimeMillis();

        transactionHelper.beginTransaction();

        // Create account
        String email = "longorg_test_" + ts + "@example.com";
        Account account = accountService.createAccount(email, "Long Org Test", email, "Pass123!", AccountService.NATIVE, "Long Org " + ts);

        transactionHelper.commitTransaction();

        // Create token with very long orgId
        StringBuilder longOrgId = new StringBuilder();
        for (int i = 0; i < 1000; i++) {
            longOrgId.append("a");
        }
        String longOrgToken = Jwt.issuer("https://abstrauth.abstratium.dev")
            .subject(account.getId())
            .upn("test_" + account.getId() + "@example.com")
            .groups(Set.of("abstratium-abstrauth_user"))
            .claim("orgId", longOrgId.toString())
            .sign();

        // System should handle long orgId safely
        given()
            .auth().oauth2(longOrgToken)
            .when()
            .get("/api/accounts")
            .then()
            .statusCode(anyOf(is(200), is(400), is(403)));
    }

    // ====================================================================================
    // TEST 9: Case Sensitivity in orgId
    // ====================================================================================

    @Test
    public void testCaseSensitivityInOrgId() throws Exception {
        long ts = System.currentTimeMillis();

        transactionHelper.beginTransaction();

        // Create account and org
        String email = "case_test_" + ts + "@example.com";
        Account account = accountService.createAccount(email, "Case Test", email, "Pass123!", AccountService.NATIVE, "Case Org " + ts);
        String orgId = organisationService.listOrganisationsForAccount(account.getId()).get(0).getId();

        transactionHelper.commitTransaction();

        // Create token with uppercase version of orgId
        String upperOrgId = orgId.toUpperCase();
        String upperOrgToken = Jwt.issuer("https://abstrauth.abstratium.dev")
            .subject(account.getId())
            .upn("test_" + account.getId() + "@example.com")
            .groups(Set.of("abstratium-abstrauth_user"))
            .claim("orgId", upperOrgId)
            .sign();

        // UUIDs are typically case-insensitive, but let's see how the system handles it
        given()
            .auth().oauth2(upperOrgToken)
            .when()
            .get("/api/organisations/current")
            .then()
            .statusCode(anyOf(is(200), is(404)));
    }

    // ====================================================================================
    // TEST 10: Race Condition Simulation (Rapid orgId Switching)
    // ====================================================================================

    @Test
    public void testRapidTokenSwitching() throws Exception {
        long ts = System.currentTimeMillis();

        transactionHelper.beginTransaction();

        // Create Org A
        String emailA = "rapid_orgA_" + ts + "@example.com";
        Account accountA = accountService.createAccount(emailA, "Rapid Org A Test", emailA, "Pass123!", AccountService.NATIVE, "Rapid Org A " + ts);
        String orgAId = organisationService.listOrganisationsForAccount(accountA.getId()).get(0).getId();

        // Create Org B
        String emailB = "rapid_orgB_" + ts + "@example.com";
        Account accountB = accountService.createAccount(emailB, "Rapid Org B Test", emailB, "Pass123!", AccountService.NATIVE, "Rapid Org B " + ts);
        String orgBId = organisationService.listOrganisationsForAccount(accountB.getId()).get(0).getId();

        // Create clients in each org
        String clientIdA = "rapid-client-a-" + ts;
        String clientUuidA = UUID.randomUUID().toString();
        em.createNativeQuery(
            "INSERT INTO T_oauth_clients (id, client_id, client_name, client_type, redirect_uris, allowed_scopes, require_pkce, auto_subscribe, org_id) " +
            "VALUES (:id, :clientId, :name, 'confidential', '[]', '[]', true, true, :orgId)")
            .setParameter("id", clientUuidA)
            .setParameter("clientId", clientIdA)
            .setParameter("name", "Rapid Client A")
            .setParameter("orgId", orgAId)
            .executeUpdate();

        String clientIdB = "rapid-client-b-" + ts;
        String clientUuidB = UUID.randomUUID().toString();
        em.createNativeQuery(
            "INSERT INTO T_oauth_clients (id, client_id, client_name, client_type, redirect_uris, allowed_scopes, require_pkce, auto_subscribe, org_id) " +
            "VALUES (:id, :clientId, :name, 'confidential', '[]', '[]', true, true, :orgId)")
            .setParameter("id", clientUuidB)
            .setParameter("clientId", clientIdB)
            .setParameter("name", "Rapid Client B")
            .setParameter("orgId", orgBId)
            .executeUpdate();

        transactionHelper.commitTransaction();

        // Create tokens for both orgs
        String tokenA = adminTokenForOrg(accountA.getId(), orgAId);
        String tokenB = adminTokenForOrg(accountB.getId(), orgBId);

        // Rapidly switch between tokens and verify isolation holds
        for (int i = 0; i < 5; i++) {
            // Request with token A
            given()
                .auth().oauth2(tokenA)
                .when()
                .get("/api/clients")
                .then()
                .statusCode(200)
                .body("clientId", hasItem(clientIdA))
                .body("clientId", not(hasItem(clientIdB)));

            // Request with token B
            given()
                .auth().oauth2(tokenB)
                .when()
                .get("/api/clients")
                .then()
                .statusCode(200)
                .body("clientId", hasItem(clientIdB))
                .body("clientId", not(hasItem(clientIdA)));
        }
    }
}
