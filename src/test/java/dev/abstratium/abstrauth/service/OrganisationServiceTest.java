package dev.abstratium.abstrauth.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;

import dev.abstratium.abstrauth.entity.Organisation;
import dev.abstratium.abstrauth.entity.OrganisationAccount;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;

@QuarkusTest
public class OrganisationServiceTest {

    private static final AtomicLong COUNTER = new AtomicLong(System.currentTimeMillis());

    @Inject
    OrganisationService organisationService;

    @Inject
    AccountService accountService;

    private String createTestAccount() {
        long id = COUNTER.incrementAndGet();
        String email = "org-test-" + id + "@example.com";
        return accountService.createAccount(email, "Test User", email, "Password1!", AccountService.NATIVE, "Test Org").getId();
    }

    @Test
    public void testCreateOrganisation() {
        Organisation org = organisationService.createOrganisation("Acme Corp");

        assertNotNull(org.getId());
        assertEquals("Acme Corp", org.getName());
        assertNotNull(org.getCreatedAt());
    }

    @Test
    public void testFindById() {
        Organisation org = organisationService.createOrganisation("Find Me");

        Optional<Organisation> found = organisationService.findById(org.getId());

        assertTrue(found.isPresent());
        assertEquals(org.getId(), found.get().getId());
        assertEquals("Find Me", found.get().getName());
    }

    @Test
    public void testFindByIdNotFound() {
        Optional<Organisation> found = organisationService.findById("non-existent-org-id");
        assertFalse(found.isPresent());
    }

    @Test
    public void testAddMemberAndListOrganisationsForAccount() {
        String accountId = createTestAccount();
        Organisation org = organisationService.createOrganisation("Test Org for Members");

        organisationService.addMember(org.getId(), accountId);

        List<Organisation> orgs = organisationService.listOrganisationsForAccount(accountId);
        assertTrue(orgs.stream().anyMatch(o -> o.getId().equals(org.getId())));
    }

    @Test
    public void testAddMemberTwiceThrows() {
        String accountId = createTestAccount();
        Organisation org = organisationService.createOrganisation("Duplicate Member Org");
        organisationService.addMember(org.getId(), accountId);

        assertThrows(IllegalArgumentException.class, () ->
                organisationService.addMember(org.getId(), accountId));
    }

    @Test
    public void testIsMember() {
        String accountId = createTestAccount();
        Organisation org = organisationService.createOrganisation("IsMember Org");

        assertFalse(organisationService.isMember(org.getId(), accountId));
        organisationService.addMember(org.getId(), accountId);
        assertTrue(organisationService.isMember(org.getId(), accountId));
    }

    @Test
    public void testAddOwnerAndIsOwner() {
        String accountId = createTestAccount();
        Organisation org = organisationService.createOrganisation("Owner Org");

        assertFalse(organisationService.isOwner(org.getId(), accountId));
        OrganisationAccount oa = organisationService.addOwner(org.getId(), accountId);

        assertNotNull(oa.getId());
        assertEquals(OrganisationService.ROLE_OWNER, oa.getRole());
        assertTrue(organisationService.isOwner(org.getId(), accountId));
    }

    @Test
    public void testAddOwnerTwiceThrows() {
        String accountId = createTestAccount();
        Organisation org = organisationService.createOrganisation("Double Owner Org");
        organisationService.addOwner(org.getId(), accountId);

        assertThrows(IllegalArgumentException.class, () ->
                organisationService.addOwner(org.getId(), accountId));
    }

    @Test
    public void testRemoveMember() {
        String accountId = createTestAccount();
        Organisation org = organisationService.createOrganisation("Remove Member Org");
        organisationService.addMember(org.getId(), accountId);

        assertTrue(organisationService.isMember(org.getId(), accountId));
        organisationService.removeMember(org.getId(), accountId);
        assertFalse(organisationService.isMember(org.getId(), accountId));
    }

