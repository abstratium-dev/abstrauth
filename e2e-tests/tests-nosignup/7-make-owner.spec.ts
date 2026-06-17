import { test, expect, Page } from '@playwright/test';
import {
    signInAsAdmin,
    navigateToSigninPage,
    signInViaInviteLink
} from '../pages/signin.page';
import { navigateToAccounts, signout } from '../pages/header';
import { addAccount, tryMakeOwner, isMakeOwnerButtonVisible } from '../pages/accounts.page';
import { approveAuthorization } from '../pages/authorize.page';
import { changePassword } from '../pages/change-password.page';
import { dismissToasts } from '../pages/toast';

/*
 * E2E tests for the "Make Owner" feature.
 *
 * Verifies:
 *   - Owner can promote a member to owner
 *   - Make Owner button is only visible to owners
 *   - Make Owner button is not visible for self
 *   - Non-owners cannot see the Make Owner button
 *   - Cannot make someone an owner twice (error handling)
 */

/**
 * Helper: Clean up test accounts if they exist
 * Assumes user is already signed in and on accounts page
 */
async function cleanupTestAccounts(page: Page) {
    console.log('Cleaning up test accounts...');
    try {
        await dismissToasts(page);

        // Try to delete test accounts if they exist
        const tiles = page.locator('.tile');
        const count = await tiles.count();

        for (let i = count - 1; i >= 0; i--) {
            const tile = tiles.nth(i);
            const emailElement = tile.locator('.tile-subtitle');
            const email = await emailElement.textContent();

            if (email?.includes('makeowner_member_') || email?.includes('makeowner_nonowner_') ||
                email?.includes('makeowner_twice_') || email?.includes('makeowner_promo_')) {
                console.log(`Found test account to clean up: ${email}`);
                const deleteButton = tile.locator('.btn-icon-danger').first();
                if (await deleteButton.isVisible().catch(() => false)) {
                    await deleteButton.click();
                    const confirmButton = page.locator('button.btn-danger').filter({ hasText: 'Delete Account' });
                    await expect(confirmButton).toBeVisible({ timeout: 2000 });
                    await confirmButton.click();
                    // Wait for toast
                    await page.waitForTimeout(1000);
                    await dismissToasts(page);
                }
            }
        }
    } catch (e) {
        console.log('Cleanup encountered issue (may be expected):', e);
    }
}

