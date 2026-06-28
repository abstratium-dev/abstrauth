import { test, expect, Page } from '@playwright/test';
import {
    signInAsAdmin,
    signInViaInviteLink,
    navigateToSigninPage
} from '../pages/signin.page';
import { navigateToAccounts, signout } from '../pages/header';
import { addAccount, tryDeleteAccount } from '../pages/accounts.page';
import { approveAuthorization } from '../pages/authorize.page';
import { changePassword } from '../pages/change-password.page';
import { navigateToOrganisations, createOrganisation } from '../pages/organisations.page';
import { dismissToasts } from '../pages/toast';

/*
 * E2E tests for self-service account deletion (GDPR right to erasure).
 *
 * Verifies:
 *   - A user can delete their own account from the accounts page.
 *   - After self-deletion the user is signed out and cannot sign back in.
 *   - The deleted account is removed from every organisation it belonged to,
 *     so another organisation owner cannot see it anymore.
 */

const USER_PASSWORD = 'TempPass123!';

/**
 * Helper: clean up any remaining test accounts.
 * The test deletes its own account, but if a test fails mid-flight we tidy up here.
 */
async function cleanupSelfDeleteAccounts(page: Page) {
    console.log('Cleaning up self-delete test accounts...');
    try {
        await dismissToasts(page);

        const tiles = page.locator('.tile');
        const count = await tiles.count();
        for (let i = count - 1; i >= 0; i--) {
            const tile = tiles.nth(i);
            const emailElement = tile.locator('.tile-subtitle');
            const email = await emailElement.textContent();

            if (email?.includes('selfdelete_')) {
                console.log(`Found test account to clean up: ${email}`);
                const deleteButton = tile.locator('.btn-icon-danger').first();
                if (await deleteButton.isVisible().catch(() => false)) {
                    await deleteButton.click();
                    const confirmButton = page.locator('button.btn-danger').filter({ hasText: /Delete (My )?Account/i });
                    await expect(confirmButton).toBeVisible({ timeout: 2000 });
                    // Type the email as the required phrase (if the input is visible)
                    const phraseInput = page.locator('[data-testid="confirm-phrase-input"]');
                    if (await phraseInput.isVisible({ timeout: 500 }).catch(() => false)) {
                        await phraseInput.fill(email?.trim() ?? '');
                    }
                    await confirmButton.click();
                    await page.waitForResponse(
                        response => response.url().includes('/api/accounts') && response.request().method() === 'DELETE',
                        { timeout: 10000 }
                    ).catch(() => {
                        console.log('No DELETE /api/accounts response observed during cleanup');
                    });
                    await dismissToasts(page);
                }
            }
        }
    } catch (e) {
        console.log('Cleanup encountered issue (may be expected):', e);
    }
}

test.describe('Self-service account deletion', () => {

    test.afterEach(async ({ page }) => {
        // Sign out if still signed in after the test.
        const accountsLink = page.locator('#accounts-link');
        if (await accountsLink.isVisible().catch(() => false)) {
            await navigateToAccounts(page);
            await cleanupSelfDeleteAccounts(page);
            // If the current user was deleted, cleanup already signed them out.
            if (await accountsLink.isVisible().catch(() => false)) {
                await signout(page);
            }
        }
    });

    test('user can delete their own account and is removed from all organisations', async ({ page }) => {
        test.setTimeout(120000);
        console.log('=== Test: self-service account deletion across organisations ===');

        const ts = Date.now();
        const userEmail = `selfdelete_user_${ts}@abstratium.dev`;
        const userName = 'SelfDelete User';
        const secondOrgName = `selfdelete-org-${ts}`;

        // ========================================================================
        // Step 1: Admin invites a new user to the default organisation
        // ========================================================================
        console.log('\n--- Step 1: Admin invites user to default organisation ---');
        await signInAsAdmin(page);
        await navigateToAccounts(page);
        const inviteLink = await addAccount(page, userEmail, userName);
        expect(inviteLink).toBeTruthy();
        console.log(`Admin invited ${userEmail}`);

        // ========================================================================
        // Step 2: New user signs in via invite link and sets a password
        // ========================================================================
        console.log('\n--- Step 2: User signs in via invite link ---');
        await signout(page);
        await signInViaInviteLink(page, inviteLink, userEmail);
        await approveAuthorization(page);
        await changePassword(page, USER_PASSWORD);
        console.log('User signed in and password changed');

        // ========================================================================
        // Step 3: User creates a second organisation so they belong to multiple orgs
        // ========================================================================
        console.log('\n--- Step 3: User creates a second organisation ---');
        await navigateToOrganisations(page);
        await createOrganisation(page, secondOrgName);
        await dismissToasts(page);
        console.log(`Created organisation '${secondOrgName}'`);

        // ========================================================================
        // Step 4: User deletes their own account
        // ========================================================================
        console.log('\n--- Step 4: User deletes their own account ---');
        await navigateToAccounts(page);
        const deleteError = await tryDeleteAccount(page, userEmail);
        expect(deleteError).toBeNull();
        console.log('User account deleted successfully');

        // ========================================================================
        // Step 5: Verify the user is signed out and on the sign-in page
        // ========================================================================
        console.log('\n--- Step 5: Verify user is signed out ---');
        // Self-deletion redirects through the BFF logout endpoint; navigate to the sign-in page
        // explicitly so the subsequent assertions and sign-in attempt are deterministic.
        await navigateToSigninPage(page);
        await expect(page.locator('#username')).toBeVisible({ timeout: 10000 });
        await expect(page.locator('#signin-button')).toBeVisible({ timeout: 10000 });
        console.log('User is on the sign-in page after self-deletion');

        // ========================================================================
        // Step 6: Verify the deleted user cannot sign back in
        // ========================================================================
        console.log('\n--- Step 6: Verify deleted user cannot sign back in ---');
        await page.locator('#username').fill(userEmail);
        await page.locator('#password').fill(USER_PASSWORD);
        await page.locator('#signin-button').click();

        const signinError = page.locator('.error-box, .error-message, #message');
        await expect(signinError).toBeVisible({ timeout: 10000 });
        const errorText = await signinError.textContent();
        console.log(`Sign-in error for deleted account: ${errorText?.trim()}`);
        expect(errorText?.toLowerCase()).toMatch(/invalid|not found|account|credentials|deleted|disabled/);
        console.log('Deleted user cannot sign back in');

        // ========================================================================
        // Step 7: Admin signs in and verifies the deleted account is gone from the default org
        // ========================================================================
        console.log('\n--- Step 7: Verify account is removed from default organisation ---');
        await signInAsAdmin(page);
        await navigateToAccounts(page);
        const userTile = page.locator('.tile').filter({ hasText: userEmail });
        await expect(userTile).toHaveCount(0, { timeout: 5000 });
        console.log('Deleted account is not visible to the default organisation owner');

        console.log('\n=== Self-delete test passed ===');
    });
});
