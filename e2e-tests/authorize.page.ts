import { Page, expect } from '@playwright/test';

// Element accessors
function _getApproveButton(page: Page) {
    return page.locator('#approve-button');
}

function _getUserLink(page: Page) {
    return page.locator("#user-link");
}

/**
 * Approves the authorization request.
 * Assumes we're already on the authorization page.
 */
export async function approveAuthorization(page: Page) {
    console.log("Approving authorization...");
    
    // Wait for approve button to be visible
    await expect(_getApproveButton(page)).toBeVisible({ timeout: 5000 });
    
    // Click approve
    await _getApproveButton(page).click();
    
    console.log("Authorization approved");
}

/**
 * Verifies that the user is signed in with the expected name.
 */
export async function verifySignedIn(page: Page, expectedName: string) {
    console.log(`Verifying user is signed in as: ${expectedName}`);
    
    // Wait for user link to be visible
    await expect(_getUserLink(page)).toBeVisible({ timeout: 5000 });
    
    // Verify the name
    await expect(_getUserLink(page)).toContainText(expectedName);
    
    console.log(`User successfully signed in as: ${expectedName}`);
}
