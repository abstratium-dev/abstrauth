package dev.abstratium.abstrauth.non_multitenancy.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import dev.abstratium.abstrauth.entity.Organisation;
import dev.abstratium.abstrauth.non_multitenancy.entity.NonMultitenancySubscription;
import dev.abstratium.abstrauth.service.NoSubscriptionException;
import dev.abstratium.abstrauth.service.OrganisationService;
import dev.abstratium.abstrauth.util.TestDatabaseResetHelper;
import dev.abstratium.abstrauth.util.TestTransactionHelper;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;

@QuarkusTest
public class NonMultitenancyAuthorizationServiceTest {

    @Inject
    NonMultitenancyAuthorizationService nonMultitenancyAuthorizationService;

    @Inject
    NonMultitenancySubscriptionService subscriptionService;

    @Inject
    OrganisationService organisationService;

    @Inject
    EntityManager em;

    @Inject
    TestTransactionHelper transactionHelper;

    @Inject
    TestDatabaseResetHelper dbResetHelper;

    @BeforeEach
    public void resetContext() throws Exception {
        dbResetHelper.resetDatabase();
    }

    private dev.abstratium.abstrauth.entity.OAuthClient createClientWithFlags(String clientId, boolean publik, boolean autoSubscribe) throws Exception {
        transactionHelper.beginTransaction();
        var q = em.createQuery("SELECT c FROM OAuthClient c WHERE c.clientId = :cid", dev.abstratium.abstrauth.entity.OAuthClient.class);
        q.setParameter("cid", clientId);
        if (q.getResultList().isEmpty()) {
            dev.abstratium.abstrauth.entity.OAuthClient c = new dev.abstratium.abstrauth.entity.OAuthClient();
            c.setClientId(clientId);
            c.setClientName("Flag Test " + clientId);
            c.setClientType("confidential");
            c.setRedirectUris("[]");
            c.setAllowedScopes("[]");
            c.setRequirePkce(true);
            c.setPublik(publik);
            c.setAutoSubscribe(autoSubscribe);
            em.persist(c);
        }
        transactionHelper.commitTransaction();
        return em.createQuery("SELECT c FROM OAuthClient c WHERE c.clientId = :cid", dev.abstratium.abstrauth.entity.OAuthClient.class)
                 .setParameter("cid", clientId).getSingleResult();
    }

    @Test
    public void testCheckSubscription_publicAutoSubscribe_createsSubscription() throws Exception {
        String clientId = "publik_auto_" + System.nanoTime();
        createClientWithFlags(clientId, true, true);
        // Must use a real org: T_subscriptions has FK on org_id
        transactionHelper.beginTransaction();
        Organisation org = organisationService.createOrganisation("AutoSub Org " + System.nanoTime(), null);
        transactionHelper.commitTransaction();
        String orgId = org.getId();

        nonMultitenancyAuthorizationService.checkSubscription(orgId, clientId);

        assertTrue(subscriptionService.findNonMultitenancySubscription(orgId, clientId).isPresent(),
                "Subscription should have been auto-created for public+autoSubscribe client");
    }

    @Test
    public void testCheckSubscription_privateAutoSubscribeTrue_throwsNoSubscription() throws Exception {
        String clientId = "private_auto_" + System.nanoTime();
        // No subscription will be created (throws before insert), so any string orgId is fine here
        String orgId = java.util.UUID.randomUUID().toString();
        createClientWithFlags(clientId, false, true);

        assertThrows(NoSubscriptionException.class,
                () -> nonMultitenancyAuthorizationService.checkSubscription(orgId, clientId),
                "Private client must not auto-subscribe even when autoSubscribe=true");
    }

    @Test
    public void testCheckSubscription_publicAutoSubscribeFalse_throwsNoSubscription() throws Exception {
        String clientId = "publik_noauto_" + System.nanoTime();
        // No subscription will be created (throws before insert), so any string orgId is fine here
        String orgId = java.util.UUID.randomUUID().toString();
        createClientWithFlags(clientId, true, false);

        assertThrows(NoSubscriptionException.class,
                () -> nonMultitenancyAuthorizationService.checkSubscription(orgId, clientId),
                "Public client with autoSubscribe=false must require explicit subscription");
    }

    @Test
    public void testCheckSubscription_alreadySubscribed_doesNotThrow() throws Exception {
        String clientId = "already_sub_" + System.nanoTime();
        createClientWithFlags(clientId, false, false);
        // Must use a real org and commit subscription before calling checkSubscription
        transactionHelper.beginTransaction();
        Organisation org = organisationService.createOrganisation("AlreadySub Org " + System.nanoTime(), null);
        String orgId = org.getId();
        NonMultitenancySubscription sub = new NonMultitenancySubscription();
        sub.setOrgId(orgId);
        sub.setClientId(clientId);
        em.persist(sub);
        transactionHelper.commitTransaction();

        assertDoesNotThrow(() -> nonMultitenancyAuthorizationService.checkSubscription(orgId, clientId),
                "Already-subscribed org must be allowed regardless of publik/autoSubscribe flags");
    }

    @Test
    public void testApproveWithSubscriptionCheck_requestNotFound_throwsNotFoundException() {
        // Use a non-existent request ID
        String nonExistentRequestId = java.util.UUID.randomUUID().toString();
        String accountId = java.util.UUID.randomUUID().toString();
        String orgId = java.util.UUID.randomUUID().toString();

        jakarta.ws.rs.NotFoundException thrown = assertThrows(jakarta.ws.rs.NotFoundException.class,
                () -> nonMultitenancyAuthorizationService.approveWithSubscriptionCheck(nonExistentRequestId, accountId, "native", orgId),
                "Should throw NotFoundException when request doesn't exist");
        assertEquals("Authorization request not found", thrown.getMessage());
    }

}