test.describe('Make Owner Feature', () => {

    test.afterEach(async ({ page }) => {
        // Clean up after each test - only if we're signed in (accounts link visible)
        const accountsLink = page.locator('#accounts-link');
        if (await accountsLink.isVisible().catch(() => false)) {
            await navigateToAccounts(page);
            await cleanupTestAccounts(page);
            await signout(page);
        }
    });

    test('owner can make a member an owner', async ({ page }) => {
        console.log('=== Test: owner can make a member an owner ===');
        const ts = Date.now();
        const memberEmail = `makeowner_member_${ts}@abstratium.dev`;

        // Step 1: Sign in as admin (who is an owner)
        await signInAsAdmin(page);

        // Step 2: Navigate to accounts and add a new member
        await navigateToAccounts(page);
        const inviteLink = await addAccount(page, memberEmail, 'MakeMember');
        expect(inviteLink).toBeTruthy();

        // Step 3: Verify the Make Owner button is visible for the new member
        const isVisible = await isMakeOwnerButtonVisible(page, memberEmail);
        expect(isVisible).toBe(true);

        // Step 4: Click Make Owner button and confirm
        const error = await tryMakeOwner(page, memberEmail);
        expect(error).toBeNull(); // Should succeed

        // Step 5: Dismiss success toast
        await dismissToasts(page);

        console.log('✓ Test passed: owner can make a member an owner');
    });

    test('make owner button is not visible for non-owners', async ({ page }) => {
        console.log('=== Test: make owner button is not visible for non-owners ===');
        const ts = Date.now();
        const memberEmail = `makeowner_nonowner_${ts}@abstratium.dev`;

        // Step 1: Sign in as admin and create a member
        await signInAsAdmin(page);
        await navigateToAccounts(page);
        const inviteLink = await addAccount(page, memberEmail, 'MakeMember');
        expect(inviteLink).toBeTruthy();

        // Step 2: Sign out admin
        await signout(page);

        // Step 3: Sign in as the new member (who is not an owner)
        await navigateToSigninPage(page);
        await signInViaInviteLink(page, inviteLink, memberEmail);

        // Step 4: Approve authorization and change password (first time login)
        await approveAuthorization(page);
        await changePassword(page, 'TempPass123!');

        // Step 5: Reload to get fresh organization data with current roles
        await page.reload();
        await page.waitForLoadState('networkidle');

        // Step 6: Navigate to accounts
        await navigateToAccounts(page);

        // Step 7: Verify the Make Owner button is NOT visible (member can't see it)
        // The member should see themselves but not have the make owner button
        const isVisible = await isMakeOwnerButtonVisible(page, memberEmail);
        expect(isVisible).toBe(false);

        console.log('✓ Test passed: make owner button is not visible for non-owners');
    });

    test('make owner button is not visible for self', async ({ page }) => {
        console.log('=== Test: make owner button is not visible for self ===');

        // Step 1: Sign in as admin
        await signInAsAdmin(page);
        await navigateToAccounts(page);

        // Step 2: Get admin email from highlighted tile
        const adminTile = page.locator('.tile.highlighted-tile');
        await expect(adminTile).toBeVisible();
        const emailElement = adminTile.locator('.tile-subtitle');
        const adminEmail = await emailElement.textContent();

        // Step 3: Verify Make Owner button is NOT visible for self
        const isVisible = await isMakeOwnerButtonVisible(page, adminEmail!);
        expect(isVisible).toBe(false);

        console.log('✓ Test passed: make owner button is not visible for self');
    });

    test('cannot make someone an owner twice', async ({ page }) => {
        console.log('=== Test: cannot make someone an owner twice ===');
        const ts = Date.now();
        const ownerEmail = `makeowner_twice_${ts}@abstratium.dev`;

        // Step 1: Sign in as admin
        await signInAsAdmin(page);

        // Step 2: Navigate to accounts and add a new member
        await navigateToAccounts(page);
        const inviteLink = await addAccount(page, ownerEmail, 'MakeOwner');
        expect(inviteLink).toBeTruthy();

        // Step 3: Make the member an owner (first time - should succeed)
        const error1 = await tryMakeOwner(page, ownerEmail);
        expect(error1).toBeNull();
        await dismissToasts(page);

        // Step 4: Try to make the same member an owner again (should fail)
        const error2 = await tryMakeOwner(page, ownerEmail);
        expect(error2).toContain('already an owner');

        console.log('✓ Test passed: cannot make someone an owner twice');
    });

    test('newly promoted owner can see make owner button', async ({ page }) => {
        console.log('=== Test: newly promoted owner can see make owner button ===');
        const ts = Date.now();
        const ownerEmail = `makeowner_promo_${ts}@abstratium.dev`;
        const memberEmail = `makeowner_member_${ts}@abstratium.dev`;

        // Step 1: Sign in as admin and create two members
        await signInAsAdmin(page);
        await navigateToAccounts(page);

        const inviteLink1 = await addAccount(page, ownerEmail, 'MakeOwner');
        expect(inviteLink1).toBeTruthy();

        const inviteLink2 = await addAccount(page, memberEmail, 'MakeMember');
        expect(inviteLink2).toBeTruthy();

        // Step 2: Make the first member an owner
        const error = await tryMakeOwner(page, ownerEmail);
        expect(error).toBeNull();
        await dismissToasts(page);

        // Step 3: Sign out admin
        await signout(page);

        // Step 4: Sign in as the newly promoted owner
        await navigateToSigninPage(page);
        await signInViaInviteLink(page, inviteLink1, ownerEmail);

        // Step 5: Approve authorization and change password (first time login)
        await approveAuthorization(page);
        await changePassword(page, 'TempPass123!');

        // Step 6: Reload to get fresh organization data with current roles (owner)
        await page.reload();
        await page.waitForLoadState('networkidle');

        // Step 7: Navigate to accounts
        await navigateToAccounts(page);

        // Step 8: Verify the newly promoted owner can see the Make Owner button for the other member
        const isVisible = await isMakeOwnerButtonVisible(page, memberEmail);
        expect(isVisible).toBe(true);

        console.log('✓ Test passed: newly promoted owner can see make owner button');
    });

});
