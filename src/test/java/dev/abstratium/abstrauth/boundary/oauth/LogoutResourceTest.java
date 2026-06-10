package dev.abstratium.abstrauth.boundary.oauth;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
public class LogoutResourceTest {

    private static final String LOGOUT_PATH = "/api/auth/logout";

    @Test
    public void testLogoutGet_withoutParams_redirectsToRoot() {
        given()
                .redirects().follow(false)
                .when()
                .get(LOGOUT_PATH)
                .then()
                .statusCode(303)
                .header("Location", endsWith("/"))
                .cookie("XSRF-TOKEN", "");
    }

    @Test
    public void testLogoutGet_withPostLogoutUri_redirectsToUri() {
        given()
                .queryParam("post_logout_redirect_uri", "/dashboard")
                .redirects().follow(false)
                .when()
                .get(LOGOUT_PATH)
                .then()
                .statusCode(303)
                .header("Location", endsWith("/dashboard"));
    }

    @Test
    public void testLogoutGet_withPostLogoutUriAndState_redirectsToUriWithState() {
        given()
                .queryParam("post_logout_redirect_uri", "/dashboard")
                .queryParam("state", "abc123")
                .redirects().follow(false)
                .when()
                .get(LOGOUT_PATH)
                .then()
                .statusCode(303)
                .header("Location", endsWith("/dashboard?state=abc123"));
    }

    @Test
    public void testLogoutGet_withStateOnExistingQuery() {
        given()
                .queryParam("post_logout_redirect_uri", "/dashboard?tab=profile")
                .queryParam("state", "abc123")
                .redirects().follow(false)
                .when()
                .get(LOGOUT_PATH)
                .then()
                .statusCode(303)
                .header("Location", endsWith("/dashboard?tab=profile&state=abc123"));
    }

    @Test
    public void testLogoutPost_withoutParams_redirectsToRoot() {
        given()
                .redirects().follow(false)
                .when()
                .post(LOGOUT_PATH)
                .then()
                .statusCode(303)
                .header("Location", endsWith("/"));
    }

    @Test
    public void testLogoutPost_withParams_redirectsToUriWithState() {
        given()
                .queryParam("post_logout_redirect_uri", "/home")
                .queryParam("state", "xyz789")
                .redirects().follow(false)
                .when()
                .post(LOGOUT_PATH)
                .then()
                .statusCode(303)
                .header("Location", endsWith("/home?state=xyz789"));
    }
}
