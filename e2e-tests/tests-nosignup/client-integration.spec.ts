import { test, expect, Page } from '@playwright/test';
import { signInViaInviteLink } from '../pages/signin.page';
import { changePassword } from '../pages/change-password.page';

/**
 * E2E test for OAuth client integration
 * 
 * This test demonstrates the complete OAuth 2.0 Authorization Code Flow with PKCE
 * from the perspective of a third-party client application.
 * 
 * Prerequisites:
 * - The 'anapp-acomp' client must be created (done in happy2.spec.ts)
 * - The 'AUser' account must exist with a role on 'anapp-acomp' (done in happy2.spec.ts)
 * - The example client server must be running on http://localhost:3333
 */

const AUSER_EMAIL = 'auser@abstratium.dev';
const AUSER_NAME = 'AUser';
const AUSER_PASSWORD = 'AUserPassword123!';

test('AUser signs into example OAuth client application', async ({ page }) => {
    console.log("=== Starting OAuth Client Integration Test ===");
    
    // Step 1: Visit the example client application
    console.log("Step 1: Visiting example client application at http://localhost:3333");
    await page.goto('http://localhost:3333');
    
    // Verify we're on the login page
    await expect(page.locator('h1')).toContainText('Welcome to Example OAuth Client');
    console.log("✓ Example client home page loaded");
    
    // Step 2: Click the "Sign In" button to initiate OAuth flow
    console.log("Step 2: Clicking 'Sign In' button to initiate OAuth flow");
    await page.click('a.login-btn');
    
    // The client should redirect us to the authorization server
    // Wait for navigation to the authorization server
    await page.waitForURL(/localhost:8080/, { timeout: 10000 });
    console.log("✓ Redirected to authorization server");
    
    // Step 3: We should be on the signin page with a request_id
    console.log("Step 3: Verifying we're on the signin page");
    await expect(page.url()).toContain('/signin/');
    
    // Extract request_id from URL
    const urlMatch = page.url().match(/\/signin\/([^?]+)/);
    expect(urlMatch).toBeTruthy();
    const requestId = urlMatch![1];
    console.log(`✓ On signin page with request_id: ${requestId}`);
    
    // Step 4: Sign in as AUser (first time - using invite link from happy2.spec.ts)
    // Note: In a real scenario, AUser would have already set their password
    // For this test, we'll check if AUser needs to set password or can sign in directly
    console.log("Step 4: Attempting to sign in as AUser");
    
    // Check if there's a username/password form or if we need to use invite link
    const usernameInput = page.locator('input[name="username"]');
    const passwordInput = page.locator('input[name="password"]');
    
    // Try to sign in with username/password
    if (await usernameInput.isVisible({ timeout: 2000 }).catch(() => false)) {
        console.log("Step 4a: Username/password form found, attempting to sign in");
        await usernameInput.fill(AUSER_EMAIL);
        await passwordInput.fill(AUSER_PASSWORD);
        
        const signInButton = page.locator('button[type="submit"]').filter({ hasText: /sign in/i });
        await signInButton.click();
        
        // Wait for either consent page or error
        await page.waitForTimeout(1000);
    } else {
        console.log("Step 4b: No login form found, user may need to complete onboarding first");
        // This would happen if AUser hasn't set their password yet
        // In the happy2.spec.ts test, AUser is created but may not have completed password setup
        // For this test to work, we assume AUser has already completed onboarding
    }
    
    // Step 5: Approve consent
    console.log("Step 5: Looking for consent page");
    
    // Wait for consent page to appear
    const consentHeading = page.locator('h1, h2').filter({ hasText: /consent|authorize|permission/i });
    
    try {
        await consentHeading.waitFor({ timeout: 5000 });
        console.log("✓ Consent page displayed");
        
        // Look for the approve button
        const approveButton = page.locator('button').filter({ hasText: /approve|allow|authorize/i });
        await approveButton.click();
        console.log("✓ Clicked approve button");
    } catch (error) {
        console.log("Note: Consent page may have been auto-approved or skipped");
    }
    
    // Step 6: Should be redirected back to the client application
    console.log("Step 6: Waiting for redirect back to client application");
    await page.waitForURL(/localhost:3333/, { timeout: 15000 });
    console.log("✓ Redirected back to client application");
    
    // Step 7: Verify we're on the authenticated dashboard
    console.log("Step 7: Verifying authenticated dashboard");
    await expect(page.locator('h1')).toContainText('Authentication Successful', { timeout: 10000 });
    console.log("✓ Authentication successful page displayed");
    
    // Step 8: Verify user information is displayed
    console.log("Step 8: Verifying user information is displayed");
    
    // Wait for user info to load
    await page.waitForSelector('#user-name', { timeout: 10000 });
    
    // Check user name
    const displayedName = await page.locator('#user-name').textContent();
    expect(displayedName).toBe(AUSER_NAME);
    console.log(`✓ User name displayed: ${displayedName}`);
    
    // Check email
    const displayedEmail = await page.locator('#user-email').textContent();
    expect(displayedEmail).toBe(AUSER_EMAIL);
    console.log(`✓ User email displayed: ${displayedEmail}`);
    
    // Check token type
    const tokenType = await page.locator('#token-type').textContent();
    expect(tokenType).toBe('Bearer');
    console.log(`✓ Token type: ${tokenType}`);
    
    // Check scope
    const scope = await page.locator('#token-scope').textContent();
    expect(scope).toContain('openid');
    console.log(`✓ Scope includes: ${scope}`);
    
    // Step 9: Verify PKCE and security features are mentioned
    console.log("Step 9: Verifying security features are documented");
    await expect(page.locator('text=PKCE Protection')).toBeVisible();
    await expect(page.locator('text=State Validation')).toBeVisible();
    await expect(page.locator('text=CSRF')).toBeVisible();
    console.log("✓ Security features documented on page");
    
    // Step 10: Test logout
    console.log("Step 10: Testing logout functionality");
    await page.click('a.btn-danger'); // Sign Out button
    
    // Should be redirected back to the login page
    await page.waitForURL('http://localhost:3333/', { timeout: 5000 });
    await expect(page.locator('h1')).toContainText('Welcome to Example OAuth Client');
    console.log("✓ Successfully logged out");
    
    console.log("=== OAuth Client Integration Test Completed Successfully ===");
});

