package dev.abstratium.abstrauth.service;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.Test;

import jakarta.inject.Inject;

import static org.hamcrest.Matchers.*;

/**
 * Tests for the ConfigInfoContributor that adds runtime configuration to the /info endpoint.
 * The info endpoint is exposed on the management interface at port 9001 (test mode).
 */
@QuarkusTest
class ConfigInfoContributorTest {

    @Inject
    @ConfigProperty(name = "quarkus.management.test-port", defaultValue = "9001")
    int managementPort;

    @Test
    void testInfoEndpointIncludesCustomConfig() {
        RestAssured.given()
            .baseUri("http://localhost:" + managementPort)
            .when().get("/m/info")
            .then()
            .statusCode(200)
            .body("config", notNullValue())
            .body("config.allowSignup", notNullValue())
            .body("config.jwtIssuer", equalTo("https://abstrauth.abstratium.dev"))
            .body("config.googleRedirectUri", notNullValue())
            .body("config.rateLimitEnabled", notNullValue())
            .body("config.buildVersion", notNullValue())
            // In test mode, buildVersion may be @build.version@ placeholder or actual timestamp
            .body("config.buildVersion", anyOf(matchesRegex("\\d{14}"), equalTo("@build.version@")));
    }

    @Test
    void testInfoEndpointIncludesStandardSections() {
        RestAssured.given()
            .baseUri("http://localhost:" + managementPort)
            .when().get("/m/info")
            .then()
            .statusCode(200)
            .body("build", notNullValue())
            .body("build.name", equalTo("abstrauth"))
            .body("build.group", equalTo("dev.abstratium"))
            .body("git", notNullValue())
            .body("java", notNullValue())
            .body("os", notNullValue());
    }

    @Test
    void testInfoEndpointIncludesCustomBuildProperties() {
        RestAssured.given()
            .baseUri("http://localhost:" + managementPort)
            .when().get("/m/info")
            .then()
            .statusCode(200)
            .body("build.app-name", equalTo("abstrauth"))
            .body("build.oauth-redirect-uri", notNullValue());
    }
}
