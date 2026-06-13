import { test, expect, Page } from '@playwright/test';
import {
    signInAsAdmin,
    navigateToSigninPage,
    ADMIN_EMAIL,
    signInViaInviteLink
} from '../pages/signin.page';
import { navigateToAccounts, navigateToClients, signout } from '../pages/header';
import { addAccount, addRoleToAccount, tryDeleteAccount } from '../pages/accounts.page';
import { addClient, deleteClientIfExists } from '../pages/clients.page';
import { approveAuthorization, verifySignedIn } from '../pages/authorize.page';
import { changePassword } from '../pages/change-password.page';
import {
    navigateToOrganisations,
    createOrganisation,
    getOrganisationNames,
    selectOrganisation,
    getOrgSelectionNames
} from '../pages/organisations.page';
import { dismissToasts } from '../pages/toast';

/*
 * E2E test for multitenancy scenarios.
 *
 * Verifies:
 *   - User invitation to an organisation
 *   - Client creation and role assignment across users
 *   - Organisation isolation (clients, accounts, roles)
 *   - Organisation creation and switching
 *   - Org-selection flow when a user belongs to multiple organisations
 */

const MULTITENANT_EMAIL = 'multitenant@abstratium.dev';
const MULTITENANT_NAME = 'Multitenant';
const MULTITENANT_PASSWORD = 'secretLongTenant';

const NEW_CLIENT_ID = 'test_multitenant_client';
const NEW_CLIENT_NAME = 'Test Multitenant Client';
const REDIRECT_URI = 'http://localhost:3333/callback';
const SCOPES = 'openid profile email';

// NOTE: This role must exist in the abstratium-abstrauth client's allowlist.
// We use 'manage-accounts' because it is known to be in the allowlist.
const SOME_OTHER_ROLE = 'manage-accounts';

/**
 * Helper: count how many sub-tile (role) entries exist for a given clientId
 * inside an account tile.
 */
async function countRolesForClient(page: Page, accountTile: any, clientId: string): Promise<number> {
    const roles = accountTile.locator(`.sub-tile[data-client-id="${clientId}"]`);
    return await roles.count();
}

/**
 * Helper: get all role names for a given clientId inside an account tile.
 */
async function getRoleNamesForClient(page: Page, accountTile: any, clientId: string): Promise<string[]> {
    const roles = accountTile.locator(`.sub-tile[data-client-id="${clientId}"]`);
    const count = await roles.count();
    const names: string[] = [];
    for (let i = 0; i < count; i++) {
        const text = await roles.nth(i).textContent();
        // Extract the role name — it's the text inside .role-link or the first line
        const roleMatch = text?.match(/([a-zA-Z0-9\-]+)/);
        if (roleMatch) names.push(roleMatch[1]);
    }
    return names;
}

/**
 * Helper: sign in as the multitenant user.
 * If the user belongs to multiple orgs, optionally select a specific one.
 */
async function signInAsMultitenant(page: Page, selectOrgName?: string) {
    console.log('Signing in as multitenant...');
    await navigateToSigninPage(page);

    const usernameInput = page.locator('#username');
    await expect(usernameInput).toBeVisible({ timeout: 10000 });
    await usernameInput.fill(MULTITENANT_EMAIL);
    await page.locator('#password').fill(MULTITENANT_PASSWORD);

    const signinButton = page.locator('#signin-button');
    await expect(signinButton).toBeEnabled({ timeout: 5000 });
    await signinButton.click();

    // After sign-in we land on either the consent page (single org)
    // or the org-selection page (multiple orgs).
    const approveButton = page.locator('#approve-button');
    const orgSelectionHeading = page.locator('h1').filter({ hasText: 'Select Organisation' });

    await Promise.race([
        approveButton.waitFor({ state: 'visible', timeout: 15000 }),
        orgSelectionHeading.waitFor({ state: 'visible', timeout: 15000 })
    ]);

    if (await orgSelectionHeading.isVisible().catch(() => false)) {
        console.log('Org selection page shown');
        if (selectOrgName) {
            await selectOrganisation(page, selectOrgName);
        } else {
            await page.locator('#select-org-button').click();
        }
        // After org selection the OAuth flow completes and we land on the home page.
        await expect(page.locator('#user-link')).toBeVisible({ timeout: 15000 });
    } else {
        await approveButton.click();
        await expect(page.locator('#user-link')).toBeVisible({ timeout: 10000 });
    }

    console.log('Signed in as multitenant successfully');
}

