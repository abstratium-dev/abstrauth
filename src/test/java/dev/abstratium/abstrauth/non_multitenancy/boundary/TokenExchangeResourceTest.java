package dev.abstratium.abstrauth.non_multitenancy.boundary;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import dev.abstratium.abstrauth.entity.Account;
import dev.abstratium.abstrauth.entity.ClientAllowedRole;
import dev.abstratium.abstrauth.entity.ClientSecret;
import dev.abstratium.abstrauth.entity.OAuthClient;
import dev.abstratium.abstrauth.non_multitenancy.entity.NonMultitenancySubscription;
import dev.abstratium.abstrauth.service.AccountService;
import dev.abstratium.abstrauth.service.TokenRevocationService;
import dev.abstratium.abstrauth.util.TestDatabaseResetHelper;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.response.Response;
import io.smallrye.jwt.build.Jwt;
import jakarta.inject.Inject;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonString;
import jakarta.json.JsonValue;
import jakarta.persistence.EntityManager;
import jakarta.transaction.UserTransaction;

/**
 * Integration tests for the RFC 8693 Token Exchange endpoint (POST /oauth2/token/exchange).
 */
@QuarkusTest
public class TokenExchangeResourceTest {

    private static final String GRANT_TYPE = "urn:ietf:params:oauth:grant-type:token-exchange";
    private static final String TOKEN_TYPE = "urn:ietf:params:oauth:token-type:access_token";
    private static final String ABSTRAUTH_CLIENT_ID = "abstratium-abstrauth";
    private static final String ABSTRAUTH_CLIENT_SECRET = "dev-secret-CHANGE-IN-PROD"; // from V01.010 migration

    @Inject
    TestDatabaseResetHelper dbResetHelper;

    @Inject
    AccountService accountService;

    @Inject
    TokenRevocationService tokenRevocationService;

    @Inject
    EntityManager em;

    @Inject
    UserTransaction userTransaction;

    @ConfigProperty(name = "default.org.uuid")
    String defaultOrgId;

    @ConfigProperty(name = "mp.jwt.verify.issuer")
    String issuer;

    @BeforeEach
    public void setup() {
        dbResetHelper.resetDatabase();
    }

    // ─────────────────────────────────────────────────────────────────────
    // Validation / error path tests
    // ─────────────────────────────────────────────────────────────────────

    @Test
    public void testMissingGrantTypeReturns400() {
        given()
            .formParam("subject_token", "some.jwt.token")
            .formParam("subject_token_type", TOKEN_TYPE)
            .formParam("audience", ABSTRAUTH_CLIENT_ID)
            .formParam("client_id", ABSTRAUTH_CLIENT_ID)
            .formParam("client_secret", ABSTRAUTH_CLIENT_SECRET)
            .when().post("/oauth2/token/exchange")
            .then().statusCode(400)
            .body("error", equalTo("unsupported_grant_type"));
    }

    @Test
    public void testWrongGrantTypeReturns400() {
        given()
            .formParam("grant_type", "authorization_code")
            .formParam("subject_token", "some.jwt.token")
            .formParam("subject_token_type", TOKEN_TYPE)
            .formParam("audience", ABSTRAUTH_CLIENT_ID)
            .formParam("client_id", ABSTRAUTH_CLIENT_ID)
            .formParam("client_secret", ABSTRAUTH_CLIENT_SECRET)
            .when().post("/oauth2/token/exchange")
            .then().statusCode(400)
            .body("error", equalTo("unsupported_grant_type"));
    }

    @Test
    public void testMissingSubjectTokenReturns400() {
        given()
            .formParam("grant_type", GRANT_TYPE)
            .formParam("subject_token_type", TOKEN_TYPE)
            .formParam("audience", ABSTRAUTH_CLIENT_ID)
            .formParam("client_id", ABSTRAUTH_CLIENT_ID)
            .formParam("client_secret", ABSTRAUTH_CLIENT_SECRET)
            .when().post("/oauth2/token/exchange")
            .then().statusCode(400)
            .body("error", equalTo("invalid_request"))
            .body("error_description", containsString("subject_token is required"));
    }

