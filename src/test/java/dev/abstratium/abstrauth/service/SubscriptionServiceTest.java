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
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.UserTransaction;

@QuarkusTest
public class SubscriptionServiceTest {

    // In test context JwtOrgResolver falls back to the default org — SubscriptionService
    // calls must use this orgId so that @TenantId matches the resolved tenant.
    private static final String DEFAULT_ORG_ID = "00000000-0000-0000-0000-000000000000";

    @Inject
    SubscriptionService subscriptionService;

    @Inject
    NonMultitenancySubscriptionService nonMultitenancySubscriptionService;

    @Inject
    OrganisationService organisationService;

    @Inject
    jakarta.persistence.EntityManager em;

    @Inject
    UserTransaction userTransaction;

    /**
     * Creates a fresh client in the default org and returns its clientId.
     * Each test uses its own client to avoid unique-constraint collisions across tests.
     */
    private String newClient() throws Exception {
        String clientId = "sub-test-" + System.nanoTime();
        userTransaction.begin();
        dev.abstratium.abstrauth.entity.OAuthClient client = new dev.abstratium.abstrauth.entity.OAuthClient();
        client.setClientId(clientId);
        client.setClientName("Sub Test " + clientId);
        client.setClientType("confidential");
        client.setRedirectUris("[]");
        client.setAllowedScopes("[]");
        client.setRequirePkce(false);
        em.persist(client);
        userTransaction.commit();
        return clientId;
    }

    /** Creates a new organisation and returns its id (used for isolation assertions only). */
    private String newOrg() throws Exception {
        userTransaction.begin();
        String orgId = organisationService.createOrganisation("Sub Test Org", null).getId();
        userTransaction.commit();
        return orgId;
    }

    @Test
    public void testSubscribe_createsSubscription() throws Exception {
        String clientId = newClient();

        Subscription sub = subscriptionService.subscribe(DEFAULT_ORG_ID, clientId);

        assertNotNull(sub.getId());
        assertEquals(DEFAULT_ORG_ID, sub.getOrgId());
        assertEquals(clientId, sub.getClientId());
        assertNotNull(sub.getCreatedAt());

        // Confirm via non-multitenancy read that orgId is stored correctly in the DB
        Optional<NonMultitenancySubscription> persisted =
                nonMultitenancySubscriptionService.findNonMultitenancySubscription(DEFAULT_ORG_ID, clientId);
        assertTrue(persisted.isPresent());
        assertEquals(DEFAULT_ORG_ID, persisted.get().getOrgId());
        assertEquals(clientId, persisted.get().getClientId());
    }

    @Test
    public void testSubscribe_throwsOnDuplicate() throws Exception {
        String clientId = newClient();
        subscriptionService.subscribe(DEFAULT_ORG_ID, clientId);

        assertThrows(IllegalArgumentException.class,
                () -> subscriptionService.subscribe(DEFAULT_ORG_ID, clientId),
                "Second subscribe to the same org+client must throw");
    }

    @Test
    public void testUnsubscribe_removesSubscription() throws Exception {
        String clientId = newClient();
        subscriptionService.subscribe(DEFAULT_ORG_ID, clientId);
        assertTrue(subscriptionService.subscriptionExists(DEFAULT_ORG_ID, clientId));

        subscriptionService.unsubscribe(DEFAULT_ORG_ID, clientId);

        assertFalse(subscriptionService.subscriptionExists(DEFAULT_ORG_ID, clientId));
        // Confirm removal is visible via non-multitenancy read
        assertFalse(nonMultitenancySubscriptionService
                .findNonMultitenancySubscription(DEFAULT_ORG_ID, clientId).isPresent());
    }

    @Test
    public void testUnsubscribe_throwsWhenNotSubscribed() throws Exception {
        String clientId = newClient();

        assertThrows(IllegalArgumentException.class,
                () -> subscriptionService.unsubscribe(DEFAULT_ORG_ID, clientId),
                "Unsubscribing when not subscribed must throw");
    }

    @Test
    public void testSubscriptionExists_falseBeforeSubscribe() throws Exception {
        String clientId = newClient();

        assertFalse(subscriptionService.subscriptionExists(DEFAULT_ORG_ID, clientId));
    }

    @Test
    public void testFindSubscription_returnsCorrectData() throws Exception {
        String clientId = newClient();
        subscriptionService.subscribe(DEFAULT_ORG_ID, clientId);

        Optional<Subscription> found = subscriptionService.findSubscription(DEFAULT_ORG_ID, clientId);

        assertTrue(found.isPresent());
        assertEquals(DEFAULT_ORG_ID, found.get().getOrgId());
        assertEquals(clientId, found.get().getClientId());
    }

    @Test
    public void testFindClientIdsByOrgId_includesSubscribedClients() throws Exception {
        String clientId1 = newClient();
        String clientId2 = newClient();
        subscriptionService.subscribe(DEFAULT_ORG_ID, clientId1);
        subscriptionService.subscribe(DEFAULT_ORG_ID, clientId2);

        List<String> clientIds = subscriptionService.findClientIdsByOrgId(DEFAULT_ORG_ID);

        assertTrue(clientIds.contains(clientId1));
        assertTrue(clientIds.contains(clientId2));
    }

    @Test
    public void testSubscriptionIsolatedByOrg() throws Exception {
        String clientId = newClient();
        subscriptionService.subscribe(DEFAULT_ORG_ID, clientId);

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
        subscriptionService.subscribe(DEFAULT_ORG_ID, clientId1);

        assertFalse(subscriptionService.subscriptionExists(DEFAULT_ORG_ID, clientId2),
                "Subscribing to one client must not create a subscription for another");
    }
}
