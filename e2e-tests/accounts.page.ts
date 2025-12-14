import { test, expect, Page } from '@playwright/test';

export async function addAccount(page: Page, email: string, name: string): Promise<string> {
    await page.locator("#add-account-button").click();
    await page.locator("#email").fill(email);
    
    // select "native" authProvider
    await page.locator("#authProvider").selectOption("native");

    await page.locator("#name").fill(name);

    await page.locator("#create-account-button").click();

    // Wait for invite link input to be visible
    await page.waitForSelector("#invite-link-input", { timeout: 5000 });
    
    // Get the value attribute, not textContent
    const link = await page.locator("#invite-link-input").inputValue();
    console.log("Invite link: " + link);

    await page.getByRole("button", { name: "Done" }).click();
    
    return link || '';
}

/**
 * Deletes all accounts except the one with the given name.
 * Assumes we're already on the accounts page.
 */
export async function deleteAccountsExcept(page: Page, keepAccountName: string) {
    console.log(`Deleting all accounts except: ${keepAccountName}`);
    
    // Wait for accounts to load
    await page.waitForSelector('.tile', { timeout: 5000 }).catch(() => {
        console.log("No accounts found on page");
    });
    
    // Get all account tiles
    const tiles = page.locator('.tile');
    const count = await tiles.count();
    console.log(`Found ${count} account tiles`);
    
    // Process tiles in reverse order to avoid index shifting issues
    for (let i = count - 1; i >= 0; i--) {
        const tile = tiles.nth(i);
        
        // Get the account name from the tile - use first() to get the first matching element
        const nameElement = tile.locator('.tile-title').first();
        const accountName = await nameElement.textContent();
        
        console.log(`Checking account: ${accountName}`);
        
        if (accountName?.trim() !== keepAccountName) {
            console.log(`Deleting account: ${accountName}`);
            
            // Find and click the delete button (trash icon) for this account
            const deleteButton = tile.locator('.btn-icon-danger').first();
            await deleteButton.click();
            
            // Wait for confirmation dialog and confirm
            await page.waitForTimeout(500);
            
            // Click the confirm button in the dialog
            // The dialog should have a button with text like "Delete Account"
            await page.getByRole('button', { name: /Delete Account/i }).click();
            
            // Wait for the account to be deleted and DOM to update
            await page.waitForTimeout(1000);
        } else {
            console.log(`Keeping account: ${accountName}`);
        }
    }
    
    console.log("Finished deleting accounts");
}