    @Test
    public void testWrongSubjectTokenTypeReturns400() {
        given()
            .formParam("grant_type", GRANT_TYPE)
            .formParam("subject_token", "some.jwt.token")
            .formParam("subject_token_type", "urn:ietf:params:oauth:token-type:id_token")
            .formParam("audience", ABSTRAUTH_CLIENT_ID)
            .formParam("client_id", ABSTRAUTH_CLIENT_ID)
            .formParam("client_secret", ABSTRAUTH_CLIENT_SECRET)
            .when().post("/oauth2/token/exchange")
            .then().statusCode(400)
            .body("error", equalTo("invalid_request"))
            .body("error_description", containsString("subject_token_type"));
    }

    @Test
    public void testMissingAudienceReturns400() {
        given()
            .formParam("grant_type", GRANT_TYPE)
            .formParam("subject_token", "some.jwt.token")
            .formParam("subject_token_type", TOKEN_TYPE)
            .formParam("client_id", ABSTRAUTH_CLIENT_ID)
            .formParam("client_secret", ABSTRAUTH_CLIENT_SECRET)
            .when().post("/oauth2/token/exchange")
            .then().statusCode(400)
            .body("error", equalTo("invalid_request"))
            .body("error_description", containsString("audience is required"));
    }

    @Test
    public void testMissingClientIdReturns400() {
        given()
            .formParam("grant_type", GRANT_TYPE)
            .formParam("subject_token", "some.jwt.token")
            .formParam("subject_token_type", TOKEN_TYPE)
            .formParam("audience", ABSTRAUTH_CLIENT_ID)
            .when().post("/oauth2/token/exchange")
            .then().statusCode(400)
            .body("error", equalTo("invalid_request"))
            .body("error_description", containsString("client_id is required"));
    }

    @Test
    public void testMalformedJwtReturns400() {
        given()
            .formParam("grant_type", GRANT_TYPE)
            .formParam("subject_token", "not-a-jwt")
            .formParam("subject_token_type", TOKEN_TYPE)
            .formParam("audience", ABSTRAUTH_CLIENT_ID)
            .formParam("client_id", ABSTRAUTH_CLIENT_ID)
            .formParam("client_secret", ABSTRAUTH_CLIENT_SECRET)
            .when().post("/oauth2/token/exchange")
            .then().statusCode(400)
            .body("error", equalTo("invalid_request"))
            .body("error_description", containsString("valid JWT"));
    }

    @Test
    public void testInvalidSignatureSubjectTokenReturns400() throws Exception {
        String token = buildJwt(issuer, "user-123", defaultOrgId,
                Instant.now().plusSeconds(900), "openid", null);

        // Replace the signature with a different base64url string of the same length
        String signature = token.substring(token.lastIndexOf('.') + 1);
        String tamperedToken = token.substring(0, token.lastIndexOf('.') + 1)
                + "A".repeat(signature.length());

        given()
            .formParam("grant_type", GRANT_TYPE)
            .formParam("subject_token", tamperedToken)
            .formParam("subject_token_type", TOKEN_TYPE)
            .formParam("audience", ABSTRAUTH_CLIENT_ID)
            .formParam("client_id", ABSTRAUTH_CLIENT_ID)
            .formParam("client_secret", ABSTRAUTH_CLIENT_SECRET)
            .when().post("/oauth2/token/exchange")
            .then().statusCode(400)
            .body("error", equalTo("invalid_request"))
            .body("error_description", containsString("valid JWT"));
    }

    @Test
    public void testExpiredSubjectTokenReturns400() throws Exception {
        String expiredToken = buildJwt(issuer, "user-123", defaultOrgId,
                Instant.now().minusSeconds(3600), "openid", null);

        given()
            .formParam("grant_type", GRANT_TYPE)
            .formParam("subject_token", expiredToken)
            .formParam("subject_token_type", TOKEN_TYPE)
            .formParam("audience", ABSTRAUTH_CLIENT_ID)
            .formParam("client_id", ABSTRAUTH_CLIENT_ID)
            .formParam("client_secret", ABSTRAUTH_CLIENT_SECRET)
            .when().post("/oauth2/token/exchange")
            .then().statusCode(400)
            .body("error", equalTo("invalid_grant"))
            .body("error_description", containsString("expired"));
    }

