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

    // Collect all client IDs upfront to avoid index-shifting issues as cards are removed
    const allClientIds: string[] = [];
    const count = await cards.count();
    for (let i = 0; i < count; i++) {
        const id = await cards.nth(i).getAttribute('data-client-id');
        if (id) allClientIds.push(id);
    }
    console.log(`Found ${allClientIds.length} client cards`);
    
    for (const clientId of allClientIds) {
        console.log(`Checking client: ${clientId}`);
        
        if (!_clientIdMatches(clientId, keepClientId)) {
            console.log(`Deleting client: ${clientId}`);
            
            // Use a stable locator keyed by data-client-id so index shifts don't matter
            const card = page.locator(`.card[data-client-id="${clientId}"]`);
            
            // The button may not be visible if the current user doesn't own the client
            const deleteButton = card.locator('.btn-icon-danger').first();
            const isDeleteVisible = await deleteButton.isVisible().catch(() => false);
            if (!isDeleteVisible) {
                console.log(`Skipping client '${clientId}' - delete button not visible (not owned by current user)`);
                continue;
            }
            await deleteButton.click();
            
            // Wait for confirmation dialog and confirm
            const confirmBtn = _getDeleteClientButton(page);
            await expect(confirmBtn).toBeVisible({ timeout: 5000 });
            await confirmBtn.click();
            
            // Wait for this specific card to be removed using the stable locator
            await expect(card).not.toBeAttached({ timeout: 10000 });
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
    
    // Collect all client IDs upfront to avoid index-shifting issues
    const allClientIds: string[] = [];
    for (let i = 0; i < count; i++) {
        const id = await cards.nth(i).getAttribute('data-client-id');
        if (id) allClientIds.push(id);
    }

    for (const cardClientId of allClientIds) {
        if (_clientIdMatches(cardClientId, clientId)) {
            console.log(`Found client '${clientId}', deleting...`);

            // Use a stable locator keyed by data-client-id so index shifts don't matter
            const card = page.locator(`.card[data-client-id="${cardClientId}"]`);

            // Find and click the delete button (trash icon) for this client
            const deleteButton = card.locator('.btn-icon-danger').first();
            await deleteButton.click();
            
            // Wait for confirmation dialog and confirm
            const confirmButton = _getDeleteClientButton(page);
            await expect(confirmButton).toBeVisible({ timeout: 5000 });
            await confirmButton.click();
            
            // Wait for the card to be removed from the DOM
            await expect(card).not.toBeAttached({ timeout: 10000 });
            
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
