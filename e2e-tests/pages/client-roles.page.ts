import { expect, Page } from '@playwright/test';

/**
 * Toggles the roles view for a specific client.
 * Assumes we're already on the clients page.
 */
export async function toggleRolesView(page: Page, clientId: string) {
    console.log(`Toggling roles view for client '${clientId}'...`);
    
    // Find the client card
    const clientCard = page.locator(`.card[data-client-id="${clientId}"]`);
    await expect(clientCard).toBeVisible({ timeout: 5000 });
    
    // Click the "Manage Roles" button
    const manageRolesButton = clientCard.getByRole('button', { name: /Manage Roles|Hide Roles/i });
    await expect(manageRolesButton).toBeVisible({ timeout: 5000 });
    await manageRolesButton.click();
    
    console.log(`✓ Toggled roles view for client '${clientId}'`);
}

/**
 * Adds a role to a client.
 * Assumes the roles view is already open for the client.
 */
export async function addRole(page: Page, roleName: string) {
    console.log(`Adding role '${roleName}'...`);
    
    // Click "Add Role" button
    const addRoleButton = page.getByRole('button', { name: /^\+ Add Role$/i });
    await expect(addRoleButton).toBeVisible({ timeout: 5000 });
    await addRoleButton.click();
    
    // Fill in the role name
    await page.locator('#role-name').fill(roleName);
    
    // Submit the form
    const submitButton = page.getByRole('button', { name: /^Add Role$/i });
    await expect(submitButton).toBeVisible({ timeout: 5000 });
    await submitButton.click();
    
    // Wait for the role to appear in the list
    const roleCard = page.locator('.role-card').filter({ hasText: roleName });
    await expect(roleCard).toBeVisible({ timeout: 5000 });
    
    console.log(`✓ Added role '${roleName}'`);
}

/**
 * Removes a role from a client.
 * Assumes the roles view is already open for the client.
 */
export async function removeRole(page: Page, roleName: string) {
    console.log(`Removing role '${roleName}'...`);
    
    // Find the role card
    const roleCard = page.locator('.role-card').filter({ hasText: roleName });
    await expect(roleCard).toBeVisible({ timeout: 5000 });
    
    // Click the remove button
    const removeButton = roleCard.getByRole('button', { name: /Remove/i });
    await expect(removeButton).toBeVisible({ timeout: 5000 });
    await removeButton.click();
    
    // Wait for the role to be removed
    await expect(roleCard).not.toBeVisible({ timeout: 5000 });
    
    console.log(`✓ Removed role '${roleName}'`);
}

/**
 * Verifies that a role exists for a client.
 * Assumes the roles view is already open for the client.
 */
export async function verifyRoleExists(page: Page, roleName: string, clientId: string) {
    console.log(`Verifying role '${roleName}' exists for client '${clientId}'...`);
    
    // Find the role card by role name
    const roleCard = page.locator('.role-card').filter({ hasText: roleName });
    await expect(roleCard).toBeVisible({ timeout: 5000 });
    
    // Verify the group name format (clientId_role)
    const groupName = `${clientId}_${roleName}`;
    const groupNameElement = roleCard.locator('.role-group-name').filter({ hasText: groupName });
    await expect(groupNameElement).toBeVisible({ timeout: 5000 });
    
    console.log(`✓ Verified role '${roleName}' exists with group name '${groupName}'`);
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
 * Verifies that the "Manage Roles" button is disabled with a warning message.
 * This happens when a client has scopes configured.
 */
export async function verifyRolesDisabledForScopedClient(page: Page, clientId: string) {
    console.log(`Verifying roles are disabled for scoped client '${clientId}'...`);
    
    // Find the client card
    const clientCard = page.locator(`.card[data-client-id="${clientId}"]`);
    await expect(clientCard).toBeVisible({ timeout: 5000 });
    
    // Verify the "Manage Roles" button is disabled
    const manageRolesButton = clientCard.getByRole('button', { name: /Manage Roles/i });
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
 */
export async function cancelAddRole(page: Page) {
    console.log('Canceling add role form...');
    
    const cancelButton = page.getByRole('button', { name: /Cancel/i }).last();
    await expect(cancelButton).toBeVisible({ timeout: 5000 });
    await cancelButton.click();
    
    // Verify the form is hidden
    const roleNameInput = page.locator('#role-name');
    await expect(roleNameInput).not.toBeVisible({ timeout: 5000 });
    
    console.log('✓ Canceled add role form');
}