    @Test
    public void testWrongIssuerSubjectTokenReturns400() throws Exception {
        String wrongIssuerToken = buildJwt("https://evil.example.com", "user-123", defaultOrgId,
                Instant.now().plusSeconds(900), "openid", null);

        given()
            .formParam("grant_type", GRANT_TYPE)
            .formParam("subject_token", wrongIssuerToken)
            .formParam("subject_token_type", TOKEN_TYPE)
            .formParam("audience", ABSTRAUTH_CLIENT_ID)
            .formParam("client_id", ABSTRAUTH_CLIENT_ID)
            .formParam("client_secret", ABSTRAUTH_CLIENT_SECRET)
            .when().post("/oauth2/token/exchange")
            .then().statusCode(400)
            .body("error", equalTo("invalid_request"))
            .body("error_description", containsString("issuer"));
    }

    @Test
    public void testRevokedSubjectTokenReturns400() throws Exception {
        String jti = java.util.UUID.randomUUID().toString();
        String token = buildJwtWithJti(issuer, "user-123", defaultOrgId,
                Instant.now().plusSeconds(900), "openid", jti);

        userTransaction.begin();
        tokenRevocationService.revokeToken(jti, "test_revocation");
        userTransaction.commit();

        given()
            .formParam("grant_type", GRANT_TYPE)
            .formParam("subject_token", token)
            .formParam("subject_token_type", TOKEN_TYPE)
            .formParam("audience", ABSTRAUTH_CLIENT_ID)
            .formParam("client_id", ABSTRAUTH_CLIENT_ID)
            .formParam("client_secret", ABSTRAUTH_CLIENT_SECRET)
            .when().post("/oauth2/token/exchange")
            .then().statusCode(400)
            .body("error", equalTo("invalid_grant"))
            .body("error_description", containsString("revoked"));
    }

    @Test
    public void testOnceExchangedTokenCanBeExchangedAgain() throws Exception {
        // depth 1 token (has one act) — should succeed, still within default max depth of 3
        Account account = accountService.createAccount(
            "depth1_user_" + System.currentTimeMillis() + "@example.com",
            "Depth1 User", "depth1_user_" + System.currentTimeMillis(),
            "Pass123!", AccountService.NATIVE, null);

        String depth1Token = Jwt.issuer(issuer)
            .subject(account.getId())
            .audience(ABSTRAUTH_CLIENT_ID)
            .claim("orgId", defaultOrgId)
            .claim("scope", "openid")
            .claim("client_id", ABSTRAUTH_CLIENT_ID)
            .claim("auth_method", "native")
            .claim("jti", java.util.UUID.randomUUID().toString())
            .claim("act", Json.createObjectBuilder().add("sub", "some-bff").build())
            .expiresAt(Instant.now().plusSeconds(900))
            .jws().keyId("abstrauth-key-1").sign();

        given()
            .formParam("grant_type", GRANT_TYPE)
            .formParam("subject_token", depth1Token)
            .formParam("subject_token_type", TOKEN_TYPE)
            .formParam("audience", ABSTRAUTH_CLIENT_ID)
            .formParam("client_id", ABSTRAUTH_CLIENT_ID)
            .formParam("client_secret", ABSTRAUTH_CLIENT_SECRET)
            .when().post("/oauth2/token/exchange")
            .then().statusCode(200)
            .body("access_token", notNullValue());
    }

