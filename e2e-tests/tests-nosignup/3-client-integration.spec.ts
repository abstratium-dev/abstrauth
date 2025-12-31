import { test, expect, Page } from '@playwright/test';
import { signInAsAdmin, ADMIN_EMAIL, ADMIN_PASSWORD, navigateWithRetry } from '../pages/signin.page';
import { navigateToAccounts, navigateToClients, signout } from '../pages/header';
import { addClient, deleteClientIfExists } from '../pages/clients.page';
import { addRoleToAccount } from '../pages/accounts.page';
import { approveAuthorization } from '../pages/authorize.page';

/**
 * Helper function to sign in on the OAuth signin page (with request_id in URL)
 */
async function signInOnOAuthPage(page: Page, email: string, password: string) {
    console.log(`Signing in as ${email} on OAuth signin page...`);
    
    // Wait for the signin form to be visible
    const usernameInput = page.locator('#username');
    await expect(usernameInput).toBeVisible({ timeout: 10000 });
    
    // Fill in credentials
    await usernameInput.fill(email);
    await page.locator('#password').fill(password);
    
    // Click sign in button
    const signinButton = page.locator('#signin-button');
    await expect(signinButton).toBeEnabled({ timeout: 5000 });
    await signinButton.click();
    
    console.log(`✓ Submitted signin form for ${email}`);
}

/**
 * E2E test for OAuth client integration
 * 
 * This test demonstrates the complete OAuth 2.0 Authorization Code Flow with PKCE
 * from the perspective of a third-party client application.
 * 
 * Prerequisites:
 * - Admin account must exist (created in happy2.spec.ts)
 * - The example client server must be running on http://localhost:3333
 */

const CLIENT_ID = 'test-oauth-client';
const CLIENT_NAME = 'Test OAuth Client';
const REDIRECT_URI = 'http://localhost:3333/oauth/callback';
const SCOPES = 'openid profile email';
const ROLE_NAME = 'aRole';

