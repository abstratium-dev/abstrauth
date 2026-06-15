package dev.abstratium.abstrauth.non_multitenancy.service;

import java.util.List;

import dev.abstratium.abstrauth.non_multitenancy.entity.NonMultitenancyClientRole;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

@ApplicationScoped
public class NonMultitenancyClientRoleService {

    @Inject
    EntityManager em;

    /**
     * Find all client roles where the specified client is the source client.
     * Uses NonMultitenancyClientRole to bypass the @TenantId discriminator.
     * This is needed for client credentials grant where the orgId is not yet known.
     *
     * @param srcClientId The source client ID
     * @return List of client roles for the source client
     */
    public List<NonMultitenancyClientRole> findBySrcClientId(String srcClientId) {
        return em.createQuery(
                "SELECT cr FROM NonMultitenancyClientRole cr WHERE cr.srcClientId = :srcClientId",
                NonMultitenancyClientRole.class)
                .setParameter("srcClientId", srcClientId)
                .getResultList();
    }
}
