import { Page } from '@playwright/test';
import { dismissToasts } from './toast';

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

// Exported functions
export async function signout(page: Page) {
    // Dismiss any toast notifications that might block the signout link
    await dismissToasts(page);
    
    // Click signout and wait for the logout request to complete
    // In BFF pattern, this calls /api/auth/logout which clears the session cookie
    // and redirects to the post-logout path (/)
    await Promise.all([
        page.waitForResponse(response => 
            response.url().includes('/api/auth/logout') && 
            (response.status() === 302 || response.status() === 200)
        ),
        _getSignoutLink(page).click()
    ]);
    
    // Wait for navigation to complete - use 'load' instead of 'networkidle' to avoid timeout issues
    await page.waitForLoadState('load');
    
    // Clear all cookies after signout to prevent WebKit from reusing stale state cookies
    // This is especially important for WebKit which caches cookies more aggressively
    await page.context().clearCookies();
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