    @Test
    public void testActChainIsNestedCorrectly() throws Exception {
        // Exchange a once-exchanged token; resulting act claim must nest the previous one
        Account account = accountService.createAccount(
            "actchain_user_" + System.currentTimeMillis() + "@example.com",
            "ActChain User", "actchain_user_" + System.currentTimeMillis(),
            "Pass123!", AccountService.NATIVE, null);

        JsonObject previousAct = Json.createObjectBuilder().add("sub", "upstream-bff").build();
        String depth1Token = Jwt.issuer(issuer)
            .subject(account.getId())
            .audience(ABSTRAUTH_CLIENT_ID)
            .claim("orgId", defaultOrgId)
            .claim("scope", "openid")
            .claim("client_id", ABSTRAUTH_CLIENT_ID)
            .claim("auth_method", "native")
            .claim("jti", java.util.UUID.randomUUID().toString())
            .claim("act", previousAct)
            .expiresAt(Instant.now().plusSeconds(900))
            .jws().keyId("abstrauth-key-1").sign();

        String newToken = given()
            .formParam("grant_type", GRANT_TYPE)
            .formParam("subject_token", depth1Token)
            .formParam("subject_token_type", TOKEN_TYPE)
            .formParam("audience", ABSTRAUTH_CLIENT_ID)
            .formParam("client_id", ABSTRAUTH_CLIENT_ID)
            .formParam("client_secret", ABSTRAUTH_CLIENT_SECRET)
            .when().post("/oauth2/token/exchange")
            .then().statusCode(200)
            .extract().path("access_token");

        JsonObject claims = decodeJwtPayload(newToken);
        assertTrue(claims.containsKey("act"), "act claim must be present");
        JsonObject act = claims.getJsonObject("act");
        assertEquals(ABSTRAUTH_CLIENT_ID, act.getString("sub"),
                "act.sub should be the calling client");
        assertTrue(act.containsKey("act"), "act.act must be present (RFC 8693 chain)");
        assertEquals("upstream-bff", act.getJsonObject("act").getString("sub"),
                "act.act.sub should carry the previous actor");
    }

    @Test
    public void testExchangeAtMaxDepthIsRejected() throws Exception {
        // Build a token already at depth 3 (default max); next exchange must be rejected
        // depth 3: act.act.act exists (three nested act levels)
        JsonObject actDepth3 = Json.createObjectBuilder()
            .add("sub", "service-c")
            .add("act", Json.createObjectBuilder()
                .add("sub", "service-b")
                .add("act", Json.createObjectBuilder()
                    .add("sub", "service-a")
                    .build())
                .build())
            .build();

        String maxDepthToken = Jwt.issuer(issuer)
            .subject("user-123")
            .audience(ABSTRAUTH_CLIENT_ID)
            .claim("orgId", defaultOrgId)
            .claim("scope", "openid")
            .claim("jti", java.util.UUID.randomUUID().toString())
            .claim("act", actDepth3)
            .expiresAt(Instant.now().plusSeconds(900))
            .jws().keyId("abstrauth-key-1").sign();

        given()
            .formParam("grant_type", GRANT_TYPE)
            .formParam("subject_token", maxDepthToken)
            .formParam("subject_token_type", TOKEN_TYPE)
            .formParam("audience", ABSTRAUTH_CLIENT_ID)
            .formParam("client_id", ABSTRAUTH_CLIENT_ID)
            .formParam("client_secret", ABSTRAUTH_CLIENT_SECRET)
            .when().post("/oauth2/token/exchange")
            .then().statusCode(400)
            .body("error", equalTo("invalid_request"))
            .body("error_description", containsString("depth"));
    }

    @Test
    public void testUnknownCallerClientReturns401() throws Exception {
        String token = buildJwt(issuer, "user-123", defaultOrgId,
                Instant.now().plusSeconds(900), "openid", null);

        given()
            .formParam("grant_type", GRANT_TYPE)
            .formParam("subject_token", token)
            .formParam("subject_token_type", TOKEN_TYPE)
            .formParam("audience", ABSTRAUTH_CLIENT_ID)
            .formParam("client_id", "non-existent-client")
            .formParam("client_secret", "any-secret")
            .when().post("/oauth2/token/exchange")
            .then().statusCode(401)
            .body("error", equalTo("invalid_client"));
    }

