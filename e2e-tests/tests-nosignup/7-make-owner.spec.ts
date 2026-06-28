import { test, expect, Page } from '@playwright/test';
import {
    signInAsAdmin,
    navigateToSigninPage,
    signInViaInviteLink
} from '../pages/signin.page';
import { navigateToAccounts, signout } from '../pages/header';
import { addAccount, tryMakeOwner, isMakeOwnerButtonVisible, tryRemoveOwner, isRemoveOwnerButtonVisible } from '../pages/accounts.page';
import { approveAuthorization } from '../pages/authorize.page';
import { changePassword } from '../pages/change-password.page';
import { dismissToasts } from '../pages/toast';

/*
 * E2E tests for the "Make Owner" and "Remove Owner" features.
 *
 * Make Owner verifies:
 *   - Owner can promote a member to owner
 *   - Make Owner button is only visible to owners
 *   - Make Owner button is not visible for self
 *   - Non-owners cannot see the Make Owner button
 *   - Cannot make someone an owner twice (error handling)
 *
 * Remove Owner verifies:
 *   - Owner with MANAGE_ACCOUNTS can demote a co-owner to member
 *   - Demoted account retains member status
 *   - Cannot remove the last owner
 *   - Remove owner button is not visible for self
 */

/**
 * Helper: Clean up test accounts if they exist
 * Assumes user is already signed in and on accounts page
 */
async function cleanupTestAccounts(page: Page) {
    console.log('Cleaning up test accounts...');
    try {
        await dismissToasts(page);

        const isTestAccount = (email: string | null) =>
            email?.includes('makeowner_member_') || email?.includes('makeowner_nonowner_') ||
            email?.includes('makeowner_twice_') || email?.includes('makeowner_promo_') ||
            email?.includes('removeowner_');

        // Re-query the tile list after each deletion so we never reference stale tiles.
        // Also stop if the current user self-deleted and the page redirected to signin.
        while (true) {
            if (await page.locator('#signin-button').isVisible().catch(() => false)) {
                console.log('Cleanup: page is on signin, current user was self-deleted');
                break;
            }

            const tiles = page.locator('.tile');
            const count = await tiles.count();
            if (count === 0) {
                console.log('Cleanup: no account tiles found');
                break;
            }

            let deletedAny = false;
            for (let i = 0; i < count; i++) {
                const tile = tiles.nth(i);
                const emailElement = tile.locator('.tile-subtitle').first();
                const email = await emailElement.textContent().catch(() => null);

                if (isTestAccount(email)) {
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

                        // Wait for the tile to disappear or for the page to redirect to signin
                        await expect(async () => {
                            if (await page.locator('#signin-button').isVisible().catch(() => false)) {
                                return;
                            }
                            const stillVisible = await tile.isVisible().catch(() => false);
                            if (stillVisible) {
                                throw new Error('Tile still visible after deletion');
                            }
                        }).toPass({ timeout: 5000 });

                        await dismissToasts(page);
                        deletedAny = true;
                    }
                    break; // restart the outer loop with a fresh tile list
                }
            }

            if (!deletedAny) {
                console.log('Cleanup: no more test accounts to delete');
                break;
            }
        }
    } catch (e) {
        console.log('Cleanup encountered issue (may be expected):', e);
    }
}

