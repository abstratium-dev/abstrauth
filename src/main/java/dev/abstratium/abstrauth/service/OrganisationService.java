package dev.abstratium.abstrauth.service;

import dev.abstratium.abstrauth.entity.Organisation;
import dev.abstratium.abstrauth.entity.OrganisationAccount;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class OrganisationService {

    static final String ROLE_OWNER = "owner";
    static final String ROLE_MEMBER = "member";

    @Inject
    EntityManager em;

    @Transactional
    public Organisation createOrganisation(String name, String createdByAccountId) {
        Organisation org = new Organisation();
        org.setName(name);
        org.setCreatedByAccountId(createdByAccountId);
        em.persist(org);
        return org;
    }

    public Optional<Organisation> findById(String orgId) {
        return Optional.ofNullable(em.find(Organisation.class, orgId));
    }

    @Transactional
    public Organisation updateName(String orgId, String name) {
        Organisation org = em.find(Organisation.class, orgId);
        if (org != null) {
            org.setName(name);
        }
        return org;
    }

    public List<Organisation> listOrganisationsForAccount(String accountId) {
        return em.createQuery(
                "SELECT o FROM Organisation o WHERE o.id IN " +
                "(SELECT oa.id.orgId FROM OrganisationAccount oa WHERE oa.id.accountId = :accountId AND oa.id.role = :role)",
                Organisation.class)
                .setParameter("accountId", accountId)
                .setParameter("role", ROLE_MEMBER)
                .getResultList();
    }

    @Transactional
    public OrganisationAccount addMember(String orgId, String accountId) {
        if (isMember(orgId, accountId)) {
            throw new IllegalArgumentException("Account is already a member of this organisation");
        }
        OrganisationAccount oa = new OrganisationAccount();
        oa.setId(new OrganisationAccount.Id(orgId, accountId, ROLE_MEMBER));
        em.persist(oa);
        return oa;
    }

    @Transactional
    public OrganisationAccount addOwner(String orgId, String accountId) {
        if (isOwner(orgId, accountId)) {
            throw new IllegalArgumentException("Account is already an owner of this organisation");
        }
        OrganisationAccount oa = new OrganisationAccount();
        oa.setId(new OrganisationAccount.Id(orgId, accountId, ROLE_OWNER));
        em.persist(oa);
        return oa;
    }

    @Transactional
    public void removeMember(String orgId, String accountId) {
        // First check if this account is an owner
        Optional<OrganisationAccount> ownerRow = findOwnerRow(orgId, accountId);
        if (ownerRow.isPresent()) {
            // Check if this is the last owner
            long ownerCount = countOwners(orgId);
            if (ownerCount <= 1) {
                throw new IllegalStateException("Cannot remove the last owner of an organisation");
            }
            em.remove(ownerRow.get());
            return;
        }

        // Not an owner, try to find as member
        OrganisationAccount memberRow = findMemberRow(orgId, accountId)
                .orElseThrow(() -> new IllegalArgumentException("Account is not a member of this organisation"));

        em.remove(memberRow);
    }

    public List<String> getRolesForAccount(String orgId, String accountId) {
        return em.createQuery(
                "SELECT oa.id.role FROM OrganisationAccount oa WHERE oa.id.orgId = :orgId AND oa.id.accountId = :accountId",
                String.class)
                .setParameter("orgId", orgId)
                .setParameter("accountId", accountId)
                .getResultList();
    }

    public boolean isMember(String orgId, String accountId) {
        return findMemberRow(orgId, accountId).isPresent();
    }

    public boolean isOwner(String orgId, String accountId) {
        return findOwnerRow(orgId, accountId).isPresent();
    }

    private Optional<OrganisationAccount> findMemberRow(String orgId, String accountId) {
        return em.createQuery(
                "SELECT oa FROM OrganisationAccount oa WHERE oa.id.orgId = :orgId AND oa.id.accountId = :accountId AND oa.id.role = :role",
                OrganisationAccount.class)
                .setParameter("orgId", orgId)
                .setParameter("accountId", accountId)
                .setParameter("role", ROLE_MEMBER)
                .getResultStream()
                .findFirst();
    }

    public Optional<OrganisationAccount> findOwnerRow(String orgId, String accountId) {
        return em.createQuery(
                "SELECT oa FROM OrganisationAccount oa WHERE oa.id.orgId = :orgId AND oa.id.accountId = :accountId AND oa.id.role = :role",
                OrganisationAccount.class)
                .setParameter("orgId", orgId)
                .setParameter("accountId", accountId)
                .setParameter("role", ROLE_OWNER)
                .getResultStream()
                .findFirst();
    }

    private long countOwners(String orgId) {
        return em.createQuery(
                "SELECT COUNT(oa) FROM OrganisationAccount oa WHERE oa.id.orgId = :orgId AND oa.id.role = :role",
                Long.class)
                .setParameter("orgId", orgId)
                .setParameter("role", ROLE_OWNER)
                .getSingleResult();
    }
}
