import { Page, expect } from '@playwright/test';

// Element accessors
function _getNewPasswordInput(page: Page) {
    return page.locator('#newPassword');
}

function _getConfirmPasswordInput(page: Page) {
    return page.locator('#confirmPassword');
}

function _getChangePasswordButton(page: Page) {
    return page.getByRole('button', { name: /Change Password/i });
}

/**
 * Changes password on the change-password page.
 * Assumes we're already on the change-password page.
 * The current password is pre-filled from invite data.
 * Waits for the password change to complete and redirect to home.
 */
export async function changePassword(page: Page, newPassword: string) {
    console.log("Changing password...");
    
    // Wait for the change password form to be visible
    await expect(_getNewPasswordInput(page)).toBeVisible({ timeout: 5000 });
    
    // Fill in new password fields
    await _getNewPasswordInput(page).fill(newPassword);
    await _getConfirmPasswordInput(page).fill(newPassword);
    
    // Click the change password button and wait for navigation
    await _getChangePasswordButton(page).click();
    
    // Wait for the success toast to appear
    const successToast = page.locator('.toast-success, .success-message').filter({ hasText: /password changed successfully/i });
    await expect(successToast).toBeVisible({ timeout: 5000 });
    
    // Wait for redirect to home page (URL should be '/')
    await page.waitForURL('/', { timeout: 5000 });
    
    // Give Angular time to clear session storage and re-render
    await page.waitForTimeout(500);
    
    console.log("Password changed successfully and redirected to home");
}
