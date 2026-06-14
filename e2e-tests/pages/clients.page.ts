import { expect, Page } from '@playwright/test';

// Element accessors
function _getClientCards(page: Page) {
    return page.locator('.card');
}

function _getDeleteClientButton(page: Page) {
    return page.getByRole('button', { name: /Delete Client/i });
}

/**
 * Check if a card's clientId matches the search term.
 * The backend prepends orgId + "__" to client IDs, so data-client-id
 * contains the prefixed ID while tests pass unprefixed IDs.
 */
function _clientIdMatches(cardClientId: string, searchClientId: string): boolean {
    return cardClientId === searchClientId || cardClientId.endsWith('__' + searchClientId);
}

/**
 * Adds a new OAuth client.
 * Assumes we're already on the clients page.
 * Returns the actual prefixed clientId and the client secret displayed in the modal.
 */
export async function addClient(page: Page, clientId: string, clientName: string, redirectUri: string, scopes: string): Promise<{ clientId: string, secret: string }> {
    console.log(`Adding new client '${clientId}'...`);

    // Wait for the page to load and the Add Client button to be visible
    const addClientButton = page.getByRole('button', { name: /Add Client/i });
    await expect(addClientButton).toBeVisible({ timeout: 10000 });
    await addClientButton.click();

    // Fill in the form
    await page.locator('#clientId').fill(clientId);
    await page.locator('#clientName').fill(clientName);
    await page.locator('#redirectUris').fill(redirectUri);
    await page.locator('#allowedScopes').fill(scopes);

    // Submit the form
    const createButton = page.getByRole('button', { name: /Create Client/i });
    await expect(createButton).toBeEnabled({ timeout: 5000 });
    await createButton.click();

    // Wait for the form to close (client created successfully)
    await page.locator('#clientId').waitFor({ state: 'hidden', timeout: 5000 });

    // Wait for the client secret modal to appear
    console.log('Waiting for client secret modal...');
    const secretModal = page.locator('.secret-modal');
    await expect(secretModal).toBeVisible({ timeout: 5000 });

    // Extract the actual clientId and secret from the modal
    // The modal contains two .secret-value elements: first is clientId, second is the actual secret
    const clientIdValue = page.locator('.secret-value').nth(0);
    await expect(clientIdValue).toBeVisible({ timeout: 5000 });
    const actualClientId = await clientIdValue.textContent();
    if (!actualClientId) {
        throw new Error('Failed to extract client ID from modal');
    }

    const secretValue = page.locator('.secret-value').nth(1);
    await expect(secretValue).toBeVisible({ timeout: 5000 });
    const clientSecret = await secretValue.textContent();
    if (!clientSecret) {
        throw new Error('Failed to extract client secret from modal');
    }

    console.log(`✓ Captured actual clientId: ${actualClientId}`);
    console.log(`✓ Captured client secret: ${clientSecret.substring(0, 10)}...`);

    // In test environment, clipboard API doesn't work, so we'll just enable the button directly
    // by removing the disabled attribute
    const closeButton = page.getByRole('button', { name: /I have saved the secret/i });
    await closeButton.evaluate((btn) => btn.removeAttribute('disabled'));

    console.log('✓ Enabled close button (bypassed clipboard requirement for test)');

    // Now the close button should be enabled
    await expect(closeButton).toBeEnabled({ timeout: 1000 });

    // Close the modal
    await closeButton.click();

    // Wait for modal to close
    await expect(secretModal).not.toBeVisible({ timeout: 5000 });

    console.log(`✓ Created new client '${actualClientId}'`);
    return { clientId: actualClientId, secret: clientSecret };
}

/**
 * Deletes all clients except the specified one.
 * Assumes we're already on the clients page.
 */
