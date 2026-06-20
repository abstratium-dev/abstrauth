package dev.abstratium.abstrauth.non_multitenancy.service;

import java.util.List;

import dev.abstratium.abstrauth.non_multitenancy.entity.NonMultitenancyClientRole;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

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

    /**
     * Remove all ClientRole rows for a given target client and role across ALL organisations.
     * Uses NonMultitenancyClientRole to bypass the @TenantId discriminator.
     * This is called when a role is removed from the target client's allowed roles catalog.
     *
     * @param targetClientId The target client ID (the client whose allowed role is being removed)
     * @param role The role name
     */
    @Transactional
    public void removeClientRolesForTargetAndRole(String targetClientId, String role) {
        em.createQuery(
                "SELECT cr FROM NonMultitenancyClientRole cr WHERE cr.targetClientId = :targetClientId AND cr.role = :role",
                NonMultitenancyClientRole.class)
                .setParameter("targetClientId", targetClientId)
                .setParameter("role", role)
                .getResultList()
                .forEach(em::remove);
    }

    /**
     * Remove all ClientRole rows for a given target client and role from organisations
     * OTHER THAN the specified owning organisation.
     * Uses NonMultitenancyClientRole to bypass the @TenantId discriminator.
     * This is called when a role's availableToForeignOrgs is changed from true to false.
     *
     * @param targetClientId The target client ID
     * @param role The role name
     * @param owningOrgId The organisation ID to preserve roles in (the client owner)
     */
    @Transactional
    public void removeClientRolesForTargetAndRoleOutsideOrg(String targetClientId, String role, String owningOrgId) {
        em.createQuery(
                "SELECT cr FROM NonMultitenancyClientRole cr WHERE cr.targetClientId = :targetClientId AND cr.role = :role AND cr.orgId != :owningOrgId",
                NonMultitenancyClientRole.class)
                .setParameter("targetClientId", targetClientId)
                .setParameter("role", role)
                .setParameter("owningOrgId", owningOrgId)
                .getResultList()
                .forEach(em::remove);
    }
}
