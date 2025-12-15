import { expect, Page } from '@playwright/test';

// Element accessors
function _getAddAccountButton(page: Page) {
    return page.locator("#add-account-button");
}

function _getEmailInput(page: Page) {
    return page.locator("#email");
}

function _getAuthProviderSelect(page: Page) {
    return page.locator("#authProvider");
}

function _getNameInput(page: Page) {
    return page.locator("#name");
}

function _getCreateAccountButton(page: Page) {
    return page.locator("#create-account-button");
}

function _getInviteLinkInput(page: Page) {
    return page.locator("#invite-link-input");
}

function _getDoneButton(page: Page) {
    return page.getByRole("button", { name: "Done" });
}

function _getAccountTiles(page: Page) {
    return page.locator('.tile');
}

function _getCurrentUserTile(page: Page) {
    return page.locator('.tile.highlighted-tile');
}

function _getAddRoleButton(page: Page) {
    return page.getByRole('button', { name: /^Add Role$/i });
}

function _getCancelButton(page: Page) {
    return page.getByRole('button', { name: /Cancel/i });
}

function _getDeleteAccountButton(page: Page) {
    return page.getByRole('button', { name: /Delete Account/i });
}

export async function addAccount(page: Page, email: string, name: string): Promise<string> {
    await _getAddAccountButton(page).click();
    await _getEmailInput(page).fill(email);
    
    // select "native" authProvider
    await _getAuthProviderSelect(page).selectOption("native");

    await _getNameInput(page).fill(name);

    await _getCreateAccountButton(page).click();

    // Wait for invite link input to be visible
    await expect(_getInviteLinkInput(page)).toBeVisible({ timeout: 5000 });
    
    // Get the value attribute, not textContent
    const link = await _getInviteLinkInput(page).inputValue();
    console.log("Invite link: " + link);

    await _getDoneButton(page).click();
    
    return link || '';
}

/**
 * Deletes all accounts except the one with the given name.
 * Assumes we're already on the accounts page.
 */
export async function deleteAccountsExcept(page: Page, keepAccountName: string) {
    console.log(`Deleting all accounts except: ${keepAccountName}`);
    
    // Wait for accounts to load
    const tiles = _getAccountTiles(page);
    await tiles.first().waitFor({ state: 'visible', timeout: 5000 }).catch(() => {
        console.log("No accounts found on page");
    });
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
            await _getDeleteAccountButton(page).click();
            
            // Wait for the account to be deleted and DOM to update
            await page.waitForTimeout(1000);
        } else {
            console.log(`Keeping account: ${accountName}`);
        }
    }
    
    console.log("Finished deleting accounts");
}

/**
 * Attempts to add a role to the current user's account.
 * Assumes we're already on the accounts page.
 * Returns the error message if one appears, or null if successful.
 */
export async function tryAddRoleToSelf(page: Page, clientId: string, roleName: string): Promise<string | null> {
    console.log(`Attempting to add role "${roleName}" for client "${clientId}"...`);
    
    // Wait a bit to ensure any previous DOM updates are complete
    await page.waitForTimeout(500);
    
    // Find the highlighted tile (current user's account)
    const currentUserTile = _getCurrentUserTile(page);
    await expect(currentUserTile).toBeVisible({ timeout: 5000 });
    
    // Wait for the "+ Add Role" button to be enabled (not disabled)
    // The button is disabled when addingRoleForAccountId !== null
    const addRoleButton = currentUserTile.locator('.btn-add-small');
    await expect(addRoleButton).toBeEnabled({ timeout: 5000 });
    
    // Click the "+ Add Role" button
    await addRoleButton.click();
    
    // Wait for the form to appear
    const clientSelect = currentUserTile.locator('select[id^="clientId-"]');
    await expect(clientSelect).toBeVisible({ timeout: 5000 });
    
    // Use page.evaluate to fill and submit the form in one atomic operation
    // This avoids all the element detachment issues from Angular's change detection
    await page.evaluate(({clientId, roleName}) => {
        // Find the select element for client
        const selectElement = document.querySelector('.tile.highlighted-tile select[id^="clientId-"]') as HTMLSelectElement;
        if (selectElement) {
            selectElement.value = clientId;
            selectElement.dispatchEvent(new Event('input', { bubbles: true }));
            selectElement.dispatchEvent(new Event('change', { bubbles: true }));
        }
        
        // Find the input element for role
        const roleInput = document.querySelector('.tile.highlighted-tile input[id^="role-"]') as HTMLInputElement;
        if (roleInput) {
            roleInput.value = roleName;
            roleInput.dispatchEvent(new Event('input', { bubbles: true }));
            roleInput.dispatchEvent(new Event('change', { bubbles: true }));
        }
        
        // Wait a moment for Angular to process, then click submit button
        return new Promise<void>((resolve) => {
            setTimeout(() => {
                const submitButton = document.querySelector('.tile.highlighted-tile form button[type="submit"]') as HTMLButtonElement;
                if (submitButton) {
                    submitButton.click();
                }
                resolve();
            }, 300);
        });
    }, {clientId, roleName});
    
    // Wait for the submission to process
    await page.waitForTimeout(500);
    
    // Wait for either error box to appear or form to disappear (success)
    const errorBox = currentUserTile.locator('.error-box');
    
    // Debug: check what's on the page
    const pageContent = await page.content();
    console.log(`Checking for error after role submission...`);
    
    try {
        // Wait for error box to appear (if validation fails)
        await errorBox.waitFor({ state: 'visible', timeout: 3000 });
        
        // Error appeared
        const errorText = await errorBox.textContent();
        console.log(`✓ Error occurred: ${errorText}`);
        
        // Cancel the form
        await _getCancelButton(page).click();
        
        // Wait for the form to fully close and tile to be ready for next operation
        await currentUserTile.locator('select[id^="clientId-"]').waitFor({ state: 'hidden', timeout: 2000 });
        
        return errorText?.trim() || 'Unknown error';
    } catch (e) {
        // Error box didn't appear - check if form disappeared (success case)
        const formStillVisible = await currentUserTile.locator('select[id^="clientId-"]').isVisible().catch(() => false);
        
        console.log(`No error box appeared. Form still visible: ${formStillVisible}`);
        
        if (!formStillVisible) {
            console.log(`✗ Role "${roleName}" was added successfully (should have failed!)`);
            return null;
        }
        
        // Form still visible but no error - unexpected state
        console.log(`Unexpected state: form still visible but no error after 3 seconds`);
        
        // Check if there's an error in the page
        const hasError = await page.locator('.error-box').count();
        console.log(`Error boxes on page: ${hasError}`);
        
        await _getCancelButton(page).click();
        return null;
    }
}