export async function deleteClientsExcept(page: Page, keepClientId: string) {
    console.log(`Deleting all clients except: ${keepClientId}`);
    
    // Wait for clients to load
    const cards = _getClientCards(page);
    await cards.first().waitFor({ state: 'visible', timeout: 5000 }).catch(() => {
        console.log("No clients found on page");
    });
    const count = await cards.count();
    console.log(`Found ${count} client cards`);
    
    // Process cards in reverse order to avoid index shifting issues
    for (let i = count - 1; i >= 0; i--) {
        const card = cards.nth(i);
        
        // Get the client ID from the data attribute
        const clientId = await card.getAttribute('data-client-id');
        
        console.log(`Checking client: ${clientId}`);
        
        if (clientId && !_clientIdMatches(clientId, keepClientId)) {
            console.log(`Deleting client: ${clientId}`);
            
            // Find and click the delete button (trash icon) for this client
            const deleteButton = card.locator('.btn-icon-danger').first();
            await deleteButton.click();
            
            // Wait for confirmation dialog and confirm
            await page.waitForTimeout(500);
            
            // Click the confirm button in the dialog
            // The dialog should have a button with text like "Delete Client"
            await _getDeleteClientButton(page).click();
            
            // Wait for the client to be deleted and DOM to update
            await page.waitForTimeout(1000);
        } else {
            console.log(`Keeping client: ${clientId}`);
        }
    }
    
    console.log("Finished deleting clients");
}

/**
 * Deletes a specific client by ID if it exists.
 * Assumes we're already on the clients page.
 * Returns true if client was found and deleted, false otherwise.
 */
export async function deleteClientIfExists(page: Page, clientId: string): Promise<boolean> {
    console.log(`Checking if client '${clientId}' exists...`);
    
    // Wait for clients to load
    const cards = _getClientCards(page);
    await cards.first().waitFor({ state: 'visible', timeout: 5000 }).catch(() => {
        console.log("No clients found on page");
        return false;
    });
    
    const count = await cards.count();
    console.log(`Found ${count} client cards`);
    
    // Iterate through all cards to find the matching client
    for (let i = 0; i < count; i++) {
        const card = cards.nth(i);
        const cardClientId = await card.getAttribute('data-client-id');
        
        if (cardClientId && _clientIdMatches(cardClientId, clientId)) {
            console.log(`Found client '${clientId}', deleting...`);
            
            // Find and click the delete button (trash icon) for this client
            const deleteButton = card.locator('.btn-icon-danger').first();
            await deleteButton.click();
            
            // Wait for confirmation dialog and confirm
            await page.waitForTimeout(500);
            
            // Click the confirm button in the dialog
            await _getDeleteClientButton(page).click();
            
            // Wait for the client to be deleted and DOM to update
            await page.waitForTimeout(1000);
            
            console.log(`✓ Deleted client '${clientId}'`);
            return true;
        }
    }
    
    console.log(`✓ Client '${clientId}' does not exist`);
    return false;
}

/**
 * Adds an allowed role to a client via the "Manage Allowed Roles" UI.
 * Assumes we're already on the clients page.
 */
export async function addAllowedRoleToClient(page: Page, clientId: string, roleName: string): Promise<void> {
    console.log(`Adding allowed role '${roleName}' to client '${clientId}'...`);

    // Find the client card by data-client-id
    const card = page.locator(`.card[data-client-id="${clientId}"]`);
    await expect(card).toBeVisible({ timeout: 5000 });

    // Click "Manage Allowed Roles" button
    const manageRolesButton = card.getByRole('button', { name: /Manage Allowed Roles/i });
    await expect(manageRolesButton).toBeVisible({ timeout: 5000 });
    await manageRolesButton.click();

    // Click "+ Add Allowed Role" button
    const addAllowedRoleBtn = card.getByRole('button', { name: /Add Allowed Role/i });
    await expect(addAllowedRoleBtn).toBeVisible({ timeout: 5000 });
    await addAllowedRoleBtn.click();

    // Fill in the role name
    const roleInput = card.locator('#allowed-role-name');
    await expect(roleInput).toBeVisible({ timeout: 5000 });
    await roleInput.fill(roleName);

    // Find the submit button inside the visible allowed-role form
    const roleForm = card.locator('.form-container.role-form').filter({ has: page.locator('#allowed-role-name') });
    const submitButton = roleForm.getByRole('button', { name: /^Add Role$/i });
    await expect(submitButton).toBeVisible({ timeout: 5000 });
    await expect(submitButton).toBeEnabled({ timeout: 5000 });
    await submitButton.click();

    // Wait for the form to close (input hidden = success)
    await roleInput.waitFor({ state: 'hidden', timeout: 10000 });

    // Verify the role appears in the list
    const roleCard = card.locator('.role-card').filter({ hasText: roleName });
    await expect(roleCard).toBeVisible({ timeout: 5000 });

    console.log(`✓ Added allowed role '${roleName}' to client '${clientId}'`);
}
