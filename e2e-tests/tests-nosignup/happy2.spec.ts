import { test, expect, Page } from '@playwright/test';

import { signout, navigateToAccounts, navigateToClients, getCurrentUserName } from '../pages/header';
import { ensureAdminIsAuthenticated, trySignInAsAdmin, signInViaInviteLink, signInAsAdmin, signInAsManager, MANAGER_EMAIL, MANAGER_NAME, MANAGER_PASSWORD } from '../pages/signin.page';
import { addAccount, deleteAccountsExcept, tryAddRoleToSelf, addRoleToAccount } from '../pages/accounts.page';
import { deleteClientsExcept, addClient } from '../pages/clients.page';
import { changePassword } from '../pages/change-password.page';
import { approveAuthorization, verifySignedIn } from '../pages/authorize.page';
import { dismissToasts } from '../pages/toast';

test('admin creates manager account and manager signs in via invite link', async ({ page }) => {
    // Step 1: Try to sign in as admin to check if database needs cleanup
    console.log("Step 1: Attempting to sign in as admin...");
    const adminExists = await trySignInAsAdmin(page);
    
    if (adminExists) {
        // Admin exists, so clean up other accounts and clients
        console.log("Step 2: Admin exists, cleaning up database...");
        
        // Get the current user's name from the header
        const currentUserName = await getCurrentUserName(page);
        console.log(`Current user name: ${currentUserName}`);
        
        // Navigate to accounts page and delete all accounts except current user
        console.log("Step 2a: Cleaning up accounts...");
        await navigateToAccounts(page);
        await deleteAccountsExcept(page, currentUserName);
        
        // Navigate to clients page and delete all clients except abstratium-abstrauth
        console.log("Step 2b: Cleaning up clients...");
        await navigateToClients(page);
        await deleteClientsExcept(page, 'abstratium-abstrauth');
        
        // Sign out after cleanup
        console.log("Step 2c: Signing out after cleanup...");
        await signout(page);
    } else {
        console.log("Step 2: Admin doesn't exist, database is already clean");
    }
    
    // Step 3: Now run the actual test - sign up as admin
    // The first account automatically gets admin, manage-accounts, and manage-clients roles
    console.log("Step 3: Signing up as admin...");
    await ensureAdminIsAuthenticated(page);
    
    // Step 3a: Sign out and back in to get fresh JWT token with all roles
    console.log("Step 3a: Signing out and back in to refresh JWT token...");
    await signout(page);
    await signInAsAdmin(page);
    
    // Step 4: Navigate to accounts page and add manager account
    console.log("Step 4: Adding manager account...");
    await navigateToAccounts(page);
    const inviteLink = await addAccount(page, MANAGER_EMAIL, MANAGER_NAME);
    
    // Step 5: Sign out as admin
    console.log("Step 5: Signing out as admin...");
    await signout(page);
    
    // Step 6: Use invite link to sign in as manager (FIRST TIME ONLY)
    console.log("Step 6: Signing in as manager via invite link (first time)...");
    await signInViaInviteLink(page, inviteLink, MANAGER_EMAIL);
    
    // Step 7: Approve authorization (comes before password change)
    console.log("Step 7: Approving authorization...");
    await approveAuthorization(page);
    
    // Step 8: Change password (required for invite-based accounts)
    console.log("Step 8: Changing password...");
    await changePassword(page, MANAGER_PASSWORD);
    
    // Step 9: Verify we're signed in as Manager
    console.log("Step 9: Verifying signed in as Manager...");
    await verifySignedIn(page, MANAGER_NAME);
    
    // Step 10: Verify manager only has "user" role and can only see their own account
    console.log("Step 10: Verifying manager only has 'user' role and can only see their own account...");
    await navigateToAccounts(page);
    
    // Wait for accounts to load - the first tile should appear
    const accountTiles = page.locator('.tile');
    await expect(accountTiles.first()).toBeVisible({ timeout: 10000 });
    
    // Manager should only see their own account (not the admin account)
    const tileCount = await accountTiles.count();
    expect(tileCount).toBe(1);
    console.log("✓ Manager can only see their own account (1 tile visible)");
    
    // Verify the visible account is the manager's account
    const visibleEmail = await accountTiles.first().locator('.tile-subtitle').textContent();
    expect(visibleEmail?.trim()).toBe(MANAGER_EMAIL);
    console.log(`✓ The visible account is ${MANAGER_EMAIL}`);
    
    // Verify manager cannot see the "Add Role" button (no manage-accounts role yet)
    const addRoleButton = page.locator('.btn-add-small');
    await expect(addRoleButton).not.toBeVisible();
    console.log("✓ Manager cannot see 'Add Role' button (no manage-accounts role)");
    
    // Step 11: Sign out as manager
    console.log("Step 11: Signing out as manager...");
    await signout(page);
    
    // Step 12: Sign back in as admin and give manager the manage-accounts role
    console.log("Step 12: Signing back in as admin...");
    await signInAsAdmin(page);
    
    console.log("Step 13: Giving manager the manage-accounts and manage-clients roles...");
    await navigateToAccounts(page);
    await addRoleToAccount(page, MANAGER_EMAIL, 'abstratium-abstrauth', 'manage-accounts');
    console.log("✓ Added manage-accounts role to manager");
    await addRoleToAccount(page, MANAGER_EMAIL, 'abstratium-abstrauth', 'manage-clients');
    console.log("✓ Added manage-clients role to manager");
    
    // Step 14: Sign out as admin
    console.log("Step 14: Signing out as admin...");
    await signout(page);
    
    // Step 15: Sign back in as manager with username/password (NOT invite link)
    console.log("Step 15: Signing back in as manager with username/password...");
    await signInAsManager(page);
    
    // Step 16: Try to add admin role (should fail - only admin can add admin role)
    console.log("Step 16: Attempting to add admin role (should fail)...");
    await navigateToAccounts(page);
    
    const adminRoleError = await tryAddRoleToSelf(page, 'abstratium-abstrauth', 'admin');
    expect(adminRoleError).toContain('Only admin can add the admin role');
    console.log("✓ Correctly blocked from adding admin role");
    
    // Step 17: Navigate to clients page and add a new client
    console.log("Step 17: Adding new client 'anapp-acomp'...");
    await navigateToClients(page);
    await addClient(page, 'anapp-acomp', 'anapp-acomp', 'http://localhost:3333/callback', 'openid profile email');
    
    // Step 18: Sign out as manager
    console.log("Step 18: Signing out as manager...");
    await signout(page);
    
    // Step 19: Sign in as admin to create the new user
    console.log("Step 19: Signing in as admin to create new user...");
    await signInAsAdmin(page);
    
    // Step 20: Create a new user
    console.log("Step 20: Creating new user 'AUser'...");
    await navigateToAccounts(page);
    const auserInviteLink = await addAccount(page, 'auser@abstratium.dev', 'AUser');
    console.log("✓ Created new user 'AUser'");
    
    // Step 21: Add a role for AUser on the default client
    console.log("Step 21: Adding 'viewer' role for AUser on 'abstratium-abstrauth'...");
    await addRoleToAccount(page, 'auser@abstratium.dev', 'abstratium-abstrauth', 'viewer');
    console.log("✓ Added 'viewer' role for AUser on 'abstratium-abstrauth'");
    
    // Step 22: Add a role for AUser on the new client
    console.log("Step 22: Adding 'viewer' role for AUser on 'anapp-acomp' client...");
    await addRoleToAccount(page, 'auser@abstratium.dev', 'anapp-acomp', 'viewer');
    console.log("✓ Added 'viewer' role for AUser on 'anapp-acomp' client");
    
    // Step 23: Navigate to clients page and verify the new client exists
    console.log("Step 23: Verifying 'anapp-acomp' client exists...");
    await navigateToClients(page);
    const clientCard = page.locator('.card').filter({ hasText: 'anapp-acomp' });
    await expect(clientCard).toBeVisible({ timeout: 5000 });
    console.log("✓ Client 'anapp-acomp' is visible");
    
    // Step 24: Click on the link to view accounts with roles for this client
    console.log("Step 24: Clicking link to view accounts with roles for this client...");
    
    // Dismiss any toast notifications that might block the click
    await dismissToasts(page);
    
    await clientCard.locator('.client-link').click();
    expect(page.url()).toContain('/accounts');
    expect(page.url()).toContain('filter=anapp-acomp');
    console.log("✓ Navigated to accounts page with client filter");
    
    // Step 25: Verify only AUser account is visible (filtered by 'viewer' role on 'anapp-acomp')
    console.log("Step 25: Verifying only AUser account is visible...");
    const filteredTiles = page.locator('.tile');
    await expect(filteredTiles.first()).toBeVisible({ timeout: 5000 });
    const filteredCount = await filteredTiles.count();
    expect(filteredCount).toBe(1);
    const filteredEmail = await filteredTiles.first().locator('.tile-subtitle').textContent();
    expect(filteredEmail?.trim()).toBe('auser@abstratium.dev');
    console.log("✓ Only AUser account is visible (filtered by 'viewer' role on 'anapp-acomp')");
    
    console.log("Test completed successfully!");
});
