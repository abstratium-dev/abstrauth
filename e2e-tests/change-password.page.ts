import { Page, expect } from '@playwright/test';

/**
 * Changes password on the change-password page.
 * Assumes we're already on the change-password page.
 * The current password is pre-filled from invite data.
 */
export async function changePassword(page: Page, newPassword: string) {
    console.log("Changing password...");
    
    // Wait for the change password form to be visible
    await expect(page.locator('#newPassword')).toBeVisible({ timeout: 5000 });
    
    // Fill in new password fields
    await page.locator('#newPassword').fill(newPassword);
    await page.locator('#confirmPassword').fill(newPassword);
    
    // Click the change password button
    await page.getByRole('button', { name: /Change Password/i }).click();
    
    console.log("Password changed successfully");
}
