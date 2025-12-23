import { Page, expect } from '@playwright/test';

/**
 * Dismisses all visible toast notifications.
 * Toast notifications can block UI interactions, so this should be called
 * before clicking elements that might be obscured.
 */
export async function dismissToasts(page: Page) {
    try {
        // Simple approach: just wait for toasts to auto-dismiss or click close if available
        const toasts = page.locator('.toast');
        const count = await toasts.count().catch(() => 0);
        
        if (count === 0) {
            return;
        }
        
        console.log(`Dismissing ${count} toast notification(s)...`);
        
        // Try to click close buttons on all toasts
        const closeButtons = page.locator('.toast-close');
        const buttonCount = await closeButtons.count().catch(() => 0);
        
        for (let i = 0; i < buttonCount; i++) {
            try {
                await closeButtons.nth(i).click({ timeout: 500 });
            } catch {
                // Ignore - toast might have auto-dismissed
            }
        }
        
        // Wait for toasts to disappear, but don't fail if they don't
        await expect(async () => {
            const remaining = await toasts.count().catch(() => 0);
            expect(remaining).toBe(0);
        }).toPass({ timeout: 3000 }).catch(() => {
            // Just log and continue if toasts don't disappear
            console.log('Some toasts still visible, continuing anyway');
        });
    } catch (error) {
        // If anything goes wrong, just log and continue
        console.log('Error dismissing toasts, continuing anyway:', error);
    }
}

/**
 * Waits for a success toast to appear with the specified text.
 * Useful for verifying that an operation completed successfully.
 */
export async function waitForSuccessToast(page: Page, expectedText: RegExp | string, timeout: number = 5000) {
    const successToast = page.locator('.toast-success, .success-message').filter({ hasText: expectedText });
    await expect(successToast).toBeVisible({ timeout });
}

/**
 * Waits for an error toast to appear with the specified text.
 * Useful for verifying that an operation failed as expected.
 */
export async function waitForErrorToast(page: Page, expectedText: RegExp | string, timeout: number = 5000) {
    const errorToast = page.locator('.toast-error, .error-message').filter({ hasText: expectedText });
    await expect(errorToast).toBeVisible({ timeout });
}