    @Test
    public void testUnknownAudienceReturns400() throws Exception {
        // caller = abstrauth (public client, no secret needed)
        String token = buildJwt(issuer, "user-123", defaultOrgId,
                Instant.now().plusSeconds(900), "openid", null);

        given()
            .formParam("grant_type", GRANT_TYPE)
            .formParam("subject_token", token)
            .formParam("subject_token_type", TOKEN_TYPE)
            .formParam("audience", "non-existent-audience")
            .formParam("client_id", ABSTRAUTH_CLIENT_ID)
            .formParam("client_secret", ABSTRAUTH_CLIENT_SECRET)
            .when().post("/oauth2/token/exchange")
            .then().statusCode(400)
            .body("error", equalTo("invalid_request"))
            .body("error_description", containsString("audience client does not exist"));
    }

    @Test
    public void testNoSubscriptionToAudienceReturns400() throws Exception {
        // Create a second client that the default org is NOT subscribed to
        String targetClientId = createConfidentialClientInDefaultOrg("exchange-target-unsub");

        // Remove any subscription that may have been auto-created
        userTransaction.begin();
        em.createQuery("DELETE FROM NonMultitenancySubscription s WHERE s.orgId = :orgId AND s.clientId = :clientId")
            .setParameter("orgId", defaultOrgId)
            .setParameter("clientId", targetClientId)
            .executeUpdate();
        userTransaction.commit();

        String token = buildJwt(issuer, "user-123", defaultOrgId,
                Instant.now().plusSeconds(900), "openid", null);

        given()
            .formParam("grant_type", GRANT_TYPE)
            .formParam("subject_token", token)
            .formParam("subject_token_type", TOKEN_TYPE)
            .formParam("audience", targetClientId)
            .formParam("client_id", ABSTRAUTH_CLIENT_ID)
            .formParam("client_secret", ABSTRAUTH_CLIENT_SECRET)
            .when().post("/oauth2/token/exchange")
            .then().statusCode(400)
            .body("error", equalTo("unauthorized_client"))
            .body("error_description", containsString("not subscribed to the requested audience"));
    }

    @Test
    public void testScopeExceedingOriginalReturns400() throws Exception {
        // abstrauth is subscribed to itself in the default org by default
        String token = buildJwt(issuer, "user-123", defaultOrgId,
                Instant.now().plusSeconds(900), "openid", null);

        given()
            .formParam("grant_type", GRANT_TYPE)
            .formParam("subject_token", token)
            .formParam("subject_token_type", TOKEN_TYPE)
            .formParam("audience", ABSTRAUTH_CLIENT_ID)
            .formParam("client_id", ABSTRAUTH_CLIENT_ID)
            .formParam("client_secret", ABSTRAUTH_CLIENT_SECRET)
            .formParam("scope", "openid profile email api:write")  // more than original "openid"
            .when().post("/oauth2/token/exchange")
            .then().statusCode(400)
            .body("error", equalTo("invalid_scope"));
    }

    // ─────────────────────────────────────────────────────────────────────
    // Integration / happy path tests
    // ─────────────────────────────────────────────────────────────────────

