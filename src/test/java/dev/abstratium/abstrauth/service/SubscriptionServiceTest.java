package dev.abstratium.abstrauth.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import dev.abstratium.abstrauth.entity.Subscription;
import dev.abstratium.abstrauth.non_multitenancy.entity.NonMultitenancySubscription;
import dev.abstratium.abstrauth.non_multitenancy.service.NonMultitenancySubscriptionService;
import dev.abstratium.abstrauth.util.TestDatabaseResetHelper;
import dev.abstratium.abstrauth.util.TestTransactionHelper;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import org.junit.jupiter.api.BeforeEach;

@QuarkusTest
public class SubscriptionServiceTest {

    @Inject
    SubscriptionService subscriptionService;

    @Inject
    NonMultitenancySubscriptionService nonMultitenancySubscriptionService;

    @Inject
    OrganisationService organisationService;

    @Inject
    jakarta.persistence.EntityManager em;

    @Inject
    TestTransactionHelper transactionHelper;

    @Inject
    TestDatabaseResetHelper dbResetHelper;

    @Inject
    CurrentOrgContext currentOrgContext;

    @ConfigProperty(name = "default.org.uuid")
    String defaultOrgId;

    @BeforeEach
    public void resetDatabaseBeforeTest() throws Exception {
        dbResetHelper.resetDatabase();
        currentOrgContext.setOrgId(defaultOrgId);
    }

    /**
     * Creates a fresh client in the default org and returns its clientId.
     * Each test uses its own client to avoid unique-constraint collisions across tests.
     */
    private String newClient() throws Exception {
        String clientId = "sub-test-" + System.nanoTime();
        transactionHelper.beginTransaction();
        dev.abstratium.abstrauth.entity.OAuthClient client = new dev.abstratium.abstrauth.entity.OAuthClient();
        client.setClientId(clientId);
        client.setClientName("Sub Test " + clientId);
        client.setClientType("confidential");
        client.setRedirectUris("[]");
        client.setAllowedScopes("[]");
        client.setRequirePkce(false);
        em.persist(client);
        transactionHelper.commitTransaction();
        return clientId;
    }

    /** Creates a new organisation and returns its id (used for isolation assertions only). */
    private String newOrg() throws Exception {
        transactionHelper.beginTransaction();
        String orgId = organisationService.createOrganisation("Sub Test Org", null).getId();
        transactionHelper.commitTransaction();
        return orgId;
    }

    @Test
    public void testSubscribe_createsSubscription() throws Exception {
        String clientId = newClient();

        Subscription sub = subscriptionService.subscribe(defaultOrgId, clientId);

        assertNotNull(sub.getId());
        assertEquals(defaultOrgId, sub.getOrgId());
        assertEquals(clientId, sub.getClientId());
        assertNotNull(sub.getCreatedAt());

        // Confirm via non-multitenancy read that orgId is stored correctly in the DB
        Optional<NonMultitenancySubscription> persisted =
                nonMultitenancySubscriptionService.findNonMultitenancySubscription(defaultOrgId, clientId);
        assertTrue(persisted.isPresent());
        assertEquals(defaultOrgId, persisted.get().getOrgId());
        assertEquals(clientId, persisted.get().getClientId());
    }

    @Test
    public void testSubscribe_throwsOnDuplicate() throws Exception {
        String clientId = newClient();
        subscriptionService.subscribe(defaultOrgId, clientId);

        assertThrows(IllegalArgumentException.class,
                () -> subscriptionService.subscribe(defaultOrgId, clientId),
                "Second subscribe to the same org+client must throw");
    }

    @Test
    public void testUnsubscribe_removesSubscription() throws Exception {
        String clientId = newClient();
        subscriptionService.subscribe(defaultOrgId, clientId);
        assertTrue(subscriptionService.subscriptionExists(defaultOrgId, clientId));

        subscriptionService.unsubscribe(defaultOrgId, clientId);

        assertFalse(subscriptionService.subscriptionExists(defaultOrgId, clientId));
        // Confirm removal is visible via non-multitenancy read
        assertFalse(nonMultitenancySubscriptionService
                .findNonMultitenancySubscription(defaultOrgId, clientId).isPresent());
    }

    @Test
    public void testUnsubscribe_throwsWhenNotSubscribed() throws Exception {
        String clientId = newClient();

        assertThrows(IllegalArgumentException.class,
                () -> subscriptionService.unsubscribe(defaultOrgId, clientId),
                "Unsubscribing when not subscribed must throw");
    }

    @Test
    public void testSubscriptionExists_falseBeforeSubscribe() throws Exception {
        String clientId = newClient();

        assertFalse(subscriptionService.subscriptionExists(defaultOrgId, clientId));
    }

    @Test
    public void testFindSubscription_returnsCorrectData() throws Exception {
        String clientId = newClient();
        subscriptionService.subscribe(defaultOrgId, clientId);

        Optional<Subscription> found = subscriptionService.findSubscription(defaultOrgId, clientId);

        assertTrue(found.isPresent());
        assertEquals(defaultOrgId, found.get().getOrgId());
        assertEquals(clientId, found.get().getClientId());
    }

    @Test
    public void testFindClientIdsByOrgId_includesSubscribedClients() throws Exception {
        String clientId1 = newClient();
        String clientId2 = newClient();
        subscriptionService.subscribe(defaultOrgId, clientId1);
        subscriptionService.subscribe(defaultOrgId, clientId2);

        List<String> clientIds = subscriptionService.findClientIdsByOrgId(defaultOrgId);

        assertTrue(clientIds.contains(clientId1));
        assertTrue(clientIds.contains(clientId2));
    }

    @Test
    public void testSubscriptionIsolatedByOrg() throws Exception {
        String clientId = newClient();
        subscriptionService.subscribe(defaultOrgId, clientId);

        // A different org must not see the default org's subscription
        String otherOrgId = newOrg();
        assertFalse(nonMultitenancySubscriptionService
                .findNonMultitenancySubscription(otherOrgId, clientId).isPresent(),
                "Subscription for default org must not be visible under a different org");
    }

    @Test
    public void testSubscriptionIsolatedByClient() throws Exception {
        String clientId1 = newClient();
        String clientId2 = newClient();
        subscriptionService.subscribe(defaultOrgId, clientId1);

        assertFalse(subscriptionService.subscriptionExists(defaultOrgId, clientId2),
                "Subscribing to one client must not create a subscription for another");
    }
}
