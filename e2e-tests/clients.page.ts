import { Page } from '@playwright/test';

/**
 * Deletes all clients except the specified one.
 * Assumes we're already on the clients page.
 */
export async function deleteClientsExcept(page: Page, keepClientId: string) {
    console.log(`Deleting all clients except: ${keepClientId}`);
    
    // Wait for clients to load
    await page.waitForSelector('.card', { timeout: 5000 }).catch(() => {
        console.log("No clients found on page");
    });
    
    // Get all client cards
    const cards = page.locator('.card');
    const count = await cards.count();
    console.log(`Found ${count} client cards`);
    
    // Process cards in reverse order to avoid index shifting issues
    for (let i = count - 1; i >= 0; i--) {
        const card = cards.nth(i);
        
        // Get the client ID from the data attribute
        const clientId = await card.getAttribute('data-client-id');
        
        console.log(`Checking client: ${clientId}`);
        
        if (clientId !== keepClientId) {
            console.log(`Deleting client: ${clientId}`);
            
            // Find and click the delete button (trash icon) for this client
            const deleteButton = card.locator('.btn-icon-danger').first();
            await deleteButton.click();
            
            // Wait for confirmation dialog and confirm
            await page.waitForTimeout(500);
            
            // Click the confirm button in the dialog
            // The dialog should have a button with text like "Delete Client"
            await page.getByRole('button', { name: /Delete Client/i }).click();
            
            // Wait for the client to be deleted and DOM to update
            await page.waitForTimeout(1000);
        } else {
            console.log(`Keeping client: ${clientId}`);
        }
    }
    
    console.log("Finished deleting clients");
}