    @Test
    public void testSuccessfulExchangeWithDefaultRoleSeeding() throws Exception {
        // Create a real account in the default org
        Account account = accountService.createAccount(
            "exchange_user_" + System.currentTimeMillis() + "@example.com",
            "Exchange User", "exchange_user_" + System.currentTimeMillis(),
            "Pass123!", AccountService.NATIVE, null);
        String orgId = defaultOrgId;

        // Create a target client with a default role, subscribed to default org
        String targetClientId = createClientWithDefaultRole("exchange-target", "user", orgId);

        // Build a valid subject token (abstrauth client is subscribed to default org)
        String subjectToken = Jwt.issuer(issuer)
            .subject(account.getId())
            .audience(ABSTRAUTH_CLIENT_ID)
            .claim("orgId", orgId)
            .claim("scope", "openid profile")
            .claim("client_id", ABSTRAUTH_CLIENT_ID)
            .claim("auth_method", "native")
            .claim("jti", java.util.UUID.randomUUID().toString())
            .expiresAt(Instant.now().plusSeconds(900))
            .jws().keyId("abstrauth-key-1").sign();

        Response response = given()
            .formParam("grant_type", GRANT_TYPE)
            .formParam("subject_token", subjectToken)
            .formParam("subject_token_type", TOKEN_TYPE)
            .formParam("audience", targetClientId)
            .formParam("client_id", ABSTRAUTH_CLIENT_ID)
            .formParam("client_secret", ABSTRAUTH_CLIENT_SECRET)
            .when().post("/oauth2/token/exchange")
            .then()
            .statusCode(200)
            .body("access_token", notNullValue())
            .body("token_type", equalTo("Bearer"))
            .body("issued_token_type", equalTo(TOKEN_TYPE))
            .extract().response();

        String accessToken = response.jsonPath().getString("access_token");
        assertNotNull(accessToken);

        JsonObject claims = decodeJwtPayload(accessToken);

        // sub is the original user, not the caller
        assertEquals(account.getId(), claims.getString("sub"), "sub should be original user account ID");
        assertEquals(targetClientId, getAudience(claims), "aud should be the target client");
        assertEquals(targetClientId, claims.getString("client_id"), "client_id should be target");
        assertEquals(orgId, claims.getString("orgId"), "orgId should be carried forward");
        assertEquals("native", claims.getString("auth_method"), "auth_method should be carried forward");

        // act claim identifies the caller
        assertTrue(claims.containsKey("act"), "act claim must be present");
        assertEquals(ABSTRAUTH_CLIENT_ID, claims.getJsonObject("act").getString("sub"),
                "act.sub should be the calling client");

        // groups should contain the seeded default role
        assertTrue(claims.containsKey("groups"), "groups claim should be present");
        String expectedGroup = targetClientId + "_user";
        boolean hasRole = false;
        var groups = claims.getJsonArray("groups");
        for (int i = 0; i < groups.size(); i++) {
            if (groups.getString(i).equals(expectedGroup)) {
                hasRole = true;
                break;
            }
        }
        assertTrue(hasRole, "groups should contain " + expectedGroup + ", got " + groups);

        // txn must be present
        assertTrue(claims.containsKey("txn"), "txn claim must be present");
    }

    @Test
    public void testSuccessfulExchangeWithScopeIntersection() throws Exception {
        Account account = accountService.createAccount(
            "scope_user_" + System.currentTimeMillis() + "@example.com",
            "Scope User", "scope_user_" + System.currentTimeMillis(),
            "Pass123!", AccountService.NATIVE, null);

        String subjectToken = Jwt.issuer(issuer)
            .subject(account.getId())
            .audience(ABSTRAUTH_CLIENT_ID)
            .claim("orgId", defaultOrgId)
            .claim("scope", "openid profile email")
            .claim("client_id", ABSTRAUTH_CLIENT_ID)
            .claim("auth_method", "native")
            .claim("jti", java.util.UUID.randomUUID().toString())
            .expiresAt(Instant.now().plusSeconds(900))
            .jws().keyId("abstrauth-key-1").sign();

        Response response = given()
            .formParam("grant_type", GRANT_TYPE)
            .formParam("subject_token", subjectToken)
            .formParam("subject_token_type", TOKEN_TYPE)
            .formParam("audience", ABSTRAUTH_CLIENT_ID)
            .formParam("client_id", ABSTRAUTH_CLIENT_ID)
            .formParam("client_secret", ABSTRAUTH_CLIENT_SECRET)
            .formParam("scope", "openid profile")  // subset of original
            .when().post("/oauth2/token/exchange")
            .then()
            .statusCode(200)
            .extract().response();

        String grantedScope = response.jsonPath().getString("scope");
        // Scope must be a subset — both openid and profile should be present
        assertTrue(grantedScope.contains("openid"), "openid should be in granted scope");
        assertTrue(grantedScope.contains("profile"), "profile should be in granted scope");
        assertTrue(!grantedScope.contains("email"), "email should NOT be in granted scope (not requested)");
    }

