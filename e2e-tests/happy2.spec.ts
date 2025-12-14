import { test, expect, Page } from '@playwright/test';

import { signout, navigateToAccounts, navigateToClients, getCurrentUserName } from './header';
import { ensureAdminIsAuthenticated, trySignInAsAdmin, signInViaInviteLink } from './signin.page';
import { addAccount, deleteAccountsExcept } from './accounts.page';
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
    console.log("Step 3: Signing up as admin...");
    await ensureAdminIsAuthenticated(page);
    
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
    
    console.log("Test completed successfully!");
});
