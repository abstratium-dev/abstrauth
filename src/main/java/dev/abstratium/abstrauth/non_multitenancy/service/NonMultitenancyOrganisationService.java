package dev.abstratium.abstrauth.non_multitenancy.service;

import dev.abstratium.abstrauth.non_multitenancy.entity.NonMultitenancyOrganisation;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import java.util.Optional;

/**
 * Non-multitenancy service for organisation operations.
 *
 * This service bypasses Hibernate's @TenantId discriminator to perform
 * cross-tenant operations. Used for cascade deletions that must remove
 * related entities across all organizations.
 */
@ApplicationScoped
public class NonMultitenancyOrganisationService {

    @Inject
    EntityManager em;

    /**
     * Find an organisation by ID across all organisations.
     * Uses NonMultitenancyOrganisation to bypass the @TenantId discriminator.
     *
     * @param orgId The organisation ID
     * @return Optional containing the organisation if found
     */
    public Optional<NonMultitenancyOrganisation> findById(String orgId) {
        return Optional.ofNullable(em.find(NonMultitenancyOrganisation.class, orgId));
    }

    /**
     * Delete an organisation and all its subscriptions across ALL organisations
     * using JPA cascade.
     *
     * This uses NonMultitenancyOrganisation which has CascadeType.REMOVE on
     * the subscriptions and accounts collections, ensuring complete deletion.
     *
     * @param orgId The organisation ID to delete
     * @return true if organisation was found and deleted, false if not found
     */
    @Transactional
    public boolean deleteOrganisationWithCascade(String orgId) {
        Optional<NonMultitenancyOrganisation> orgOpt = findById(orgId);
        if (orgOpt.isEmpty()) {
            return false;
        }

        NonMultitenancyOrganisation org = orgOpt.get();

        em.remove(org);
        return true;
    }
}
