import { expect, Page } from '@playwright/test';

/**
 * Toggles the roles view for a specific client.
 * Assumes we're already on the clients page.
 */
export async function toggleRolesView(page: Page, clientId: string) {
    console.log(`Toggling roles view for client '${clientId}'...`);

    // Find the client card (backend prepends orgId + "__", so match exact or suffix)
    const clientCard = page.locator(`.card[data-client-id="${clientId}"], .card[data-client-id$="__${clientId}"]`);
    await expect(clientCard).toBeVisible({ timeout: 5000 });

    // Click the "Manage Client To Client Roles" button (uses data-testid for stability)
    const manageRolesButton = clientCard.locator('[data-testid="manage-client-roles-btn"]');
    await expect(manageRolesButton).toBeVisible({ timeout: 5000 });
    await manageRolesButton.click();

    console.log(`✓ Toggled roles view for client '${clientId}'`);
}

/**
 * Adds a client-to-client role (M2M role).
 * Assumes the roles view is already open for the source client.
 * NOTE: This was rewritten for the new dropdown-based UI.
 *
 * @param targetClientId  The full prefixed clientId of the target client
 * @param roleName        The role name to select
 */
export async function addRole(page: Page, targetClientId: string, roleName: string) {
    console.log(`Adding role '${roleName}' to target '${targetClientId}'...`);

    // Click "Add Client Role" button
    const addRoleButton = page.locator('[data-testid="toggle-add-client-role-btn"]');
    await expect(addRoleButton).toBeVisible({ timeout: 5000 });
    await addRoleButton.click();

    // Wait for form
    const form = page.locator('[data-testid="add-client-role-form"]');
    await expect(form).toBeVisible({ timeout: 5000 });

    // Select target client
    const targetSelect = page.locator('[data-testid="target-client-select"]');
    await targetSelect.selectOption({ value: targetClientId });

    // Wait for role dropdown and select role
    const roleSelect = page.locator('[data-testid="client-role-select"]');
    await expect(roleSelect).not.toBeDisabled({ timeout: 10000 });
    await roleSelect.selectOption({ value: roleName });

    // Submit
    const submitButton = page.locator('[data-testid="submit-add-client-role-btn"]');
    await expect(submitButton).toBeEnabled({ timeout: 5000 });
    await submitButton.click();

    // Wait for the role to appear in the list
    await expect(form).not.toBeVisible({ timeout: 10000 });
    const roleCard = page.locator(`.role-card[data-role="${roleName}"]`);
    await expect(roleCard).toBeVisible({ timeout: 5000 });

    console.log(`✓ Added role '${roleName}'`);
}

/**
 * Removes a client-to-client role (M2M role).
 * Assumes the roles view is already open for the client.
 * NOTE: This was rewritten for the new UI.
 *
 * @param targetClientId  The target client ID (used for precise role card selection)
 * @param roleName        The role name to remove
 */
export async function removeRole(page: Page, targetClientId: string, roleName: string) {
    console.log(`Removing role '${roleName}' from target '${targetClientId}'...`);

    // Find the role card using data attributes
    const roleCard = page.locator(`.role-card[data-target-client-id="${targetClientId}"][data-role="${roleName}"]`);
    await expect(roleCard).toBeVisible({ timeout: 5000 });

    // Click the remove button (uses data-testid)
    const removeButton = roleCard.locator('[data-testid="remove-client-role-btn"]');
    await expect(removeButton).toBeVisible({ timeout: 5000 });
    await removeButton.click();

    // Wait for and handle the custom confirmation dialog
    const dialog = page.locator('.dialog-overlay');
    await expect(dialog).toBeVisible({ timeout: 5000 });

    // Click the confirm button in the dialog
    const confirmButton = dialog.locator('button').filter({ hasText: /Remove/i });
    await expect(confirmButton).toBeVisible({ timeout: 5000 });
    await confirmButton.click();

    // Wait for the role to be removed
    await expect(roleCard).not.toBeVisible({ timeout: 5000 });

    console.log(`✓ Removed role '${roleName}'`);
}

/**
 * Verifies that a client-to-client role exists.
 * Assumes the roles view is already open for the client.
 * NOTE: Updated for new UI structure.
 */
