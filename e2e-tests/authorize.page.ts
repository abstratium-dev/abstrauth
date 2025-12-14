import { Page, expect } from '@playwright/test';

/**
 * Approves the authorization request.
 * Assumes we're already on the authorization page.
 */
export async function approveAuthorization(page: Page) {
    console.log("Approving authorization...");
    
    // Wait for approve button to be visible
    await expect(page.locator('#approve-button')).toBeVisible({ timeout: 5000 });
    
    // Click approve
    await page.locator('#approve-button').click();
    
    console.log("Authorization approved");
}

/**
 * Verifies that the user is signed in with the expected name.
 */
export async function verifySignedIn(page: Page, expectedName: string) {
    console.log(`Verifying user is signed in as: ${expectedName}`);
    
    // Wait for user link to be visible
    await expect(page.locator("#user-link")).toBeVisible({ timeout: 5000 });
    
    // Verify the name
    await expect(page.locator("#user-link")).toContainText(expectedName);
    
    console.log(`User successfully signed in as: ${expectedName}`);
}