test('OAuth client handles authorization errors correctly', async ({ page }) => {
    console.log("=== Testing OAuth Error Handling ===");
    
    // Step 1: Visit the example client
    console.log("Step 1: Visiting example client application");
    await page.goto('http://localhost:3333');
    
    // Step 2: Initiate OAuth flow
    console.log("Step 2: Initiating OAuth flow");
    await page.click('a.login-btn');
    await page.waitForURL(/localhost:8080/, { timeout: 10000 });
    
    // Step 3: User denies consent (if we can get to consent page)
    console.log("Step 3: Attempting to test consent denial");
    
    // For this test, we'll simulate an error by navigating directly to the callback
    // with an error parameter (simulating what happens when user denies consent)
    const callbackUrl = 'http://localhost:3333/callback?error=access_denied&error_description=User+denied+authorization&state=test';
    await page.goto(callbackUrl);
    
    // Step 4: Verify error is displayed
    console.log("Step 4: Verifying error handling");
    await expect(page.locator('h1')).toContainText('Authorization Error', { timeout: 5000 });
    await expect(page.locator('text=access_denied')).toBeVisible();
    console.log("✓ Error page displayed correctly");
    
    // Step 5: Verify return to home link works
    console.log("Step 5: Testing return to home");
    await page.click('a[href="/"]');
    await expect(page.locator('h1')).toContainText('Welcome to Example OAuth Client');
    console.log("✓ Return to home works");
    
    console.log("=== OAuth Error Handling Test Completed ===");
});

test('OAuth client validates state parameter (CSRF protection)', async ({ page }) => {
    console.log("=== Testing CSRF Protection (State Validation) ===");
    
    // Step 1: Visit the example client and initiate OAuth flow
    console.log("Step 1: Initiating OAuth flow to get a valid session");
    await page.goto('http://localhost:3333/login');
    
    // Wait for redirect to auth server
    await page.waitForURL(/localhost:8080/, { timeout: 10000 });
    
    // Step 2: Simulate CSRF attack by navigating to callback with invalid state
    console.log("Step 2: Simulating CSRF attack with invalid state parameter");
    const maliciousUrl = 'http://localhost:3333/callback?code=fake_code&state=malicious_state';
    await page.goto(maliciousUrl);
    
    // Step 3: Verify that the invalid state is rejected
    console.log("Step 3: Verifying state validation");
    await expect(page.locator('text=Invalid state parameter')).toBeVisible({ timeout: 5000 });
    console.log("✓ Invalid state parameter correctly rejected");
    
    console.log("=== CSRF Protection Test Completed ===");
});
