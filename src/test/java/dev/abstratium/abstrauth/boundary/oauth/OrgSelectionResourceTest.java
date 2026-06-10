package dev.abstratium.abstrauth.boundary.oauth;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

import static dev.abstratium.abstrauth.entity.AuthorizationRequest.SESSION_COOKIE_NAME;

import dev.abstratium.abstrauth.entity.Account;
import dev.abstratium.abstrauth.entity.Organisation;
import dev.abstratium.abstrauth.service.AccountService;
import dev.abstratium.abstrauth.service.AuthorizationService;
import dev.abstratium.abstrauth.service.OrganisationService;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import jakarta.inject.Inject;
import jakarta.transaction.UserTransaction;

@QuarkusTest
public class OrgSelectionResourceTest {

    private static final String CLIENT_ID = "abstratium-abstrauth";
    private static final String REDIRECT_URI = "http://localhost:8080/api/auth/callback";

    @Inject
    AccountService accountService;

    @Inject
    OrganisationService organisationService;

    @Inject
    AuthorizationService authorizationService;

    @Inject
    UserTransaction userTransaction;

    // ─────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────

    private Account createAccount(String suffix) throws Exception {
        userTransaction.begin();
        Account account = accountService.createAccount(
                "orgsel_" + suffix + "@example.com",
                "OrgSel " + suffix,
                "orgsel_" + suffix,
                "Pass123!",
                AccountService.NATIVE,
                "OrgSel Org " + suffix);
        userTransaction.commit();
        return account;
    }

    private String generateCodeVerifier() {
        byte[] code = new byte[32];
        new SecureRandom().nextBytes(code);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(code);
    }

    private String generateCodeChallenge(String verifier) throws Exception {
        byte[] hash = MessageDigest.getInstance("SHA-256")
                .digest(verifier.getBytes(StandardCharsets.UTF_8));
        return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
    }

    private String initiateAuthRequest(String codeChallenge) {
        Response r = given()
                .queryParam("response_type", "code")
                .queryParam("client_id", CLIENT_ID)
                .queryParam("redirect_uri", REDIRECT_URI)
                .queryParam("scope", "openid")
                .queryParam("code_challenge", codeChallenge)
                .queryParam("code_challenge_method", "S256")
                .redirects().follow(false)
                .get("/oauth2/authorize")
                .then().statusCode(303).extract().response();
        return extractPathId(r.getHeader("Location"), "/signin/");
    }

    private String extractPathId(String url, String prefix) {
        Pattern p = Pattern.compile(Pattern.quote(prefix) + "([^/?]+)");
        Matcher m = p.matcher(url);
        return m.find() ? m.group(1) : null;
    }

    private Organisation addSecondOrg(Account owner) throws Exception {
        userTransaction.begin();
        Organisation org = organisationService.createOrganisation(
                "Second Org " + System.currentTimeMillis(), owner.getId());
        organisationService.addOwner(org.getId(), owner.getId());
        organisationService.addMember(org.getId(), owner.getId());
        userTransaction.commit();
        return org;
    }

    // ─────────────────────────────────────────────────────────
    // Single-org: authenticate returns no orgSelectionUri
    // ─────────────────────────────────────────────────────────

    @Test
    public void singleOrg_authenticate_returnsNoOrgSelectionUri() throws Exception {
        long ts = System.currentTimeMillis();
        createAccount(ts + "_single");
        String challenge = generateCodeChallenge(generateCodeVerifier());
        String requestId = initiateAuthRequest(challenge);

        given()
                .formParam("username", "orgsel_" + ts + "_single")
                .formParam("password", "Pass123!")
                .formParam("request_id", requestId)
                .when()
                .post("/oauth2/authorize/authenticate")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("redirectTo", nullValue());
    }

    @Test
    public void singleOrg_authenticate_requestIsApproved() throws Exception {
        long ts = System.currentTimeMillis();
        createAccount(ts + "_singleapproved");
        String challenge = generateCodeChallenge(generateCodeVerifier());
        String requestId = initiateAuthRequest(challenge);

        given()
                .formParam("username", "orgsel_" + ts + "_singleapproved")
                .formParam("password", "Pass123!")
                .formParam("request_id", requestId)
                .post("/oauth2/authorize/authenticate")
                .then().statusCode(200);

        // After authenticate, request must be approved (not org_selection_pending)
        String status = authorizationService.findAuthorizationRequest(requestId)
                .map(r -> r.getStatus()).orElse(null);
        assertEquals("approved", status);
    }

