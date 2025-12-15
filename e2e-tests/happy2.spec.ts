import { test, expect, Page } from '@playwright/test';

import { signout, navigateToAccounts, navigateToClients, getCurrentUserName } from './header';
import { ensureAdminIsAuthenticated, trySignInAsAdmin, signInViaInviteLink, signInAsAdmin } from './signin.page';
import { addAccount, deleteAccountsExcept, tryAddRoleToSelf } from './accounts.page';
import { deleteClientsExcept } from './clients.page';
import { changePassword } from './change-password.page';
import { approveAuthorization, verifySignedIn } from './authorize.page';

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
    const inviteLink = await addAccount(page, 'manager@abstratium.dev', 'Manager');
    
    // Step 5: Sign out as admin
    console.log("Step 5: Signing out as admin...");
    await signout(page);
    
    // Step 6: Use invite link to sign in as manager
    console.log("Step 6: Signing in as manager via invite link...");
    await signInViaInviteLink(page, inviteLink, 'manager@abstratium.dev');
    
    // Step 7: Approve authorization (comes before password change)
    console.log("Step 7: Approving authorization...");
    await approveAuthorization(page);
    
    // Step 8: Change password (required for invite-based accounts)
    console.log("Step 8: Changing password...");
    await changePassword(page, 'secretLong2');
    
    // Step 9: Verify we're signed in as Manager
    console.log("Step 9: Verifying signed in as Manager...");
    await verifySignedIn(page, 'Manager');
    
    // Step 10: Try to add admin role (should fail)
    console.log("Step 10: Attempting to add admin role (should fail)...");
    await navigateToAccounts(page);
    
    // Debug: Check what the current page shows
    const currentUserName = await getCurrentUserName(page);
    console.log(`Current user according to header: ${currentUserName}`);
    
    const adminRoleError = await tryAddRoleToSelf(page, 'abstratium-abstrauth', 'admin');
    
    if (adminRoleError === null) {
        console.log("ERROR: Role was added successfully when it should have been blocked!");
        console.log("This suggests the manager user has admin privileges, which is incorrect.");
    }
    
    expect(adminRoleError).toContain('Only admin can add the admin role');
    console.log("✓ Correctly blocked from adding admin role");
    
    // Step 11: Try to add arbitrary role (should fail - not member of client)
    console.log("Step 11: Attempting to add arbitrary role (should fail)...");
    const arbitraryRoleError = await tryAddRoleToSelf(page, 'abstratium-abstrauth', 'asdf');
    expect(arbitraryRoleError).toContain('Only admin can add roles to accounts that are not members of the client');
    console.log("✓ Correctly blocked from adding role to client they're not a member of");
    
    // Step 12: Sign out as manager
    console.log("Step 12: Signing out as manager...");
    await signout(page);
    
    // Step 13: Sign back in as admin
    console.log("Step 13: Signing back in as admin...");
    await signInAsAdmin(page);
    
    console.log("Test completed successfully!");
});
