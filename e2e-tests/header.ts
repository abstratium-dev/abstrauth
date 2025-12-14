import { Page } from '@playwright/test';

export async function signout(page: Page) {
    await page.locator("#signout-link").click();
}

export async function navigateToAccounts(page: Page) {
    await page.locator("#accounts-link").click();
}

export async function navigateToClients(page: Page) {
    await page.locator("#clients-link").click();
}

export async function getCurrentUserName(page: Page): Promise<string> {
    const userLink = page.locator("#user-link");
    const text = await userLink.textContent();
    return text?.trim() || '';
}