    @Test
    public void singleOrg_consentApprove_redirectsWithCode() throws Exception {
        long ts = System.currentTimeMillis();
        createAccount(ts + "_singleflow");
        String verifier = generateCodeVerifier();
        String challenge = generateCodeChallenge(verifier);
        String requestId = initiateAuthRequest(challenge);

        given()
                .formParam("username", "orgsel_" + ts + "_singleflow")
                .formParam("password", "Pass123!")
                .formParam("request_id", requestId)
                .post("/oauth2/authorize/authenticate")
                .then().statusCode(200);

        given()
                .formParam("consent", "approve")
                .formParam("request_id", requestId)
                .redirects().follow(false)
                .post("/oauth2/authorize")
                .then()
                .statusCode(303)
                .header("Location", containsString("code="));
    }

    // ─────────────────────────────────────────────────────────
    // Multi-org: authenticate returns orgSelectionUri, status = org_selection_pending
    // ─────────────────────────────────────────────────────────

    @Test
    public void multiOrg_authenticate_returnsOrgSelectionUri() throws Exception {
        long ts = System.currentTimeMillis();
        Account account = createAccount(ts + "_multi");
        addSecondOrg(account);

        String challenge = generateCodeChallenge(generateCodeVerifier());
        String requestId = initiateAuthRequest(challenge);

        given()
                .formParam("username", "orgsel_" + ts + "_multi")
                .formParam("password", "Pass123!")
                .formParam("request_id", requestId)
                .when()
                .post("/oauth2/authorize/authenticate")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("redirectTo", equalTo("/org-selection/" + requestId));
    }

    @Test
    public void multiOrg_authenticate_statusIsOrgSelectionPending() throws Exception {
        long ts = System.currentTimeMillis();
        Account account = createAccount(ts + "_multipend");
        addSecondOrg(account);

        String challenge = generateCodeChallenge(generateCodeVerifier());
        String requestId = initiateAuthRequest(challenge);

        given()
                .formParam("username", "orgsel_" + ts + "_multipend")
                .formParam("password", "Pass123!")
                .formParam("request_id", requestId)
                .post("/oauth2/authorize/authenticate")
                .then().statusCode(200);

        String status = authorizationService.findAuthorizationRequest(requestId)
                .map(r -> r.getStatus()).orElse(null);
        assertEquals("org_selection_pending", status);
    }

    // ─────────────────────────────────────────────────────────
    // GET /api/org-selection/{requestId}
    // ─────────────────────────────────────────────────────────

    @Test
    public void getOrgList_pendingRequest_returnsOrgs() throws Exception {
        long ts = System.currentTimeMillis();
        Account account = createAccount(ts + "_getlist");
        addSecondOrg(account);

        String challenge = generateCodeChallenge(generateCodeVerifier());
        String requestId = initiateAuthRequest(challenge);

        given()
                .formParam("username", "orgsel_" + ts + "_getlist")
                .formParam("password", "Pass123!")
                .formParam("request_id", requestId)
                .post("/oauth2/authorize/authenticate")
                .then().statusCode(200);

        given()
                .when()
                .get("/api/org-selection/" + requestId)
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("$", hasSize(2))
                .body("id", hasItems(notNullValue()));
    }

    @Test
    public void getOrgList_unknownRequest_returns404() {
        given()
                .get("/api/org-selection/nonexistent-request-id")
                .then()
                .statusCode(404);
    }

    @Test
    public void getOrgList_pendingApprovedRequest_returns404() throws Exception {
        long ts = System.currentTimeMillis();
        createAccount(ts + "_getapproved");

        String challenge = generateCodeChallenge(generateCodeVerifier());
        String requestId = initiateAuthRequest(challenge);

        // Authenticate (single org → approved)
        given()
                .formParam("username", "orgsel_" + ts + "_getapproved")
                .formParam("password", "Pass123!")
                .formParam("request_id", requestId)
                .post("/oauth2/authorize/authenticate")
                .then().statusCode(200);

        // Already approved → not valid for org selection
        given()
                .get("/api/org-selection/" + requestId)
                .then()
                .statusCode(404);
    }

    // ─────────────────────────────────────────────────────────
    // POST /api/org-selection
    // ─────────────────────────────────────────────────────────

    @Test
    public void postOrgSelection_validChoice_returnsConsentRequired() throws Exception {
        long ts = System.currentTimeMillis();
        Account account = createAccount(ts + "_postok");
        Organisation org1 = organisationService.listOrganisationsForAccount(account.getId()).get(0);
        addSecondOrg(account);

        String challenge = generateCodeChallenge(generateCodeVerifier());
        String requestId = initiateAuthRequest(challenge);

        Response authResponse = given()
                .formParam("username", "orgsel_" + ts + "_postok")
                .formParam("password", "Pass123!")
                .formParam("request_id", requestId)
                .post("/oauth2/authorize/authenticate")
                .then().statusCode(200)
                .extract().response();
        String sessionCookie = authResponse.getCookie(SESSION_COOKIE_NAME);

        given()
                .contentType(ContentType.URLENC)
                .cookie(SESSION_COOKIE_NAME, sessionCookie)
                .formParam("request_id", requestId)
                .formParam("org_id", org1.getId())
                .when()
                .post("/api/org-selection")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("consentRequired", is(true));
    }

