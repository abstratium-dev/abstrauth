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

// Exported functions
export async function signout(page: Page) {
    await _getSignoutLink(page).click();
}

export async function navigateToAccounts(page: Page) {
    await _getAccountsLink(page).click();
}

export async function navigateToClients(page: Page) {
    await _getClientsLink(page).click();
}

export async function getCurrentUserName(page: Page): Promise<string> {
    const text = await _getUserLink(page).textContent();
    return text?.trim() || '';
}