    @Test
    public void testExchangeWithContextParameterInherited() throws Exception {
        Account account = accountService.createAccount(
            "ctx_user_" + System.currentTimeMillis() + "@example.com",
            "Context User", "ctx_user_" + System.currentTimeMillis(),
            "Pass123!", AccountService.NATIVE, null);

        String subjectToken = Jwt.issuer(issuer)
            .subject(account.getId())
            .audience(ABSTRAUTH_CLIENT_ID)
            .claim("orgId", defaultOrgId)
            .claim("scope", "openid")
            .claim("client_id", ABSTRAUTH_CLIENT_ID)
            .claim("auth_method", "native")
            .claim("jti", java.util.UUID.randomUUID().toString())
            .expiresAt(Instant.now().plusSeconds(900))
            .jws().keyId("abstrauth-key-1").sign();

        Response response = given()
            .formParam("grant_type", GRANT_TYPE)
            .formParam("subject_token", subjectToken)
            .formParam("subject_token_type", TOKEN_TYPE)
            .formParam("audience", ABSTRAUTH_CLIENT_ID)
            .formParam("client_id", ABSTRAUTH_CLIENT_ID)
            .formParam("client_secret", ABSTRAUTH_CLIENT_SECRET)
            .formParam("context", "{\"orderId\":\"abc123\"}")
            .when().post("/oauth2/token/exchange")
            .then()
            .statusCode(200)
            .extract().response();

        String accessToken = response.jsonPath().getString("access_token");
        JsonObject claims = decodeJwtPayload(accessToken);
        assertTrue(claims.containsKey("ctx"), "ctx claim should be present");
        assertEquals("abc123", claims.getJsonObject("ctx").getString("orderId"),
                "ctx.orderId should match the provided context");
    }

    @Test
    public void testExchangeWithBasicAuthCaller() throws Exception {
        Account account = accountService.createAccount(
            "basicauth_user_" + System.currentTimeMillis() + "@example.com",
            "BasicAuth User", "basicauth_user_" + System.currentTimeMillis(),
            "Pass123!", AccountService.NATIVE, null);

        // Create a confidential target client
        String callerSecret = "test-secret-" + System.currentTimeMillis();
        String callerClientId = createConfidentialClientWithSecretInDefaultOrg("caller-basic", callerSecret);

        String subjectToken = Jwt.issuer(issuer)
            .subject(account.getId())
            .audience(callerClientId)
            .claim("orgId", defaultOrgId)
            .claim("scope", "openid")
            .claim("client_id", callerClientId)
            .claim("auth_method", "native")
            .claim("jti", java.util.UUID.randomUUID().toString())
            .expiresAt(Instant.now().plusSeconds(900))
            .jws().keyId("abstrauth-key-1").sign();

        String basicAuth = "Basic " + Base64.getEncoder().encodeToString(
            (callerClientId + ":" + callerSecret).getBytes(StandardCharsets.UTF_8));

        given()
            .header("Authorization", basicAuth)
            .formParam("grant_type", GRANT_TYPE)
            .formParam("subject_token", subjectToken)
            .formParam("subject_token_type", TOKEN_TYPE)
            .formParam("audience", ABSTRAUTH_CLIENT_ID)
            // client_id + client_secret NOT in form params — comes from Basic Auth
            .when().post("/oauth2/token/exchange")
            .then()
            .statusCode(200)
            .body("access_token", notNullValue());
    }

    @Test
    public void testDiscoveryAdvertisesTokenExchangeGrantType() {
        given()
            .when().get("/.well-known/openid-configuration")
            .then().statusCode(200)
            .body("grant_types_supported", org.hamcrest.Matchers.hasItem(GRANT_TYPE));
    }

    // ─────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────

