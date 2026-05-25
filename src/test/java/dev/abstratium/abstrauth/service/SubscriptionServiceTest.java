package dev.abstratium.abstrauth.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import dev.abstratium.abstrauth.entity.Subscription;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;

@QuarkusTest
public class SubscriptionServiceTest {

    // Known client_id values inserted by import.sql
    private static final String CLIENT_A = "client-a";
    private static final String CLIENT_B = "client-b";
    private static final String CLIENT_X = "client-x";
    private static final String CLIENT_Y = "client-y";

    @Inject
    SubscriptionService subscriptionService;

    @Inject
    OrganisationService organisationService;

    private String newOrg() {
        return organisationService.createOrganisation("Sub Test Org", null).getId();
    }

    @Test
    public void testSubscribe() {
        String orgId = newOrg();

        Subscription subscription = subscriptionService.subscribe(orgId, CLIENT_A);

        assertNotNull(subscription.getId());
        assertEquals(orgId, subscription.getOrgId());
        assertEquals(CLIENT_A, subscription.getClientId());
        assertNotNull(subscription.getCreatedAt());
    }

    @Test
    public void testSubscribeWhenAlreadySubscribedThrows() {
        String orgId = newOrg();
        subscriptionService.subscribe(orgId, CLIENT_A);

        assertThrows(IllegalArgumentException.class, () ->
                subscriptionService.subscribe(orgId, CLIENT_A));
    }

    @Test
    public void testSubscriptionExistsAfterSubscribe() {
        String orgId = newOrg();

        assertFalse(subscriptionService.subscriptionExists(orgId, CLIENT_A));
        subscriptionService.subscribe(orgId, CLIENT_A);
        assertTrue(subscriptionService.subscriptionExists(orgId, CLIENT_A));
    }

    @Test
    public void testUnsubscribe() {
        String orgId = newOrg();
        subscriptionService.subscribe(orgId, CLIENT_B);

        assertTrue(subscriptionService.subscriptionExists(orgId, CLIENT_B));
        subscriptionService.unsubscribe(orgId, CLIENT_B);
        assertFalse(subscriptionService.subscriptionExists(orgId, CLIENT_B));
    }

    @Test
    public void testUnsubscribeWhenNotSubscribedThrows() {
        String orgId = newOrg();

        assertThrows(IllegalArgumentException.class, () ->
                subscriptionService.unsubscribe(orgId, CLIENT_A));
    }

    @Test
    public void testFindSubscription() {
        String orgId = newOrg();

        Optional<Subscription> before = subscriptionService.findSubscription(orgId, CLIENT_X);
        assertFalse(before.isPresent());

        subscriptionService.subscribe(orgId, CLIENT_X);

        Optional<Subscription> after = subscriptionService.findSubscription(orgId, CLIENT_X);
        assertTrue(after.isPresent());
        assertEquals(orgId, after.get().getOrgId());
        assertEquals(CLIENT_X, after.get().getClientId());
    }

    @Test
    public void testSubscriptionExistsReturnsFalseForDifferentOrg() {
        String orgId = newOrg();
        String otherOrgId = newOrg();
        subscriptionService.subscribe(orgId, CLIENT_Y);

        assertFalse(subscriptionService.subscriptionExists(otherOrgId, CLIENT_Y),
                "Different org should not have a subscription");
    }

    @Test
    public void testSubscriptionExistsReturnsFalseForDifferentClient() {
        String orgId = newOrg();
        subscriptionService.subscribe(orgId, CLIENT_A);

        assertFalse(subscriptionService.subscriptionExists(orgId, CLIENT_B),
                "Different client should not have a subscription");
    }

    @Test
    public void testResubscribeAfterUnsubscribe() {
        String orgId = newOrg();
        subscriptionService.subscribe(orgId, CLIENT_A);
        subscriptionService.unsubscribe(orgId, CLIENT_A);

        Subscription reSubscription = subscriptionService.subscribe(orgId, CLIENT_A);
        assertNotNull(reSubscription.getId());
        assertTrue(subscriptionService.subscriptionExists(orgId, CLIENT_A));
    }
}