test.describe('Make Owner Feature', () => {

    test.setTimeout(60000);

    test.afterEach(async ({ page }) => {
        // Clean up after each test - only if we're signed in (accounts link visible)
        let accountsLink = page.locator('#accounts-link');
        if (await accountsLink.isVisible().catch(() => false)) {
            await navigateToAccounts(page);
            await cleanupTestAccounts(page);
            // Cleanup may have self-deleted the current user, which already signs out
            accountsLink = page.locator('#accounts-link');
            if (await accountsLink.isVisible().catch(() => false)) {
                await signout(page);
            }
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

        // Step 4: Reload to refresh owner status - make owner button should now be gone
        await page.reload();
        await page.waitForLoadState('networkidle');
        await navigateToAccounts(page);

        // Step 5: Verify Make Owner button is no longer visible (account is now an owner)
        const isButtonVisible = await isMakeOwnerButtonVisible(page, ownerEmail);
        expect(isButtonVisible).toBe(false);

        // Step 6: Verify the remove-owner button is visible for the now-owner
        // (the crown badge is replaced by a remove-owner button when the caller is an owner with MANAGE_ACCOUNTS)
        const accountTile = page.locator('.tile').filter({ hasText: ownerEmail });
        await expect(accountTile.locator('[data-testid="remove-owner-button"]')).toBeVisible();

        console.log('✓ Test passed: cannot make someone an owner twice - button hidden after promotion');
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

    test('owner with manage-accounts can demote a co-owner to member', async ({ page }) => {
        console.log('=== Test: owner can remove owner role from co-owner ===');
        const ts = Date.now();
        const coOwnerEmail = `removeowner_coowner_${ts}@abstratium.dev`;

        // Step 1: Sign in as admin and create a new account
        await signInAsAdmin(page);
        await navigateToAccounts(page);
        const inviteLink = await addAccount(page, coOwnerEmail, 'CoOwner');
        expect(inviteLink).toBeTruthy();

        // Step 2: Promote the new account to owner
        const makeError = await tryMakeOwner(page, coOwnerEmail);
        expect(makeError).toBeNull();
        await dismissToasts(page);

        // Step 3: Verify the remove-owner button is now visible for the co-owner
        const isRemoveVisible = await isRemoveOwnerButtonVisible(page, coOwnerEmail);
        expect(isRemoveVisible).toBe(true);

        // Step 4: Remove the owner role
        const removeError = await tryRemoveOwner(page, coOwnerEmail);
        expect(removeError).toBeNull();
        await dismissToasts(page);

        // Step 5: After demotion, the account tile should still exist (still a member)
        const accountTile = page.locator('.tile').filter({ hasText: coOwnerEmail });
        await expect(accountTile).toBeVisible({ timeout: 5000 });

        // Step 6: The remove-owner button should now be gone (no longer an owner)
        // and the make-owner button should be back
        await page.reload();
        await page.waitForLoadState('load');
        await navigateToAccounts(page);

        const isStillRemoveVisible = await isRemoveOwnerButtonVisible(page, coOwnerEmail);
        expect(isStillRemoveVisible).toBe(false);

        const isMakeOwnerVisible = await isMakeOwnerButtonVisible(page, coOwnerEmail);
        expect(isMakeOwnerVisible).toBe(true);

        console.log('✓ Test passed: owner can demote a co-owner to member');
    });

    test('cannot remove the last owner', async ({ page }) => {
        console.log('=== Test: cannot remove the last owner ===');

        // Step 1: Sign in as admin (the only owner)
        await signInAsAdmin(page);
        await navigateToAccounts(page);

        // Step 2: The admin is the only owner — the remove-owner button should NOT be visible for self
        const adminTile = page.locator('.tile.highlighted-tile');
        await expect(adminTile).toBeVisible();
        const adminEmail = (await adminTile.locator('.tile-subtitle').textContent()) ?? '';

        const isRemoveVisible = await isRemoveOwnerButtonVisible(page, adminEmail.trim());
        expect(isRemoveVisible).toBe(false);

        console.log('✓ Test passed: remove owner button not visible for self (last owner)');
    });

    test('remove owner button is not visible for non-owner tile', async ({ page }) => {
        console.log('=== Test: remove owner button is not visible for plain members ===');
        const ts = Date.now();
        const memberEmail = `removeowner_member_${ts}@abstratium.dev`;

        // Step 1: Sign in as admin and create a plain member (not promoted to owner)
        await signInAsAdmin(page);
        await navigateToAccounts(page);
        const inviteLink = await addAccount(page, memberEmail, 'PlainMember');
        expect(inviteLink).toBeTruthy();

        // Step 2: The member is NOT an owner — the remove-owner button should not be visible
        const isRemoveVisible = await isRemoveOwnerButtonVisible(page, memberEmail);
        expect(isRemoveVisible).toBe(false);

        // Step 3: The make-owner button should be visible (they are a member, not an owner)
        const isMakeVisible = await isMakeOwnerButtonVisible(page, memberEmail);
        expect(isMakeVisible).toBe(true);

        console.log('✓ Test passed: remove owner button not visible for plain members');
    });

});