test('multitenancy end-to-end scenario', async ({ page }) => {
    test.setTimeout(180000);
    console.log('=== Starting Multitenancy Test ===');

    // ========================================================================
    // STEP 1: Admin invites a new user
    // ========================================================================
    console.log('\n--- Step 1: Admin invites multitenant@abstratium.dev ---');
    await signInAsAdmin(page);
    console.log('Admin signed in');

    await navigateToAccounts(page);

    // Clean up: delete the multitenant account if it exists from a previous run
    const existingTile = page.locator('.tile').filter({ hasText: MULTITENANT_EMAIL });
    const existingCount = await existingTile.count();
    if (existingCount > 0) {
        console.log(`Account ${MULTITENANT_EMAIL} already exists, deleting first...`);
        await tryDeleteAccount(page, MULTITENANT_EMAIL);
        console.log(`Deleted existing account ${MULTITENANT_EMAIL}`);
    }

    const inviteLink = await addAccount(page, MULTITENANT_EMAIL, MULTITENANT_NAME);
    console.log(`Invited ${MULTITENANT_EMAIL}, invite link received`);

    // ========================================================================
    // STEP 2: Admin creates a new client
    // ========================================================================
    console.log('\n--- Step 2: Admin creates a new client ---');
    await navigateToClients(page);
    await deleteClientIfExists(page, NEW_CLIENT_ID);
    const newClient = await addClient(
        page,
        NEW_CLIENT_ID,
        NEW_CLIENT_NAME,
        REDIRECT_URI,
        SCOPES
    );
    const actualNewClientId = newClient.clientId;
    console.log(`Created client '${actualNewClientId}'`);

    // ========================================================================
    // STEP 3: Admin assigns roles to the new user
    // ========================================================================
    console.log('\n--- Step 3: Admin assigns roles to the new user ---');
    await navigateToAccounts(page);

    await addRoleToAccount(page, MULTITENANT_EMAIL, actualNewClientId, 'user');
    console.log(`Assigned role 'user' on client '${actualNewClientId}' to ${MULTITENANT_EMAIL}`);

    await addRoleToAccount(page, MULTITENANT_EMAIL, 'abstratium-abstrauth', SOME_OTHER_ROLE);
    console.log(`Assigned role '${SOME_OTHER_ROLE}' on abstratium-abstrauth to ${MULTITENANT_EMAIL}`);

    // ========================================================================
    // STEP 4: Admin signs out
    // ========================================================================
    console.log('\n--- Step 4: Admin signs out ---');
    await signout(page);
    console.log('Admin signed out');

    // ========================================================================
    // STEP 5: Multitenant user signs in via invite link
    // ========================================================================
    console.log('\n--- Step 5: Multitenant user signs in ---');
    await signInViaInviteLink(page, inviteLink, MULTITENANT_EMAIL);
    console.log('Submitted sign-in via invite link');

    await approveAuthorization(page);
    console.log('Approved authorization');

    await changePassword(page, MULTITENANT_PASSWORD);
    console.log('Changed password');

    await verifySignedIn(page, MULTITENANT_NAME);
    console.log('Multitenant user is signed in');

    // Debug: log current org from header
    const orgLink = page.locator('#current-org-link');
    if (await orgLink.isVisible().catch(() => false)) {
        const orgName = await orgLink.textContent();
        console.log(`Current org in header: ${orgName?.trim()}`);
    } else {
        console.log('No current org link visible in header');
    }

    // ========================================================================
    // STEP 6: Multitenant views clients — exactly 2 visible
    // ========================================================================
    console.log('\n--- Step 6: Multitenant views clients ---');
    await dismissToasts(page);

    // Intercept the GET /api/clients response so we know when loading truly finishes
    const clientsResponsePromise = page.waitForResponse(
        response => response.url().includes('/api/clients') && response.request().method() === 'GET',
        { timeout: 15000 }
    );
    await navigateToClients(page);
    const clientsResponse = await clientsResponsePromise;
    console.log(`Clients API responded with status: ${clientsResponse.status()}`);

    // Give Angular a tick to render after the response arrives
    await page.waitForTimeout(300);

    // Check for any error displayed on the page
    const errorBox = page.locator('.error-box');
    if (await errorBox.isVisible().catch(() => false)) {
        const errorText = await errorBox.textContent();
        throw new Error(`Clients page error: ${errorText}`);
    }

    // Check for "no clients" info message
    const noClientsMessage = page.locator('.info-message');
    if (await noClientsMessage.isVisible().catch(() => false)) {
        const msgText = await noClientsMessage.textContent();
        console.log(`Info message on clients page: ${msgText?.trim()}`);
    }

    const clientCards = page.locator('.card');
    const clientCount = await clientCards.count();
    console.log(`Found ${clientCount} client card(s)`);

    // Debug: if 0 cards, dump the page text and API body to understand what's rendered
    if (clientCount === 0) {
        const bodyText = await page.locator('body').textContent();
        console.log('--- Clients page body text dump ---');
        console.log(bodyText?.substring(0, 2000));
        console.log('--- End dump ---');
        try {
            const responseBody = await clientsResponse.json();
            console.log('--- API response body ---');
            console.log(JSON.stringify(responseBody).substring(0, 2000));
            console.log('--- End API body ---');
        } catch (e) {
            console.log('Could not parse API response body');
        }
    }

    // We only assert that the two relevant clients are visible.
    // Total count may be higher if the DB already has other clients.
    await expect(
        page.locator('.card[data-client-id="abstratium-abstrauth"]')
    ).toBeVisible({ timeout: 5000 });
    console.log('abstratium-abstrauth client is visible');

    await expect(
        page.locator(`.card[data-client-id$="__${NEW_CLIENT_ID}"]`)
    ).toBeVisible({ timeout: 5000 });
    console.log(`New client '${NEW_CLIENT_ID}' is visible`);
    console.log('Step 6 passed: relevant clients visible');

    // ========================================================================
    // STEP 7 + 8: Multitenant views accounts and roles
    // ========================================================================
    console.log('\n--- Step 7+8: Multitenant views accounts and roles ---');
    await dismissToasts(page);

    const accountsResponsePromise = page.waitForResponse(
        response => response.url().includes('/api/accounts') && response.request().method() === 'GET',
        { timeout: 15000 }
    );
    await navigateToAccounts(page);
    const accountsResponse = await accountsResponsePromise;
    console.log(`Accounts API responded with status: ${accountsResponse.status()}`);

    // Give Angular a tick to render after the response arrives
    await page.waitForTimeout(300);

    const accountTiles = page.locator('.tile');
    const accountCount = await accountTiles.count();
    console.log(`Found ${accountCount} account tile(s)`);

    // Debug: if 0 accounts, dump page text and API body
    if (accountCount === 0) {
        const bodyText = await page.locator('body').textContent();
        console.log('--- Accounts page body text dump ---');
        console.log(bodyText?.substring(0, 2000));
        console.log('--- End dump ---');
        try {
            const responseBody = await accountsResponse.json();
            console.log('--- API response body ---');
            console.log(JSON.stringify(responseBody).substring(0, 2000));
            console.log('--- End API body ---');
        } catch (e) {
            console.log('Could not parse API response body');
        }
    }

    expect(accountCount).toBe(2);

    const multitenantTile = accountTiles.filter({ hasText: MULTITENANT_EMAIL });
    const adminTile = accountTiles.filter({ hasText: ADMIN_EMAIL });
    await expect(multitenantTile).toBeVisible({ timeout: 5000 });
    await expect(adminTile).toBeVisible({ timeout: 5000 });
    console.log('Both multitenant and admin accounts are visible');

    // Assert 'user' role for the new client
    const userRoleForNewClient = multitenantTile
        .locator('.sub-tile')
        .filter({ hasText: 'user' })
        .filter({ hasText: actualNewClientId });
    await expect(userRoleForNewClient).toBeVisible({ timeout: 5000 });
    console.log(`Multitenant has 'user' role for new client '${actualNewClientId}'`);

    // --- Per-org role isolation assertion (original org) ---
    // In the original org the admin assigned:
    //   - 'user' for abstratium-abstrauth (auto on first sign-in)
    //   - 'manage-accounts' for abstratium-abstrauth (explicitly assigned)
    // Total = 2 roles for abstratium-abstrauth.
    const abstrauthRoleCount = await countRolesForClient(page, multitenantTile, 'abstratium-abstrauth');
    console.log(`Roles for abstratium-abstrauth in original org: ${abstrauthRoleCount}`);
    expect(abstrauthRoleCount).toBe(2);

    await expect(
        multitenantTile.locator('.sub-tile').filter({ hasText: 'user' }).filter({ has: page.locator('[data-client-id="abstratium-abstrauth"]') })
    ).toBeVisible({ timeout: 5000 });
    await expect(
        multitenantTile.locator('.sub-tile').filter({ hasText: SOME_OTHER_ROLE }).filter({ has: page.locator('[data-client-id="abstratium-abstrauth"]') })
    ).toBeVisible({ timeout: 5000 });
    console.log(`Multitenant has 'user' and '${SOME_OTHER_ROLE}' for abstratium-abstrauth in original org`);
    console.log('Step 7+8 passed: accounts and roles are correct');

    // ========================================================================
    // STEP 9: View organisations — only one visible
    // ========================================================================
    console.log('\n--- Step 9: Multitenant views organisations ---');
    await dismissToasts(page);

    const orgsResponsePromise = page.waitForResponse(
        response => response.url().includes('/api/organisations') && response.request().method() === 'GET',
        { timeout: 15000 }
    );
    await navigateToOrganisations(page);
    const orgsResponse = await orgsResponsePromise;
    console.log(`Organisations API responded with status: ${orgsResponse.status()}`);
    await page.waitForTimeout(300);

    const orgNamesStep9 = await getOrganisationNames(page);
    console.log(`Organisations visible: ${orgNamesStep9.join(', ')}`);
    expect(orgNamesStep9.length).toBe(1);
    console.log('Step 9 passed: exactly 1 organisation visible');

    // ========================================================================
    // STEP 10: Create "second" org and sign out
    // ========================================================================
    console.log('\n--- Step 10: Create new org "second" and sign out ---');
    await createOrganisation(page, 'second');

    const orgNamesStep10 = await getOrganisationNames(page);
    console.log(`Organisations visible after creation: ${orgNamesStep10.join(', ')}`);
    expect(orgNamesStep10.length).toBe(2);
    expect(orgNamesStep10).toContain('second');
    console.log('Organisation "second" created and visible');

    await signout(page);
    console.log('Step 10 passed: signed out after creating "second"');

    // ========================================================================
    // STEP 11: Sign back in, select "second", verify clients
    // ========================================================================
    console.log('\n--- Step 11: Sign back in, select "second", verify clients ---');
    await signInAsMultitenant(page, 'second');

    const currentOrgLink = page.locator('#current-org-link');
    await expect(currentOrgLink).toBeVisible({ timeout: 5000 });
    const currentOrgName = await currentOrgLink.textContent();
    expect(currentOrgName?.trim()).toBe('second');
    console.log(`Signed into organisation '${currentOrgName?.trim()}'`);

    await dismissToasts(page);

    const clientsResponsePromise11 = page.waitForResponse(
        response => response.url().includes('/api/clients') && response.request().method() === 'GET',
        { timeout: 15000 }
    );
    await navigateToClients(page);
    const clientsResponse11 = await clientsResponsePromise11;
    console.log(`Clients API responded with status: ${clientsResponse11.status()}`);
    await page.waitForTimeout(300);

    const clientCardsStep11 = page.locator('.card');
    const clientCountStep11 = await clientCardsStep11.count();
    console.log(`Found ${clientCountStep11} client card(s) in "second" org`);

    await expect(
        page.locator('.card[data-client-id="abstratium-abstrauth"]')
    ).toBeVisible({ timeout: 5000 });
    console.log('abstratium-abstrauth client is visible in "second" org');
    console.log('Step 11 passed: abstratium-abstrauth visible in new org');

    // ========================================================================
    // STEP 12: View accounts in "second" org
    // ========================================================================
    console.log('\n--- Step 12: View accounts in "second" org ---');
    await dismissToasts(page);

    const accountsResponsePromise12 = page.waitForResponse(
        response => response.url().includes('/api/accounts') && response.request().method() === 'GET',
        { timeout: 15000 }
    );
    await navigateToAccounts(page);
    const accountsResponse12 = await accountsResponsePromise12;
    console.log(`Accounts API responded with status: ${accountsResponse12.status()}`);
    await page.waitForTimeout(300);

    const accountTilesStep12 = page.locator('.tile');
    const accountCountStep12 = await accountTilesStep12.count();
    console.log(`Found ${accountCountStep12} account tile(s) in "second" org`);
    expect(accountCountStep12).toBe(1);

    const ownTile = accountTilesStep12.filter({ hasText: MULTITENANT_EMAIL });
    await expect(ownTile).toBeVisible({ timeout: 5000 });
    console.log('Only own account is visible in "second" org');

    // --- Per-org role isolation assertion ("second" org) ---
    // In a brand-new org the owner automatically gets:
    //   - 'user' for abstratium-abstrauth (default on first sign-in)
    //   - 'manage-accounts' for abstratium-abstrauth (owner privilege)
    //   - 'manage-clients' for abstratium-abstrauth (owner privilege)
    // Total = 3 roles for abstratium-abstrauth.
    // This differs from the original org (2 roles), proving AccountRole is per-org.
    const abstrauthRoleCountSecond = await countRolesForClient(page, ownTile, 'abstratium-abstrauth');
    console.log(`Roles for abstratium-abstrauth in "second" org: ${abstrauthRoleCountSecond}`);
    expect(abstrauthRoleCountSecond).toBe(3);

    await expect(
        ownTile.locator('.sub-tile').filter({ hasText: 'user' }).filter({ has: page.locator('[data-client-id="abstratium-abstrauth"]') })
    ).toBeVisible({ timeout: 5000 });
    await expect(
        ownTile.locator('.sub-tile').filter({ hasText: 'manage-accounts' }).filter({ has: page.locator('[data-client-id="abstratium-abstrauth"]') })
    ).toBeVisible({ timeout: 5000 });
    await expect(
        ownTile.locator('.sub-tile').filter({ hasText: 'manage-clients' }).filter({ has: page.locator('[data-client-id="abstratium-abstrauth"]') })
    ).toBeVisible({ timeout: 5000 });
    console.log('Multitenant has user, manage-accounts, and manage-clients for abstratium-abstrauth in "second" org');
    console.log('Step 12 passed: 1 account visible with 3 roles for abstrauth');

    // ========================================================================
    // STEP 13: View organisations in "second" org — both should be visible
    // ========================================================================
    console.log('\n--- Step 13: View organisations in "second" org ---');
    await dismissToasts(page);

    const orgsResponsePromise13 = page.waitForResponse(
        response => response.url().includes('/api/organisations') && response.request().method() === 'GET',
        { timeout: 15000 }
    );
    await navigateToOrganisations(page);
    const orgsResponse13 = await orgsResponsePromise13;
    console.log(`Organisations API responded with status: ${orgsResponse13.status()}`);
    await page.waitForTimeout(300);

    const orgNamesStep13 = await getOrganisationNames(page);
    console.log(`Organisations visible: ${orgNamesStep13.join(', ')}`);
    expect(orgNamesStep13.length).toBe(2);
    expect(orgNamesStep13).toContain('second');
    console.log('Step 13 passed: both orgs are visible');

    // ========================================================================
    // STEP 14: Sign out, back in selecting original org, verify roles
    // ========================================================================
    console.log('\n--- Step 14: Switch back to original org, verify roles ---');
    await signout(page);
    console.log('Signed out');

    // Sign back in and select the original organisation (auto-selected by lastOrgId
    // will be "second", so we need to explicitly select the original one).
    await navigateToSigninPage(page);
    const usernameInput14 = page.locator('#username');
    await expect(usernameInput14).toBeVisible({ timeout: 10000 });
    await usernameInput14.fill(MULTITENANT_EMAIL);
    await page.locator('#password').fill(MULTITENANT_PASSWORD);
    await page.locator('#signin-button').click();

    // Wait for org-selection page (we now have 2 orgs)
    const orgSelectionHeading14 = page.locator('h1').filter({ hasText: 'Select Organisation' });
    await expect(orgSelectionHeading14).toBeVisible({ timeout: 15000 });
    console.log('Org selection page shown for re-login');

    // Assert both org names are present on the selection page
    const selectionNames = await getOrgSelectionNames(page);
    console.log(`Org-selection options: ${selectionNames.join(', ')}`);
    expect(selectionNames.length).toBe(2);

    // Select the original org (NOT "second")
    // We identify it by picking the one that is NOT "second".
    const originalOrgName = selectionNames.find(n => n !== 'second');
    expect(originalOrgName).toBeTruthy();
    console.log(`Selecting original org: '${originalOrgName}'`);

    await selectOrganisation(page, originalOrgName!);

    // After org selection we land on the home page.
    await expect(page.locator('#user-link')).toBeVisible({ timeout: 15000 });
    console.log('Signed into original organisation');

    // Navigate to accounts and verify roles
    await dismissToasts(page);

    const accountsResponsePromise14 = page.waitForResponse(
        response => response.url().includes('/api/accounts') && response.request().method() === 'GET',
        { timeout: 15000 }
    );
    await navigateToAccounts(page);
    const accountsResponse14 = await accountsResponsePromise14;
    console.log(`Accounts API responded with status: ${accountsResponse14.status()}`);
    await page.waitForTimeout(300);

    const accountTiles14 = page.locator('.tile');
    const ownTile14 = accountTiles14.filter({ hasText: MULTITENANT_EMAIL });
    await expect(ownTile14).toBeVisible({ timeout: 5000 });

    // --- Per-org role isolation assertion (back to original org) ---
    // After switching back, the role count for abstratium-abstrauth must
    // be exactly 2 again (user + manage-accounts), NOT 3 as in "second" org.
    const abstrauthRoleCount14 = await countRolesForClient(page, ownTile14, 'abstratium-abstrauth');
    console.log(`Roles for abstratium-abstrauth after switching back to original org: ${abstrauthRoleCount14}`);
    expect(abstrauthRoleCount14).toBe(2);

    await expect(
        ownTile14.locator('.sub-tile').filter({ hasText: 'user' }).filter({ has: page.locator('[data-client-id="abstratium-abstrauth"]') })
    ).toBeVisible({ timeout: 5000 });
    await expect(
        ownTile14.locator('.sub-tile').filter({ hasText: SOME_OTHER_ROLE }).filter({ has: page.locator('[data-client-id="abstratium-abstrauth"]') })
    ).toBeVisible({ timeout: 5000 });

    // Also confirm manage-clients (which existed in "second" org) is NOT present here
    const manageClientsInOriginal = ownTile14
        .locator('.sub-tile')
        .filter({ hasText: 'manage-clients' })
        .filter({ has: page.locator('[data-client-id="abstratium-abstrauth"]') });
    const manageClientsCount = await manageClientsInOriginal.count();
    expect(manageClientsCount).toBe(0);
    console.log(`Multitenant has 'user' and '${SOME_OTHER_ROLE}' for abstratium-abstrauth in original org (manage-clients absent)`);

    console.log('Step 14 passed: roles verified in original org');

    console.log('\n=== Multitenancy Test Completed Successfully ===');
});