export async function verifyRoleExists(page: Page, targetClientId: string, roleName: string) {
    console.log(`Verifying role '${roleName}' exists for target '${targetClientId}'...`);

    // Find the role card using data attributes
    const roleCard = page.locator(`.role-card[data-target-client-id="${targetClientId}"][data-role="${roleName}"]`);
    await expect(roleCard).toBeVisible({ timeout: 5000 });

    // Verify the target client and role name are displayed
    const targetText = await roleCard.locator('.target-client-id').textContent();
    expect(targetText).toContain(targetClientId);

    const roleText = await roleCard.locator('.role-name').textContent();
    expect(roleText?.trim()).toBe(roleName);

    console.log(`✓ Verified role '${roleName}' exists for target '${targetClientId}'`);
}

/**
 * Gets the count of roles displayed.
 * Assumes the roles view is already open for the client.
 */
export async function getRoleCount(page: Page): Promise<number> {
    const roleCards = page.locator('.role-card');
    const count = await roleCards.count();
    console.log(`Found ${count} roles`);
    return count;
}

/**
 * Verifies that the "Manage Client To Client Roles" button is disabled with a warning message.
 * This happens when a client has scopes configured.
 * NOTE: Updated to use data-testid.
 */
export async function verifyRolesDisabledForScopedClient(page: Page, clientId: string) {
    console.log(`Verifying roles are disabled for scoped client '${clientId}'...`);

    // Find the client card (backend prepends orgId + "__", so match exact or suffix)
    const clientCard = page.locator(`.card[data-client-id="${clientId}"], .card[data-client-id$="__${clientId}"]`);
    await expect(clientCard).toBeVisible({ timeout: 5000 });

    // Verify the "Manage Client To Client Roles" button is disabled
    const manageRolesButton = clientCard.locator('[data-testid="manage-client-roles-btn"]');
    await expect(manageRolesButton).toBeDisabled({ timeout: 5000 });

    // Verify the warning message is displayed
    const warningMessage = clientCard.locator('.form-hint').filter({
        hasText: /Roles can only be managed for clients with no scopes configured/i
    });
    await expect(warningMessage).toBeVisible({ timeout: 5000 });

    console.log(`✓ Verified roles are disabled for scoped client '${clientId}'`);
}

/**
 * Cancels the add role form.
 * Assumes the add role form is currently open.
 * NOTE: Updated for new UI.
 */
export async function cancelAddRole(page: Page) {
    console.log('Canceling add role form...');

    const form = page.locator('[data-testid="add-client-role-form"]');
    await expect(form).toBeVisible({ timeout: 5000 });

    const cancelButton = form.getByRole('button', { name: /Cancel/i });
    await expect(cancelButton).toBeVisible({ timeout: 5000 });
    await cancelButton.click();

    // Verify the form is hidden
    await expect(form).not.toBeVisible({ timeout: 5000 });

    console.log('✓ Canceled add role form');
}

// ============================================================================
// Client-to-Client (M2M) Role Management Functions (new dropdown-based UI)
// ============================================================================

/**
 * Returns the client card locator for the given (unprefixed) clientId.
 * The backend prepends orgId + "__" so we match either exact or suffix.
 */
function _clientCard(page: Page, clientId: string) {
    return page.locator(`.card[data-client-id="${clientId}"], .card[data-client-id$="__${clientId}"]`);
}

/**
 * Opens (or closes) the client-to-client roles panel for a client.
 * Assumes we are already on the clients page.
 */
export async function toggleClientRolesView(page: Page, clientId: string): Promise<void> {
    console.log(`Toggling client roles view for '${clientId}'...`);
    const card = _clientCard(page, clientId);
    await expect(card).toBeVisible({ timeout: 10000 });
    const btn = card.locator('[data-testid="manage-client-roles-btn"]');
    await expect(btn).toBeVisible({ timeout: 5000 });
    await btn.click();
    console.log(`✓ Toggled client roles view for '${clientId}'`);
}

/**
 * Adds a client-to-client role.
 * Assumes the roles panel is already open for the source client.
 *
 * @param targetClientId  The full prefixed clientId of the target client as it appears in the dropdown.
 * @param role            The role name to select.
 */
