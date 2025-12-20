import { Page, expect } from '@playwright/test';

/**
 * Dismisses all visible toast notifications.
 * Toast notifications can block UI interactions, so this should be called
 * before clicking elements that might be obscured.
 */
export async function dismissToasts(page: Page) {
    const toastCloseButtons = page.locator('.toast-close');
    const count = await toastCloseButtons.count();
    
    if (count > 0) {
        console.log(`Dismissing ${count} toast notification(s)...`);
    }
    
    // Click all close buttons
    for (let i = 0; i < count; i++) {
        try {
            await toastCloseButtons.nth(i).click({ timeout: 1000 });
        } catch {
            // Toast might have auto-dismissed, continue
        }
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