    @Test
    public void postOrgSelection_validChoice_requestIsApproved() throws Exception {
        long ts = System.currentTimeMillis();
        Account account = createAccount(ts + "_postapproved");
        Organisation org1 = organisationService.listOrganisationsForAccount(account.getId()).get(0);
        addSecondOrg(account);

        String challenge = generateCodeChallenge(generateCodeVerifier());
        String requestId = initiateAuthRequest(challenge);

        Response authResponse = given()
                .formParam("username", "orgsel_" + ts + "_postapproved")
                .formParam("password", "Pass123!")
                .formParam("request_id", requestId)
                .post("/oauth2/authorize/authenticate")
                .then().statusCode(200)
                .extract().response();
        String sessionCookie = authResponse.getCookie(SESSION_COOKIE_NAME);

        given()
                .contentType(ContentType.URLENC)
                .cookie(SESSION_COOKIE_NAME, sessionCookie)
                .formParam("request_id", requestId)
                .formParam("org_id", org1.getId())
                .post("/api/org-selection")
                .then().statusCode(200);

        String status = authorizationService.findAuthorizationRequest(requestId)
                .map(r -> r.getStatus()).orElse(null);
        assertEquals("approved", status);

        String storedOrgId = authorizationService.findAuthorizationRequest(requestId)
                .map(r -> r.getOrgId()).orElse(null);
        assertEquals(org1.getId(), storedOrgId);
    }

    @Test
    public void postOrgSelection_notMember_returns403() throws Exception {
        long ts = System.currentTimeMillis();
        Account owner = createAccount(ts + "_notmemown");
        Account other = createAccount(ts + "_notmemoth");
        addSecondOrg(owner);

        String challenge = generateCodeChallenge(generateCodeVerifier());
        String requestId = initiateAuthRequest(challenge);

        given()
                .formParam("username", "orgsel_" + ts + "_notmemown")
                .formParam("password", "Pass123!")
                .formParam("request_id", requestId)
                .post("/oauth2/authorize/authenticate")
                .then().statusCode(200);

        // owner tries to pick other's org they aren't a member of
        // The backend extracts account_id from the authenticated request and verifies membership
        Organisation otherOrg = organisationService.listOrganisationsForAccount(other.getId()).get(0);

        given()
                .contentType(ContentType.URLENC)
                .formParam("request_id", requestId)
                .formParam("org_id", otherOrg.getId())
                .when()
                .post("/api/org-selection")
                .then()
                .statusCode(403); // Forbidden - authenticated but not a member of the org
    }

    @Test
    public void postOrgSelection_missingParams_returns400() {
        given()
                .contentType(ContentType.URLENC)
                .formParam("request_id", "some-id")
                .when()
                .post("/api/org-selection")
                .then()
                .statusCode(400);
    }

    @Test
    public void postOrgSelection_afterApproval_allowsConsentToComplete() throws Exception {
        long ts = System.currentTimeMillis();
        Account account = createAccount(ts + "_fullflow");
        Organisation org1 = organisationService.listOrganisationsForAccount(account.getId()).get(0);
        addSecondOrg(account);

        String verifier = generateCodeVerifier();
        String challenge = generateCodeChallenge(verifier);
        String requestId = initiateAuthRequest(challenge);

        Response authResponse = given()
                .formParam("username", "orgsel_" + ts + "_fullflow")
                .formParam("password", "Pass123!")
                .formParam("request_id", requestId)
                .post("/oauth2/authorize/authenticate")
                .then().statusCode(200)
                .extract().response();
        String sessionCookie = authResponse.getCookie(SESSION_COOKIE_NAME);

        given()
                .contentType(ContentType.URLENC)
                .cookie(SESSION_COOKIE_NAME, sessionCookie)
                .formParam("request_id", requestId)
                .formParam("org_id", org1.getId())
                .post("/api/org-selection")
                .then().statusCode(200);

        // Consent should now work since request is approved
        given()
                .formParam("consent", "approve")
                .formParam("request_id", requestId)
                .redirects().follow(false)
                .post("/oauth2/authorize")
                .then()
                .statusCode(303)
                .header("Location", containsString("code="));
    }
}
