import { test, expect, Page } from '@playwright/test';
import { signout } from './header';

/**
 * Reusable function to ensure a user is authenticated.
 * Attempts to sign in with the provided credentials.
 * If sign in fails, it signs up with the provided credentials and name.
 * 
 * @param page - Playwright Page object
 * @param email - User email address
 * @param password - User password
 * @param name - User display name (used for signup if needed)
 */
async function ensureAuthenticated(page: Page, email: string, password: string, name: string) {
    await page.goto('/');

    // Try to sign in first
    await page.locator("#username").fill(email);
    await page.locator("#password").fill(password);
    await page.locator("#signin-button").click();

    // Wait for either the error message or the approve button to appear
    // This handles the race condition where the error might appear slowly
    const errorBox = page.getByText('Invalid username or password');
    const approveButton = page.locator("#approve-button");
    
    try {
        // Race between error appearing or approve button appearing
        await Promise.race([
            errorBox.waitFor({ state: 'visible', timeout: 5000 }),
            approveButton.waitFor({ state: 'visible', timeout: 5000 })
        ]);
    } catch (e) {
        // Neither appeared in time - this is unexpected
        console.log("Neither error nor approve button appeared");
        throw e;
    }

    // Check which one is actually visible
    const isErrorVisible = await errorBox.isVisible();
    console.log("isErrorVisible: " + isErrorVisible);
    
    if (isErrorVisible) {
        // Sign in failed, need to sign up
        await page.goto('/');
        await page.locator("#signup-link").click();

        // Fill signup form
        await page.locator("#email").fill(email);
        await page.locator("#name").fill(name);
        await page.locator("#password").fill(password);
        await page.locator("#password2").fill(password);
        await page.locator("#create-account-button").click();

        // Now sign in with the new account
        await page.locator("#signin-button").click();
        
        // Wait for approve button after successful signup and signin
        await approveButton.waitFor({ state: 'visible', timeout: 5000 });
    }

    // At this point we should be at the authorization page
    await approveButton.click();

    // Verify we're authenticated
    await expect(page.locator("#user-link")).toBeVisible();
    await expect(page.locator("#user-link")).toContainText(name);
}

export async function ensureAdminIsAuthenticated(page: Page) {
    const email = 'admin@abstratium.dev';
    const password = 'secretLong';
    const name = 'Admin';
    await ensureAuthenticated(page, email, password, name);
}

export async function ensureManagerIsAuthenticated(page: Page) {
    const email = 'manager@abstratium.dev';
    const password = 'secretLong';
    const name = 'Manager';
    await ensureAuthenticated(page, email, password, name);
}

/**
 * Attempts to sign in as admin. Returns true if successful, false if failed.
 * Does NOT sign up if sign in fails.
 */
export async function trySignInAsAdmin(page: Page): Promise<boolean> {
    const email = 'admin@abstratium.dev';
    const password = 'secretLong';
    
    await page.goto('/');
    await page.locator("#username").fill(email);
    await page.locator("#password").fill(password);
    await page.locator("#signin-button").click();

    const errorBox = page.getByText('Invalid username or password');
    const approveButton = page.locator("#approve-button");
    
    try {
        await Promise.race([
            errorBox.waitFor({ state: 'visible', timeout: 5000 }),
            approveButton.waitFor({ state: 'visible', timeout: 5000 })
        ]);
    } catch (e) {
        console.log("Neither error nor approve button appeared during admin sign in attempt");
        return false;
    }

    const isErrorVisible = await errorBox.isVisible();
    
    if (isErrorVisible) {
        console.log("Admin sign in failed - account doesn't exist");
        return false;
    }
    
    // Sign in succeeded, click approve
    await approveButton.click();
    await expect(page.locator("#user-link")).toBeVisible();
    console.log("Admin sign in succeeded");
    return true;
}

/**
 * Signs in using an invite link.
 * The invite link contains pre-filled credentials.
 */
export async function signInViaInviteLink(page: Page, inviteLink: string, expectedEmail: string) {
    console.log("Signing in via invite link...");
    
    // Navigate to invite link
    await page.goto(inviteLink);
    
    // The invite link will redirect to /authorize and auto-fill credentials
    // Wait for the signin page to load with pre-filled credentials
    await expect(page.locator('#signin-button')).toBeVisible({ timeout: 5000 });
    
    // Verify the email is pre-filled from invite
    const usernameValue = await page.locator('#username').inputValue();
    expect(usernameValue).toBe(expectedEmail);
    console.log("Email pre-filled from invite: " + usernameValue);
    
    // Just click sign in - password is already filled from invite token
    await page.locator('#signin-button').click();
    
    console.log("Sign in button clicked");
}