    @Test
    public void testRemoveMemberNotMemberThrows() {
        String accountId = createTestAccount();
        Organisation org = organisationService.createOrganisation("Not Member Org");

        assertThrows(IllegalArgumentException.class, () ->
                organisationService.removeMember(org.getId(), accountId));
    }

    @Test
    public void testRemoveOwnerWhenMultipleOwners() {
        String owner1 = createTestAccount();
        String owner2 = createTestAccount();
        Organisation org = organisationService.createOrganisation("Multi Owner Org");
        organisationService.addOwner(org.getId(), owner1);
        organisationService.addOwner(org.getId(), owner2);

        organisationService.removeMember(org.getId(), owner1);

        assertFalse(organisationService.isOwner(org.getId(), owner1));
        assertTrue(organisationService.isOwner(org.getId(), owner2));
    }

    @Test
    public void testRemoveLastOwnerThrows() {
        String owner = createTestAccount();
        Organisation org = organisationService.createOrganisation("Single Owner Org");
        organisationService.addOwner(org.getId(), owner);

        assertThrows(IllegalStateException.class, () ->
                organisationService.removeMember(org.getId(), owner));
    }

    @Test
    public void testRemoveNonMemberThrows() {
        String owner1 = createTestAccount();
        String owner2 = createTestAccount();
        String nonMember = createTestAccount();
        Organisation org = organisationService.createOrganisation("Remove Non-Member");
        organisationService.addOwner(org.getId(), owner1);
        organisationService.addOwner(org.getId(), owner2);

        assertThrows(IllegalArgumentException.class, () ->
                organisationService.removeMember(org.getId(), nonMember));
    }

    @Test
    public void testGetRolesForAccount_ownerAndMember() {
        String accountId = createTestAccount();
        Organisation org = organisationService.createOrganisation("Roles Org Owner+Member");
        organisationService.addOwner(org.getId(), accountId);
        organisationService.addMember(org.getId(), accountId);

        List<String> roles = organisationService.getRolesForAccount(org.getId(), accountId);

        assertEquals(2, roles.size());
        assertTrue(roles.contains(OrganisationService.ROLE_OWNER));
        assertTrue(roles.contains(OrganisationService.ROLE_MEMBER));
    }

    @Test
    public void testGetRolesForAccount_memberOnly() {
        String accountId = createTestAccount();
        Organisation org = organisationService.createOrganisation("Roles Org Member Only");
        organisationService.addMember(org.getId(), accountId);

        List<String> roles = organisationService.getRolesForAccount(org.getId(), accountId);

        assertEquals(1, roles.size());
        assertTrue(roles.contains(OrganisationService.ROLE_MEMBER));
    }

    @Test
    public void testGetRolesForAccount_noMembership_returnsEmpty() {
        String accountId = createTestAccount();
        Organisation org = organisationService.createOrganisation("Roles Org No Member");

        List<String> roles = organisationService.getRolesForAccount(org.getId(), accountId);

        assertTrue(roles.isEmpty());
    }

    @Test
    public void testListOrganisationsForAccountReturnsOnlyMemberOrgs() {
        String accountId = createTestAccount();
        String ownerOnlyAccountId = createTestAccount();

        Organisation orgA = organisationService.createOrganisation("Org A for list");
        Organisation orgB = organisationService.createOrganisation("Org B for list");
        Organisation orgC = organisationService.createOrganisation("Org C not member");

        organisationService.addMember(orgA.getId(), accountId);
        organisationService.addMember(orgB.getId(), accountId);
        organisationService.addOwner(orgC.getId(), ownerOnlyAccountId);

        List<Organisation> orgs = organisationService.listOrganisationsForAccount(accountId);

        List<String> orgIds = orgs.stream().map(Organisation::getId).toList();
        assertTrue(orgIds.contains(orgA.getId()), "Should contain orgA");
        assertTrue(orgIds.contains(orgB.getId()), "Should contain orgB");
        assertFalse(orgIds.contains(orgC.getId()), "Should not contain orgC (account is not a member of orgC)");
    }
}