export async function addClientRole(page: Page, targetClientId: string, role: string): Promise<void> {
    console.log(`Adding client role: target='${targetClientId}' role='${role}'...`);

    // Open the add-role form
    const addBtn = page.locator('[data-testid="toggle-add-client-role-btn"]');
    await expect(addBtn).toBeVisible({ timeout: 5000 });
    await addBtn.click();

    // Wait for the form to appear
    const form = page.locator('[data-testid="add-client-role-form"]');
    await expect(form).toBeVisible({ timeout: 5000 });

    // Select target client
    const targetSelect = page.locator('[data-testid="target-client-select"]');
    await expect(targetSelect).toBeVisible({ timeout: 5000 });
    await targetSelect.selectOption({ value: targetClientId });
    console.log(`  Selected target client: ${targetClientId}`);

    // Wait for role dropdown to be populated (loading indicator gone, options present)
    const roleSelect = page.locator('[data-testid="client-role-select"]');
    await expect(roleSelect).not.toBeDisabled({ timeout: 10000 });

    // Select role
    await roleSelect.selectOption({ value: role });
    console.log(`  Selected role: ${role}`);

    // Submit
    const submitBtn = page.locator('[data-testid="submit-add-client-role-btn"]');
    await expect(submitBtn).toBeEnabled({ timeout: 5000 });
    await submitBtn.click();

    // Wait for the form to close (success)
    await expect(form).not.toBeVisible({ timeout: 10000 });

    // Verify the new role appears in the list
    const roleCard = page.locator(`.role-card[data-role="${role}"]`);
    await expect(roleCard).toBeVisible({ timeout: 10000 });

    console.log(`✓ Added client role '${role}' targeting '${targetClientId}'`);
}

/**
 * Removes a client-to-client role.
 * Assumes the roles panel is already open.
 */
export async function removeClientRole(page: Page, targetClientId: string, role: string): Promise<void> {
    console.log(`Removing client role: target='${targetClientId}' role='${role}'...`);

    const roleCard = page.locator(`.role-card[data-target-client-id="${targetClientId}"][data-role="${role}"]`);
    await expect(roleCard).toBeVisible({ timeout: 5000 });

    const removeBtn = roleCard.locator('[data-testid="remove-client-role-btn"]');
    await expect(removeBtn).toBeVisible({ timeout: 5000 });
    await removeBtn.click();

    // Confirm in the dialog
    const dialog = page.locator('.dialog-overlay');
    await expect(dialog).toBeVisible({ timeout: 5000 });
    const confirmBtn = dialog.locator('button').filter({ hasText: /Remove/i });
    await expect(confirmBtn).toBeVisible({ timeout: 5000 });
    await confirmBtn.click();

    // Verify the role card is gone
    await expect(roleCard).not.toBeVisible({ timeout: 10000 });
    console.log(`✓ Removed client role '${role}'`);
}

/**
 * Returns the number of role-cards currently visible in the roles panel.
 * Assumes the roles panel is already open.
 */
export async function getClientRoleCount(page: Page): Promise<number> {
    const cards = page.locator('[data-testid="client-roles-section"] .role-card');
    const count = await cards.count();
    console.log(`  Client role count: ${count}`);
    return count;
}

/**
 * Returns all option values (clientId strings) in the target-client dropdown.
 * Assumes the add-client-role form is already open.
 */
export async function getTargetClientOptions(page: Page): Promise<string[]> {
    const select = page.locator('[data-testid="target-client-select"]');
    await expect(select).toBeVisible({ timeout: 5000 });
    const options = await select.locator('option').allInnerTexts();
    console.log(`  Target client options: ${JSON.stringify(options)}`);
    return options;
}

/**
 * Returns all option values in the role dropdown for the currently selected target client.
 * Assumes a target client has already been selected in the add form.
 */
export async function getRoleOptions(page: Page): Promise<string[]> {
    const select = page.locator('[data-testid="client-role-select"]');
    await expect(select).not.toBeDisabled({ timeout: 10000 });
    const options = await select.locator('option').allInnerTexts();
    console.log(`  Role options: ${JSON.stringify(options)}`);
    return options;
}

/**
 * Opens the add-client-role form and returns the list of available target clients
 * without actually submitting anything. Cancels the form afterwards.
 */
export async function getAvailableTargetClients(page: Page): Promise<string[]> {
    const addBtn = page.locator('[data-testid="toggle-add-client-role-btn"]');
    await expect(addBtn).toBeVisible({ timeout: 5000 });
    await addBtn.click();

    const form = page.locator('[data-testid="add-client-role-form"]');
    await expect(form).toBeVisible({ timeout: 5000 });

    const options = await getTargetClientOptions(page);

    // Cancel the form
    const cancelBtn = form.getByRole('button', { name: /Cancel/i });
    await cancelBtn.click();
    await expect(form).not.toBeVisible({ timeout: 5000 });

    return options;
}
