import { Page } from '@playwright/test';

// Element accessors
function _getSignoutLink(page: Page) {
    return page.locator("#signout-link");
}

function _getAccountsLink(page: Page) {
    return page.locator("#accounts-link");
}

function _getClientsLink(page: Page) {
    return page.locator("#clients-link");
}

function _getUserLink(page: Page) {
    return page.locator("#user-link");
}

// Helper to dismiss any visible toasts
async function dismissToasts(page: Page) {
    const toastCloseButtons = page.locator('.toast-close');
    const count = await toastCloseButtons.count();
    
    // Click all close buttons
    for (let i = 0; i < count; i++) {
        try {
            await toastCloseButtons.nth(i).click({ timeout: 1000 });
        } catch {
            // Toast might have auto-dismissed, continue
        }
    }
}

// Exported functions
export async function signout(page: Page) {
    // Dismiss any toast notifications that might block the signout link
    await dismissToasts(page);
    
    await _getSignoutLink(page).click();
    // Wait for navigation to complete to avoid race conditions with subsequent page.goto()
    await page.waitForTimeout(300);
}

export async function navigateToAccounts(page: Page) {
    // Dismiss any toast notifications that might block navigation links
    await dismissToasts(page);
    
    await _getAccountsLink(page).click();
}

export async function navigateToClients(page: Page) {
    // Dismiss any toast notifications that might block navigation links
    await dismissToasts(page);
    
    await _getClientsLink(page).click();
}

export async function getCurrentUserName(page: Page): Promise<string> {
    const text = await _getUserLink(page).textContent();
    return text?.trim() || '';
}