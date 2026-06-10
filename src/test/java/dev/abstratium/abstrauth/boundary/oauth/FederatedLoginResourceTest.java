package dev.abstratium.abstrauth.boundary.oauth;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import dev.abstratium.abstrauth.entity.AuthorizationRequest;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

@QuarkusTest
public class FederatedLoginResourceTest {

    @Inject
    EntityManager em;

    private String createPendingRequest() {
        AuthorizationRequest req = new AuthorizationRequest();
        req.setId(UUID.randomUUID().toString());
        req.setClientId("test-client");
        req.setRedirectUri("http://localhost:8080/callback");
        req.setState("test-state");
        req.setScope("openid");
        req.setStatus("pending");
        req.setCodeChallenge("test-challenge");
        req.setCodeChallengeMethod("S256");
        req.setCreatedAt(java.time.LocalDateTime.now());
        req.setExpiresAt(java.time.LocalDateTime.now().plusMinutes(10));
        persist(req);
        return req.getId();
    }

    @Transactional
    void persist(AuthorizationRequest req) {
        em.persist(req);
    }

    @Test
    public void testInitiateGoogleLogin_missingRequestId_returns400() {
        given()
                .when()
                .get("/oauth2/federated/google")
                .then()
                .statusCode(400);
    }

    @Test
    public void testInitiateGoogleLogin_invalidRequestId_returns400() {
        given()
                .queryParam("request_id", "non-existent-id")
                .when()
                .get("/oauth2/federated/google")
                .then()
                .statusCode(400);
    }

    @Test
    public void testInitiateGoogleLogin_validRequestId_returns303() {
        String requestId = createPendingRequest();

        given()
                .queryParam("request_id", requestId)
                .redirects().follow(false)
                .when()
                .get("/oauth2/federated/google")
                .then()
                .statusCode(303)
                .header("Location", containsString("accounts.google.com"));
    }

    @Test
    public void testInitiateMicrosoftLogin_missingRequestId_returns400() {
        given()
                .when()
                .get("/oauth2/federated/microsoft")
                .then()
                .statusCode(400);
    }

    @Test
    public void testInitiateMicrosoftLogin_invalidRequestId_returns400() {
        given()
                .queryParam("request_id", "non-existent-id")
                .when()
                .get("/oauth2/federated/microsoft")
                .then()
                .statusCode(400);
    }

    @Test
    public void testInitiateMicrosoftLogin_validRequestId_returns303() {
        String requestId = createPendingRequest();

        given()
                .queryParam("request_id", requestId)
                .redirects().follow(false)
                .when()
                .get("/oauth2/federated/microsoft")
                .then()
                .statusCode(303)
                .header("Location", containsString("login.microsoftonline.com"));
    }
}
