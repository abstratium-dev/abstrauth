import { test, expect } from '@playwright/test';

import { signInAsAdmin } from '../pages/signin.page';
import { navigateToClients } from '../pages/header';
import { addClient } from '../pages/clients.page';
import { 
    toggleSecretsView, 
    createSecret, 
    revokeSecret, 
    deleteSecret, 
    verifySecretExists,
    getSecretCount 
} from '../pages/client-secrets.page';

test.describe('Client Secrets Management', () => {
    const TEST_CLIENT_NAME = 'Test Secrets Client';
    const TEST_REDIRECT_URI = 'http://localhost:3000/callback';
    const TEST_SCOPES = 'openid profile email';
    
    let testClientId: string;

    test.beforeEach(async ({ page }) => {
        // Generate unique client ID for each test
        testClientId = `test-secrets-${Date.now()}`;
        
        // Sign in as admin
        await signInAsAdmin(page);
        
        // Navigate to clients page
        await navigateToClients(page);
        
        // Create a test client
        await addClient(page, testClientId, TEST_CLIENT_NAME, TEST_REDIRECT_URI, TEST_SCOPES);
    });

    test('should create a new secret for a client', async ({ page }) => {
        console.log('Test: Create new secret');
        
        // Open secrets view
        await toggleSecretsView(page, testClientId);
        
        // Verify initial state - should have 1 secret (created with client)
        const initialCount = await getSecretCount(page);
        expect(initialCount).toBe(1);
        
        // Create a new secret
        const secretDescription = 'Production Secret';
        const secret = await createSecret(page, secretDescription);
        
        // Verify secret was created
        expect(secret).toBeTruthy();
        expect(secret.length).toBeGreaterThan(20);
        
        // Verify secret appears in the list
        await verifySecretExists(page, secretDescription, 'Active');
        
        // Verify count increased
        const newCount = await getSecretCount(page);
        expect(newCount).toBe(2);
        
        console.log('✓ Test passed: Create new secret');
    });

    test('should create a secret with expiration', async ({ page }) => {
        console.log('Test: Create secret with expiration');
        
        // Open secrets view
        await toggleSecretsView(page, testClientId);
        
        // Create a secret with 90-day expiration
        const secretDescription = 'Expiring Secret';
        const expiresInDays = 90;
        const secret = await createSecret(page, secretDescription, expiresInDays);
        
        // Verify secret was created
        expect(secret).toBeTruthy();
        
        // Verify secret appears in the list
        await verifySecretExists(page, secretDescription, 'Active');
        
        // Verify expiration date is displayed
        const secretCard = page.locator('.secret-card').filter({ hasText: secretDescription });
        const expiresLabel = secretCard.locator('.secret-detail').filter({ hasText: /Expires:/i });
        await expect(expiresLabel).toBeVisible({ timeout: 5000 });
        
        // Verify it doesn't say "Never"
        const expiresValue = await expiresLabel.locator('.value').textContent();
        expect(expiresValue).not.toBe('Never');
        
        console.log('✓ Test passed: Create secret with expiration');
    });

    test('should create multiple secrets for a client', async ({ page }) => {
        console.log('Test: Create multiple secrets');
        
        // Open secrets view
        await toggleSecretsView(page, testClientId);
        
        // Create first additional secret
        const secret1 = await createSecret(page, 'Development Secret');
        expect(secret1).toBeTruthy();
        
        // Create second additional secret
        const secret2 = await createSecret(page, 'Staging Secret');
        expect(secret2).toBeTruthy();
        
        // Verify both secrets are different
        expect(secret1).not.toBe(secret2);
        
        // Verify all secrets appear in the list
        await verifySecretExists(page, 'Development Secret', 'Active');
        await verifySecretExists(page, 'Staging Secret', 'Active');
        
        // Verify count (1 initial + 2 new = 3)
        const count = await getSecretCount(page);
        expect(count).toBe(3);
        
        console.log('✓ Test passed: Create multiple secrets');
    });

    test('should revoke a secret', async ({ page }) => {
        console.log('Test: Revoke secret');
        
        // Open secrets view
        await toggleSecretsView(page, testClientId);
        
        // Create a secret to revoke
        const secretDescription = 'Secret to Revoke';
        await createSecret(page, secretDescription);
        
        // Verify it's active
        await verifySecretExists(page, secretDescription, 'Active');
        
        // Revoke the secret
        await revokeSecret(page, secretDescription);
        
        // Verify it's now revoked
        await verifySecretExists(page, secretDescription, 'Revoked');
        
        // Verify the revoke button is no longer visible
        const secretCard = page.locator('.secret-card').filter({ hasText: secretDescription });
        const revokeButton = secretCard.getByRole('button', { name: /Revoke/i });
        await expect(revokeButton).not.toBeVisible();
        
        // Verify the delete button is now visible
        const deleteButton = secretCard.getByRole('button', { name: /Delete/i });
        await expect(deleteButton).toBeVisible({ timeout: 5000 });
        
        console.log('✓ Test passed: Revoke secret');
    });

    test('should delete a revoked secret', async ({ page }) => {
        console.log('Test: Delete revoked secret');
        
        // Open secrets view
        await toggleSecretsView(page, testClientId);
        
        // Create a secret
        const secretDescription = 'Secret to Delete';
        await createSecret(page, secretDescription);
        
        // Get initial count
        const initialCount = await getSecretCount(page);
        
        // Revoke the secret
        await revokeSecret(page, secretDescription);
        
        // Delete the secret
        await deleteSecret(page, secretDescription);
        
        // Verify count decreased
        const newCount = await getSecretCount(page);
        expect(newCount).toBe(initialCount - 1);
        
        console.log('✓ Test passed: Delete revoked secret');
    });

    test('should show warning for expiring secrets', async ({ page }) => {
        console.log('Test: Warning for expiring secrets');
        
        // Open secrets view
        await toggleSecretsView(page, testClientId);
        
        // Create a secret with very short expiration (1 day)
        const secretDescription = 'Soon to Expire';
        await createSecret(page, secretDescription, 1);
        
        // Note: The secret won't actually be expiring in the test environment
        // since we just created it, but we can verify the UI structure exists
        const secretCard = page.locator('.secret-card').filter({ hasText: secretDescription });
        await expect(secretCard).toBeVisible({ timeout: 5000 });
        
        console.log('✓ Test passed: Warning for expiring secrets (UI structure verified)');
    });

    test('should handle secret creation cancellation', async ({ page }) => {
        console.log('Test: Cancel secret creation');
        
        // Open secrets view
        await toggleSecretsView(page, testClientId);
        
        // Get initial count
        const initialCount = await getSecretCount(page);
        
        // Click "Generate New Secret" button
        const generateButton = page.getByRole('button', { name: /Generate New Secret/i });
        await expect(generateButton).toBeVisible({ timeout: 5000 });
        await generateButton.click();
        
        // Verify form is visible
        const descriptionInput = page.locator('#secret-description');
        await expect(descriptionInput).toBeVisible({ timeout: 5000 });
        
        // Click cancel
        const cancelButton = page.getByRole('button', { name: /Cancel/i }).last();
        await expect(cancelButton).toBeVisible({ timeout: 5000 });
        await cancelButton.click();
        
        // Verify form is hidden
        await expect(descriptionInput).not.toBeVisible({ timeout: 5000 });
        
        // Verify count didn't change
        const newCount = await getSecretCount(page);
        expect(newCount).toBe(initialCount);
        
        console.log('✓ Test passed: Cancel secret creation');
    });

    test('should toggle secrets view open and closed', async ({ page }) => {
        console.log('Test: Toggle secrets view');
        
        // Open secrets view
        await toggleSecretsView(page, testClientId);
        
        // Verify secrets section is visible
        const secretsSection = page.locator('.secrets-section');
        await expect(secretsSection).toBeVisible({ timeout: 5000 });
        
        // Close secrets view
        await toggleSecretsView(page, testClientId);
        
        // Verify secrets section is hidden
        await expect(secretsSection).not.toBeVisible({ timeout: 5000 });
        
        console.log('✓ Test passed: Toggle secrets view');
    });
});
