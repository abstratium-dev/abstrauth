package dev.abstratium.abstrauth.boundary.oauth;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

import org.junit.jupiter.api.Test;

import dev.abstratium.abstrauth.entity.Account;
import dev.abstratium.abstrauth.entity.OAuthClient;
import dev.abstratium.abstrauth.non_multitenancy.service.NonMultitenancySubscriptionService;
import dev.abstratium.abstrauth.service.AccountService;
import dev.abstratium.abstrauth.service.OAuthClientService;
import dev.abstratium.abstrauth.service.OrganisationService;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.response.Response;
import jakarta.inject.Inject;
import jakarta.transaction.UserTransaction;

/**
 * Tests for Feature 11: Subscription Gate on Sign-In
 */
@QuarkusTest
public class SubscriptionGateTest {

    private static final String CLIENT_ID = "abstratium-abstrauth";
    private static final String REDIRECT_URI = "http://localhost:8080/api/auth/callback";

    @Inject
    AccountService accountService;

    @Inject
    OrganisationService organisationService;

    @Inject
    OAuthClientService oAuthClientService;

    @Inject
    NonMultitenancySubscriptionService subscriptionService;

    @Inject
    UserTransaction userTransaction;

    // ─────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────

    private Account createAccount(String suffix) throws Exception {
        userTransaction.begin();
        Account account = accountService.createAccount(
                "subgate_" + suffix + "@example.com",
                "SubGate " + suffix,
                "subgate_" + suffix,
                "Pass123!",
                AccountService.NATIVE,
                "SubGate Org " + suffix);
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
                .queryParam("scope", "openid profile email")
                .queryParam("code_challenge", codeChallenge)
                .queryParam("code_challenge_method", "S256")
                .redirects().follow(false)
                .get("/oauth2/authorize")
                .then().statusCode(303).extract().response();
        String location = r.getHeader("Location");
        int idx = location.lastIndexOf('/');
        return idx >= 0 ? location.substring(idx + 1) : null;
    }

    // ─────────────────────────────────────────────────────────
    // Tests
    // ─────────────────────────────────────────────────────────

    @Test
    public void testSignInSucceedsWhenSubscribed() throws Exception {
        long ts = System.currentTimeMillis();
        Account account = createAccount(ts + "_sub");

        String orgId = organisationService.listOrganisationsForAccount(account.getId()).get(0).getId();

        // Ensure subscribed (auto_subscribe = true by default, so this is idempotent)
        userTransaction.begin();
        subscriptionService.ensureSubscribed(orgId, CLIENT_ID, true);
        userTransaction.commit();

        String verifier = generateCodeVerifier();
        String challenge = generateCodeChallenge(verifier);
        String requestId = initiateAuthRequest(challenge);

        given()
                .formParam("username", "subgate_" + ts + "_sub")
                .formParam("password", "Pass123!")
                .formParam("request_id", requestId)
                .post("/oauth2/authorize/authenticate")
                .then().statusCode(200);
    }

    @Test
    public void testSignInBlockedWhenNoSubscriptionAndAutoSubscribeFalse() throws Exception {
        long ts = System.currentTimeMillis();
        createAccount(ts + "_nosub");

        // Set auto_subscribe = false on the client
        userTransaction.begin();
        OAuthClient client = oAuthClientService.findByClientId(CLIENT_ID).orElseThrow();
        client.setAutoSubscribe(false);
        oAuthClientService.update(client);
        userTransaction.commit();
        // Fresh org from createAccount has no subscription yet — no removal needed

        try {
            String verifier = generateCodeVerifier();
            String challenge = generateCodeChallenge(verifier);
            String requestId = initiateAuthRequest(challenge);

            given()
                    .formParam("username", "subgate_" + ts + "_nosub")
                    .formParam("password", "Pass123!")
                    .formParam("request_id", requestId)
                    .post("/oauth2/authorize/authenticate")
                    .then().statusCode(403);
        } finally {
            // Restore auto_subscribe = true so other tests are not affected
            userTransaction.begin();
            OAuthClient restore = oAuthClientService.findByClientId(CLIENT_ID).orElseThrow();
            restore.setAutoSubscribe(true);
            oAuthClientService.update(restore);
            userTransaction.commit();
        }
    }

    @Test
    public void testAutoSubscribeCreatesSubscriptionOnFirstSignIn() throws Exception {
        long ts = System.currentTimeMillis();
        Account account = createAccount(ts + "_autosub");

        String orgId = organisationService.listOrganisationsForAccount(account.getId()).get(0).getId();

        // Ensure no subscription exists yet for this fresh org
        assertFalse(subscriptionService.findNonMultitenancySubscription(orgId, CLIENT_ID).isPresent(),
                "Fresh org should have no subscription");

        String verifier = generateCodeVerifier();
        String challenge = generateCodeChallenge(verifier);
        String requestId = initiateAuthRequest(challenge);

        // Sign in — auto_subscribe = true so subscription is created
        given()
                .formParam("username", "subgate_" + ts + "_autosub")
                .formParam("password", "Pass123!")
                .formParam("request_id", requestId)
                .post("/oauth2/authorize/authenticate")
                .then().statusCode(200);

        assertTrue(subscriptionService.findNonMultitenancySubscription(orgId, CLIENT_ID).isPresent(),
                "Subscription should have been auto-created during sign-in");
    }

    @Test
    public void testEnsureSubscribedIsIdempotentWhenAlreadySubscribed() throws Exception {
        long ts = System.currentTimeMillis();
        Account account = createAccount(ts + "_idem");

        String orgId = organisationService.listOrganisationsForAccount(account.getId()).get(0).getId();

        // Subscribe first
        userTransaction.begin();
        subscriptionService.ensureSubscribed(orgId, CLIENT_ID, true);
        userTransaction.commit();

        // Sign in again — should not throw even though subscription already exists
        String verifier = generateCodeVerifier();
        String challenge = generateCodeChallenge(verifier);
        String requestId = initiateAuthRequest(challenge);

        given()
                .formParam("username", "subgate_" + ts + "_idem")
                .formParam("password", "Pass123!")
                .formParam("request_id", requestId)
                .post("/oauth2/authorize/authenticate")
                .then().statusCode(200);
    }
}