    private String buildJwt(String iss, String sub, String orgId, Instant exp, String scope, String jti) {
        var builder = Jwt.issuer(iss)
            .subject(sub)
            .audience(ABSTRAUTH_CLIENT_ID)
            .claim("orgId", orgId)
            .claim("scope", scope)
            .claim("client_id", ABSTRAUTH_CLIENT_ID)
            .claim("auth_method", "native")
            .claim("jti", jti != null ? jti : java.util.UUID.randomUUID().toString())
            .expiresAt(exp);
        return builder.jws().keyId("abstrauth-key-1").sign();
    }

    private String buildJwtWithJti(String iss, String sub, String orgId, Instant exp, String scope, String jti) {
        return buildJwt(iss, sub, orgId, exp, scope, jti);
    }

    /** Creates a public client in the default org and subscribes it. */
    private String createConfidentialClientInDefaultOrg(String name) throws Exception {
        String clientId = name + "_" + System.currentTimeMillis();
        userTransaction.begin();
        OAuthClient client = new OAuthClient();
        client.setClientId(clientId);
        client.setClientName(name);
        client.setClientType("confidential");
        client.setRedirectUris("http://localhost:8080/callback");
        client.setAllowedScopes("openid");
        client.setRequirePkce(false);
        client.setPublik(true);
        client.setAutoSubscribe(false);
        em.persist(client);
        em.flush();
        userTransaction.commit();
        return clientId;
    }

    /** Creates a confidential client with a BCrypt secret and subscribes it to the default org. */
    private String createConfidentialClientWithSecretInDefaultOrg(String name, String plainSecret) throws Exception {
        String clientId = name + "_" + System.currentTimeMillis();
        userTransaction.begin();
        OAuthClient client = new OAuthClient();
        client.setClientId(clientId);
        client.setClientName(name);
        client.setClientType("confidential");
        client.setRedirectUris("http://localhost:8080/callback");
        client.setAllowedScopes("openid");
        client.setRequirePkce(false);
        client.setPublik(true);
        client.setAutoSubscribe(false);
        em.persist(client);

        ClientSecret secret = new ClientSecret();
        secret.setClientId(clientId);
        secret.setSecretHash(new BCryptPasswordEncoder().encode(plainSecret));
        secret.setDescription("test");
        secret.setActive(true);
        em.persist(secret);

        NonMultitenancySubscription sub = new NonMultitenancySubscription();
        sub.setOrgId(defaultOrgId);
        sub.setClientId(clientId);
        em.persist(sub);
        em.flush();
        userTransaction.commit();
        return clientId;
    }

    /** Creates a client with a default role in its allowed-roles catalog, subscribed to the default org. */
    private String createClientWithDefaultRole(String name, String roleName, String orgId) throws Exception {
        String clientId = name + "_" + System.currentTimeMillis();
        userTransaction.begin();
        OAuthClient client = new OAuthClient();
        client.setClientId(clientId);
        client.setClientName(name);
        client.setClientType("public");
        client.setRedirectUris("http://localhost:8080/callback");
        client.setAllowedScopes("openid");
        client.setRequirePkce(false);
        client.setPublik(true);
        client.setAutoSubscribe(false);
        em.persist(client);

        ClientAllowedRole allowedRole = new ClientAllowedRole();
        allowedRole.setClientId(clientId);
        allowedRole.setRole(roleName);
        allowedRole.setIsDefault(true);
        allowedRole.setAvailableToForeignOrgs(true);
        em.persist(allowedRole);

        NonMultitenancySubscription sub = new NonMultitenancySubscription();
        sub.setOrgId(orgId);
        sub.setClientId(clientId);
        em.persist(sub);
        em.flush();
        userTransaction.commit();
        return clientId;
    }

    private JsonObject decodeJwtPayload(String jwt) {
        String[] parts = jwt.split("\\.");
        assertEquals(3, parts.length, "JWT must have 3 parts");
        String json = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
        return Json.createReader(new StringReader(json)).readObject();
    }

    private String getAudience(JsonObject claims) {
        JsonValue aud = claims.get("aud");
        if (aud.getValueType() == JsonValue.ValueType.ARRAY) {
            return claims.getJsonArray("aud").getString(0);
        }
        return ((JsonString) aud).getString();
    }
}
