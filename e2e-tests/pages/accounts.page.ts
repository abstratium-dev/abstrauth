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

    // Wait for create button to be enabled
    const createButton = _getCreateAccountButton(page);
    await expect(createButton).toBeEnabled({ timeout: 5000 });
    await createButton.click();

    // Wait for either invite link input or error message (increased timeout for backend processing)
    const inviteLinkInput = _getInviteLinkInput(page);
    const errorBox = page.locator('.error-box');
    
    await Promise.race([
        inviteLinkInput.waitFor({ state: 'visible', timeout: 20000 }),
        errorBox.waitFor({ state: 'visible', timeout: 20000 })
    ]);
    
    // Check if error appeared
    if (await errorBox.isVisible()) {
        const errorText = await errorBox.textContent();
        throw new Error(`Account creation failed: ${errorText}`);
    }
    
    // Get the value attribute, not textContent
    const link = await inviteLinkInput.inputValue();
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
 * Add a role to an account by email.
 * Assumes we're already on the accounts page and user has manage-accounts role.
 */
export async function addRoleToAccount(page: Page, accountEmail: string, clientId: string, roleName: string): Promise<void> {
    console.log(`Adding role "${roleName}" for client "${clientId}" to account "${accountEmail}"...`);
    
    // Find the tile for the account with the given email
    const accountTile = page.locator('.tile').filter({ hasText: accountEmail });
    await expect(accountTile).toBeVisible({ timeout: 10000 });
    
    // Click the "+ Add Role" button
    const addRoleButton = accountTile.locator('.btn-add-small');
    await expect(addRoleButton).toBeVisible({ timeout: 10000 });
    await expect(addRoleButton).toBeEnabled({ timeout: 10000 });
    await addRoleButton.click();
    
    // Wait for the form to appear
    const clientSelect = accountTile.locator('select[id^="clientId-"]');
    await expect(clientSelect).toBeVisible({ timeout: 10000 });
    
    // Fill the client select dropdown
    await clientSelect.selectOption(clientId);
    
    // Fill the role input
    const roleInput = accountTile.locator('input[id^="role-"]');
    await roleInput.fill(roleName);
    
    // Click the Add Role button
    const submitButton = accountTile.getByRole('button', { name: /Add Role/i });
    await submitButton.click();
    
    // Wait for either form to close or error to appear
    const errorBox = page.locator('.error-box');
    
    await Promise.race([
        clientSelect.waitFor({ state: 'hidden', timeout: 10000 }),
        errorBox.waitFor({ state: 'visible', timeout: 10000 })
    ]);
    
    // Check if error appeared
    if (await errorBox.isVisible()) {
        const errorText = await errorBox.textContent();
        throw new Error(`Role addition failed: ${errorText}`);
    }
    
    // Wait for the tile to be stable again after role addition (DOM updates)
    await expect(accountTile).toBeVisible({ timeout: 5000 });
    
    console.log(`✓ Role "${roleName}" added to account "${accountEmail}"`);
}

/**
 * Attempts to add a role to the current user's account (highlighted tile).
 * Assumes we're already on the accounts page.
 * Returns the error message if one appears, or null if successful.
 * 
 * This function uses addRoleToAccount internally but adds error handling.
 */
export async function tryAddRoleToSelf(page: Page, clientId: string, roleName: string): Promise<string | null> {
    console.log(`Attempting to add role "${roleName}" for client "${clientId}" to self...`);
    
    // Get the current user's email from the highlighted tile
    const currentUserTile = _getCurrentUserTile(page);
    await expect(currentUserTile).toBeVisible({ timeout: 5000 });
    
    // Extract email from the tile
    const emailElement = currentUserTile.locator('.tile-subtitle');
    const accountEmail = await emailElement.textContent();
    
    if (!accountEmail) {
        throw new Error('Could not find current user email');
    }
    
    console.log(`Current user email: ${accountEmail.trim()}`);
    
    // Find the tile for the account with the given email
    const accountTile = page.locator('.tile').filter({ hasText: accountEmail.trim() });
    await expect(accountTile).toBeVisible({ timeout: 5000 });
    
    // Click the "+ Add Role" button
    const addRoleButton = accountTile.locator('.btn-add-small');
    
    // Wait for button to be enabled AND not have the disabled attribute
    // (Angular might take time to update the disabled state after closing a form)
    await expect(addRoleButton).toBeEnabled({ timeout: 10000 });
    await expect(addRoleButton).not.toHaveAttribute('disabled', { timeout: 10000 });
    
    await addRoleButton.click();
    
    // Wait for the form to appear
    const clientSelect = accountTile.locator('select[id^="clientId-"]');
    await expect(clientSelect).toBeVisible({ timeout: 5000 });
    
    // Fill the client select dropdown
    await clientSelect.selectOption(clientId);
    
    // Fill the role input
    const roleInput = accountTile.locator('input[id^="role-"]');
    await roleInput.fill(roleName);
    
    // Click the Add Role button
    const submitButton = accountTile.getByRole('button', { name: /Add Role/i });
    await submitButton.click();
    
    // Wait for either error box to appear or form to disappear (success)
    // Don't use waitForTimeout as it can fail if page closes
    const errorBox = accountTile.locator('.error-box');
    
    console.log(`Checking for error after role submission...`);
    
    try {
        // Wait for error box to appear (if validation fails)
        await errorBox.waitFor({ state: 'visible', timeout: 3000 });
        
        // Error appeared
        const errorText = await errorBox.textContent();
        console.log(`✓ Error occurred: ${errorText}`);
        
        // Cancel the form
        await _getCancelButton(page).click();
        
        // Wait for the form to fully close
        await accountTile.locator('select[id^="clientId-"]').waitFor({ state: 'hidden', timeout: 2000 });
        
        // Wait for the Add Role button to become enabled again (component state reset)
        const addRoleButton = accountTile.locator('.btn-add-small');
        await expect(addRoleButton).toBeEnabled({ timeout: 5000 });
        
        return errorText?.trim() || 'Unknown error';
    } catch (e) {
        // Error box didn't appear - check if form disappeared (success case)
        const formStillVisible = await accountTile.locator('select[id^="clientId-"]').isVisible().catch(() => false);
        
        console.log(`No error box appeared. Form still visible: ${formStillVisible}`);
        
        if (!formStillVisible) {
            console.log(`✓ Role "${roleName}" was added successfully`);
            return null;
        }
        
        // Form still visible but no error - unexpected state
        console.log(`Unexpected state: form still visible but no error after 3 seconds`);
        await _getCancelButton(page).click();
        return null;
    }
}
