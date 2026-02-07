import { expect, Page } from '@playwright/test';

/**
 * Toggles the secrets view for a specific client.
 * Assumes we're already on the clients page.
 */
export async function toggleSecretsView(page: Page, clientId: string) {
    console.log(`Toggling secrets view for client '${clientId}'...`);
    
    // Find the client card
    const clientCard = page.locator(`.card[data-client-id="${clientId}"]`);
    await expect(clientCard).toBeVisible({ timeout: 5000 });
    
    // Click the "Manage Secrets" button
    const manageSecretsButton = clientCard.getByRole('button', { name: /Manage Secrets|Hide Secrets/i });
    await expect(manageSecretsButton).toBeVisible({ timeout: 5000 });
    await manageSecretsButton.click();
    
    // Wait for the secrets section to appear (if opening) or check if it's visible
    const secretsSection = page.locator('.secrets-section');
    const isVisible = await secretsSection.isVisible().catch(() => false);
    
    if (isVisible) {
        // If opening, wait for loading to complete
        const loadingIndicator = page.locator('.secrets-section .loading');
        if (await loadingIndicator.isVisible().catch(() => false)) {
            await expect(loadingIndicator).not.toBeVisible({ timeout: 5000 });
        }
    }
    
    console.log(`✓ Toggled secrets view for client '${clientId}'`);
}

/**
 * Creates a new secret for a client.
 * Assumes the secrets view is already open for the client.
 * Returns the generated secret value.
 */
export async function createSecret(page: Page, description: string, expiresInDays?: number): Promise<string> {
    console.log(`Creating new secret with description '${description}'...`);
    
    // Click "Generate New Secret" button
    const generateButton = page.getByRole('button', { name: /Generate New Secret/i });
    await expect(generateButton).toBeVisible({ timeout: 5000 });
    await generateButton.click();
    
    // Fill in the form
    await page.locator('#secret-description').fill(description);
    
    if (expiresInDays !== undefined) {
        await page.locator('#secret-expiry').fill(expiresInDays.toString());
    }
    
    // Submit the form
    const createButton = page.getByRole('button', { name: /^Generate Secret$/i });
    await expect(createButton).toBeVisible({ timeout: 5000 });
    await createButton.click();
    
    // Wait for the secret modal to appear
    console.log('Waiting for client secret modal...');
    const secretModal = page.locator('.secret-modal');
    await expect(secretModal).toBeVisible({ timeout: 5000 });
    
    // Extract the client secret from the modal
    const secretValue = page.locator('.secret-value');
    await expect(secretValue).toBeVisible({ timeout: 5000 });
    const clientSecret = await secretValue.textContent();
    
    if (!clientSecret) {
        throw new Error('Failed to extract client secret from modal');
    }
    
    console.log(`✓ Captured client secret: ${clientSecret.substring(0, 10)}...`);
    
    // Enable the close button (bypass clipboard requirement for test)
    const closeButton = page.getByRole('button', { name: /I have saved the secret/i });
    await closeButton.evaluate((btn) => btn.removeAttribute('disabled'));
    
    console.log('✓ Enabled close button (bypassed clipboard requirement for test)');
    
    // Close the modal
    await expect(closeButton).toBeEnabled({ timeout: 1000 });
    await closeButton.click();
    
    // Wait for modal to close
    await expect(secretModal).not.toBeVisible({ timeout: 5000 });
    
    console.log(`✓ Created new secret with description '${description}'`);
    return clientSecret;
}

/**
 * Revokes a secret by its description.
 * Assumes the secrets view is already open for the client.
 */
export async function revokeSecret(page: Page, description: string) {
    console.log(`Revoking secret with description '${description}'...`);
    
    // Find the secret card by description
    const secretCard = page.locator('.secret-card').filter({ hasText: description });
    await expect(secretCard).toBeVisible({ timeout: 5000 });
    
    // Click the revoke button
    const revokeButton = secretCard.getByRole('button', { name: /Revoke/i });
    await expect(revokeButton).toBeVisible({ timeout: 5000 });
    await revokeButton.click();
    
    // Confirm the revocation in the dialog
    const confirmButton = page.getByRole('button', { name: /Revoke Secret/i });
    await expect(confirmButton).toBeVisible({ timeout: 5000 });
    await confirmButton.click();
    
    // Wait for the secret to be revoked (badge should change to "Revoked")
    const revokedBadge = secretCard.locator('.badge-danger').filter({ hasText: /Revoked/i });
    await expect(revokedBadge).toBeVisible({ timeout: 5000 });
    
    console.log(`✓ Revoked secret with description '${description}'`);
}

/**
 * Deletes a revoked secret by its description.
 * Assumes the secrets view is already open for the client.
 */
export async function deleteSecret(page: Page, description: string) {
    console.log(`Deleting secret with description '${description}'...`);
    
    // Find the secret card by description
    const secretCard = page.locator('.secret-card').filter({ hasText: description });
    await expect(secretCard).toBeVisible({ timeout: 5000 });
    
    // Click the delete button
    const deleteButton = secretCard.getByRole('button', { name: /Delete/i });
    await expect(deleteButton).toBeVisible({ timeout: 5000 });
    await deleteButton.click();
    
    // Wait for the secret to be deleted (card should disappear)
    await expect(secretCard).not.toBeVisible({ timeout: 5000 });
    
    console.log(`✓ Deleted secret with description '${description}'`);
}

/**
 * Verifies that a secret exists with the given description and status.
 * Assumes the secrets view is already open for the client.
 */
export async function verifySecretExists(page: Page, description: string, status: 'Active' | 'Revoked' | 'Expired') {
    console.log(`Verifying secret '${description}' has status '${status}'...`);
    
    // Find the secret card by description
    const secretCard = page.locator('.secret-card').filter({ hasText: description });
    await expect(secretCard).toBeVisible({ timeout: 5000 });
    
    // Verify the status badge
    const statusBadge = secretCard.locator('.badge').filter({ hasText: new RegExp(status, 'i') });
    await expect(statusBadge).toBeVisible({ timeout: 5000 });
    
    console.log(`✓ Verified secret '${description}' has status '${status}'`);
}

/**
 * Gets the count of secrets displayed.
 * Assumes the secrets view is already open for the client.
 */
export async function getSecretCount(page: Page): Promise<number> {
    const secretCards = page.locator('.secret-card');
    const count = await secretCards.count();
    console.log(`Found ${count} secrets`);
    return count;
}