test('Admin creates OAuth client and signs in via example client application', async ({ page }) => {
    console.log("=== Starting OAuth Client Integration Test ===");
    
    // Step 1: Sign in as admin on port 8080
    console.log("Step 1: Signing in as admin on port 8080...");
    await signInAsAdmin(page);
    console.log("✓ Signed in as admin");
    
    // Step 2: Navigate to clients page and delete existing client if it exists
    console.log("Step 2: Checking if client already exists...");
    await navigateToClients(page);
    await deleteClientIfExists(page, CLIENT_ID);
    
    // Step 3: Create new client and capture the client secret
    console.log("Step 3: Creating new OAuth client...");
    const clientSecret = await addClient(page, CLIENT_ID, CLIENT_NAME, REDIRECT_URI, SCOPES);
    console.log(`✓ Created client '${CLIENT_ID}' with redirect URI '${REDIRECT_URI}'`);
    console.log(`✓ Captured client secret: ${clientSecret.substring(0, 10)}...`);
    
    // Step 4: Navigate to accounts page and add role to admin
    console.log("Step 4: Adding role to admin account...");
    await navigateToAccounts(page);
    await addRoleToAccount(page, ADMIN_EMAIL, CLIENT_ID, ROLE_NAME);
    console.log(`✓ Added role '${ROLE_NAME}' for client '${CLIENT_ID}' to admin account`);
    
    // Step 4.5: Sign out from port 8080 to clear session
    console.log("Step 4.5: Signing out from authorization server...");
    await signout(page);
    console.log("✓ Signed out from authorization server");
    
    // Step 4.6: Configure the example client server with the captured client secret
    console.log("Step 4.6: Configuring example client server with client secret...");
    const configResponse = await page.request.post('http://localhost:3333/test/configure', {
        data: {
            clientId: CLIENT_ID,
            clientSecret: clientSecret
        },
        headers: {
            'Content-Type': 'application/json'
        }
    });
    
    if (!configResponse.ok()) {
        throw new Error(`Failed to configure client server: ${configResponse.status()} ${await configResponse.text()}`);
    }
    
    console.log(`✓ Configured example client server with CLIENT_ID=${CLIENT_ID}`);
    
    // Step 5: Visit the example client application on port 3333
    console.log("Step 5: Visiting example client application at http://localhost:3333");
    await navigateWithRetry(page, 'http://localhost:3333');
    
    // Step 6: Check if already signed in (Sign Out button present) or need to sign in
    console.log("Step 6: Checking authentication state on client app...");
    const signOutButton = page.locator('button').filter({ hasText: /Sign Out/i });
    const signInButton = page.locator('button').filter({ hasText: /Sign In/i });
    
    // Wait for either button to appear
    try {
        await signOutButton.waitFor({ state: 'visible', timeout: 2000 });
        console.log("Already signed in, clicking Sign Out to clear session...");
        await signOutButton.click();
        
        // Wait for Sign In button to appear after logout
        await expect(signInButton).toBeVisible({ timeout: 5000 });
        console.log("✓ Signed out, now ready to sign in");
    } catch {
        console.log("Not signed in, proceeding with sign in...");
    }
    
    // Step 7: Click the Sign In button
    console.log("Step 7: Clicking Sign In button...");
    await expect(signInButton).toBeVisible({ timeout: 10000 });
    await signInButton.click();
    console.log("✓ Clicked Sign In button");
    
    // Step 8: Wait for redirect to authorization server signin page (with UUID)
    console.log("Step 8: Waiting for redirect to authorization server signin page...");
    await page.waitForURL(/localhost:8080\/signin\/[a-f0-9-]+/, { timeout: 10000 });
    console.log("✓ Redirected to authorization server signin page");
    
    // Step 9: Sign in as admin on the OAuth signin page
    // Note: The admin session from earlier is lost because we navigated to a different app (port 3333)
    console.log("Step 9: Signing in as admin on OAuth signin page...");
    await signInOnOAuthPage(page, ADMIN_EMAIL, ADMIN_PASSWORD);
    
    // Step 10: After signin, the authorization/consent page should be displayed
    // The URL may still be /signin/... but the content changes to show the consent form
    console.log("Step 10: Approving authorization...");
    await approveAuthorization(page);
    console.log("✓ Authorization approved");
    
    // Step 11: Should be redirected back to the client application (port 3333)
    console.log("Step 11: Waiting for redirect back to client application...");
    await page.waitForURL(/localhost:3333/, { timeout: 15000 });
    console.log("✓ Redirected back to client application on port 3333");
    
    // Step 12: Wait for network traffic to stabilize (token exchange happens in background)
    console.log("Step 12: Waiting for network traffic to stabilize...");
    await page.waitForLoadState('networkidle', { timeout: 10000 });
    console.log("✓ Network traffic stabilized");
    
    // Step 13: Verify the page shows user information
    console.log("Step 13: Verifying user information is displayed...");
    
    // Wait for the content div to be visible (means user is authenticated)
    await expect(page.locator('#content')).toBeVisible({ timeout: 10000 });
    console.log("✓ User content is visible");
    
    // Step 14: Verify email is displayed
    console.log("Step 14: Verifying email address is displayed...");
    const displayedEmail = await page.locator('#user-email').textContent();
    expect(displayedEmail?.trim()).toBe(ADMIN_EMAIL);
    console.log(`✓ Email displayed: ${displayedEmail}`);
    
    // Step 15: Verify role is displayed in groups
    console.log("Step 15: Verifying role 'aRole' is displayed...");
    const displayedGroups = await page.locator('#user-groups').textContent();
    expect(displayedGroups).toContain(ROLE_NAME);
    console.log(`✓ Role '${ROLE_NAME}' displayed in groups: ${displayedGroups}`);
    
    // Step 16: Verify we're on port 3333
    console.log("Step 16: Verifying we're on port 3333...");
    expect(page.url()).toContain('localhost:3333');
    console.log(`✓ On correct port: ${page.url()}`);
    
    // Step 17: Verify security features are documented on the page
    console.log("Step 17: Verifying security features are documented...");
    await expect(page.locator('text=PKCE Protection')).toBeVisible();
    await expect(page.locator('text=State Validation')).toBeVisible();
    console.log("✓ Security features documented on page");
    
    console.log("=== OAuth Client Integration Test Completed Successfully ===");
});
