import { test, expect } from '@playwright/test';

import { signInAsAdmin } from '../pages/signin.page';
import { navigateToClients } from '../pages/header';
import { addClient } from '../pages/clients.page';
import { 
    toggleRolesView, 
    addRole, 
    removeRole, 
    verifyRoleExists,
    getRoleCount,
    verifyRolesDisabledForScopedClient,
    cancelAddRole
} from '../pages/client-roles.page';

test.describe('Client Roles Management', () => {
    const M2M_CLIENT_NAME = 'Test M2M Client';
    const M2M_REDIRECT_URI = ''; // No redirect URI for M2M clients
    const M2M_SCOPES = ''; // Empty scopes for M2M client (role-based auth)

    const SCOPED_CLIENT_NAME = 'Test Scoped Client';
    const SCOPED_REDIRECT_URI = 'http://localhost:3000/callback';
    const SCOPED_SCOPES = 'openid profile email';
    
    let m2mClientId: string;
    let scopedClientId: string;

    test.beforeEach(async ({ page }) => {
        // Generate unique client IDs for each test
        m2mClientId = `test-m2m-${Date.now()}`;
        scopedClientId = `test-scoped-${Date.now()}`;
        
        // Sign in as admin
        await signInAsAdmin(page);
        
        // Navigate to clients page
        await navigateToClients(page);
    });

    test('should add a role to an M2M client', async ({ page }) => {
        console.log('Test: Add role to M2M client');
        
        // Create an M2M client (no scopes)
        await addClient(page, m2mClientId, M2M_CLIENT_NAME, M2M_REDIRECT_URI, M2M_SCOPES);
        
        // Open roles view
        await toggleRolesView(page, m2mClientId);
        
        // Verify initial state - should have 0 roles
        const initialCount = await getRoleCount(page);
        expect(initialCount).toBe(0);
        
        // Add a role
        const roleName = 'api-reader';
        await addRole(page, roleName);
        
        // Verify role was added
        await verifyRoleExists(page, roleName, m2mClientId);
        
        // Verify count increased
        const newCount = await getRoleCount(page);
        expect(newCount).toBe(1);
        
        console.log('✓ Test passed: Add role to M2M client');
    });

    test('should add multiple roles to an M2M client', async ({ page }) => {
        console.log('Test: Add multiple roles to M2M client');
        
        // Create an M2M client
        await addClient(page, m2mClientId, M2M_CLIENT_NAME, M2M_REDIRECT_URI, M2M_SCOPES);
        
        // Open roles view
        await toggleRolesView(page, m2mClientId);
        
        // Add first role
        await addRole(page, 'api-reader');
        
        // Add second role
        await addRole(page, 'api-writer');
        
        // Add third role
        await addRole(page, 'data-processor');
        
        // Verify all roles exist
        await verifyRoleExists(page, 'api-reader', m2mClientId);
        await verifyRoleExists(page, 'api-writer', m2mClientId);
        await verifyRoleExists(page, 'data-processor', m2mClientId);
        
        // Verify count
        const count = await getRoleCount(page);
        expect(count).toBe(3);
        
        console.log('✓ Test passed: Add multiple roles to M2M client');
    });

    test('should remove a role from an M2M client', async ({ page }) => {
        console.log('Test: Remove role from M2M client');
        
        // Create an M2M client
        await addClient(page, m2mClientId, M2M_CLIENT_NAME, M2M_REDIRECT_URI, M2M_SCOPES);
        
        // Open roles view
        await toggleRolesView(page, m2mClientId);
        
        // Add roles
        await addRole(page, 'api-reader');
        await addRole(page, 'api-writer');
        
        // Get initial count
        const initialCount = await getRoleCount(page);
        expect(initialCount).toBe(2);
        
        // Remove one role
        await removeRole(page, 'api-reader');
        
        // Verify count decreased
        const newCount = await getRoleCount(page);
        expect(newCount).toBe(1);
        
        // Verify remaining role still exists
        await verifyRoleExists(page, 'api-writer', m2mClientId);
        
        console.log('✓ Test passed: Remove role from M2M client');
    });

    test('should verify role group name format', async ({ page }) => {
        console.log('Test: Verify role group name format');
        
        // Create an M2M client
        await addClient(page, m2mClientId, M2M_CLIENT_NAME, M2M_REDIRECT_URI, M2M_SCOPES);
        
        // Open roles view
        await toggleRolesView(page, m2mClientId);
        
        // Add a role
        const roleName = 'custom-role';
        await addRole(page, roleName);
        
        // Verify the group name format is correct (clientId_role)
        const expectedGroupName = `${m2mClientId}_${roleName}`;
        const roleCard = page.locator('.role-card').filter({ hasText: roleName });
        const groupNameElement = roleCard.locator('.role-group-name');
        
        const groupNameText = await groupNameElement.textContent();
        expect(groupNameText).toContain(expectedGroupName);
        
        console.log(`✓ Test passed: Verified group name format: ${expectedGroupName}`);
    });

    test('should prevent role management for clients with scopes', async ({ page }) => {
        console.log('Test: Prevent role management for scoped clients');
        
        // Create a client with scopes
        await addClient(page, scopedClientId, SCOPED_CLIENT_NAME, SCOPED_REDIRECT_URI, SCOPED_SCOPES);
        
        // Verify that the "Manage Roles" button is disabled with a warning
        await verifyRolesDisabledForScopedClient(page, scopedClientId);
        
        console.log('✓ Test passed: Role management prevented for scoped clients');
    });

    test('should handle role addition cancellation', async ({ page }) => {
        console.log('Test: Cancel role addition');
        
        // Create an M2M client
        await addClient(page, m2mClientId, M2M_CLIENT_NAME, M2M_REDIRECT_URI, M2M_SCOPES);
        
        // Open roles view
        await toggleRolesView(page, m2mClientId);
        
        // Get initial count
        const initialCount = await getRoleCount(page);
        
        // Click "Add Role" button
        const addRoleButton = page.getByRole('button', { name: /^\+ Add Role$/i });
        await expect(addRoleButton).toBeVisible({ timeout: 5000 });
        await addRoleButton.click();
        
        // Verify form is visible
        const roleNameInput = page.locator('#role-name');
        await expect(roleNameInput).toBeVisible({ timeout: 5000 });
        
        // Fill in a role name
        await roleNameInput.fill('test-role');
        
        // Cancel the form
        await cancelAddRole(page);
        
        // Verify count didn't change
        const newCount = await getRoleCount(page);
        expect(newCount).toBe(initialCount);
        
        console.log('✓ Test passed: Cancel role addition');
    });

    test('should toggle roles view open and closed', async ({ page }) => {
        console.log('Test: Toggle roles view');
        
        // Create an M2M client
        await addClient(page, m2mClientId, M2M_CLIENT_NAME, M2M_REDIRECT_URI, M2M_SCOPES);
        
        // Open roles view
        await toggleRolesView(page, m2mClientId);
        
        // Verify roles section is visible
        const rolesSection = page.locator('.roles-section');
        await expect(rolesSection).toBeVisible({ timeout: 5000 });
        
        // Close roles view
        await toggleRolesView(page, m2mClientId);
        
        // Verify roles section is hidden
        await expect(rolesSection).not.toBeVisible({ timeout: 5000 });
        
        console.log('✓ Test passed: Toggle roles view');
    });

    test('should validate role name format', async ({ page }) => {
        console.log('Test: Validate role name format');
        
        // Create an M2M client
        await addClient(page, m2mClientId, M2M_CLIENT_NAME, M2M_REDIRECT_URI, M2M_SCOPES);
        
        // Open roles view
        await toggleRolesView(page, m2mClientId);
        
        // Click "Add Role" button
        const addRoleButton = page.getByRole('button', { name: /^\+ Add Role$/i });
        await expect(addRoleButton).toBeVisible({ timeout: 5000 });
        await addRoleButton.click();
        
        // Verify the input has pattern validation
        const roleNameInput = page.locator('#role-name');
        const pattern = await roleNameInput.getAttribute('pattern');
        expect(pattern).toBe('^[a-z0-9-]+$');
        
        // Verify the hint text
        const hint = page.locator('.form-hint').filter({ 
            hasText: /Lowercase letters, numbers, and hyphens only/i 
        });
        await expect(hint).toBeVisible({ timeout: 5000 });
        
        console.log('✓ Test passed: Role name format validation');
    });

    test('should display info about service roles', async ({ page }) => {
        console.log('Test: Display service roles info');
        
        // Create an M2M client
        await addClient(page, m2mClientId, M2M_CLIENT_NAME, M2M_REDIRECT_URI, M2M_SCOPES);
        
        // Open roles view
        await toggleRolesView(page, m2mClientId);
        
        // Verify the info box is displayed
        const infoBox = page.locator('.info-box').filter({ 
            hasText: /About Service Roles/i 
        });
        await expect(infoBox).toBeVisible({ timeout: 5000 });
        
        // Verify it mentions the groups claim
        const groupsClaimText = await infoBox.textContent();
        expect(groupsClaimText).toContain('groups');
        expect(groupsClaimText).toContain('@RolesAllowed');
        
        console.log('✓ Test passed: Service roles info displayed');
    });

    test('should show empty state when no roles assigned', async ({ page }) => {
        console.log('Test: Empty state for no roles');
        
        // Create an M2M client
        await addClient(page, m2mClientId, M2M_CLIENT_NAME, M2M_REDIRECT_URI, M2M_SCOPES);
        
        // Open roles view
        await toggleRolesView(page, m2mClientId);
        
        // Verify empty state message
        const emptyMessage = page.locator('.info-box').filter({ 
            hasText: /No roles assigned/i 
        });
        await expect(emptyMessage).toBeVisible({ timeout: 5000 });
        
        // Verify count is 0
        const count = await getRoleCount(page);
        expect(count).toBe(0);
        
        console.log('✓ Test passed: Empty state displayed');
    });

    test('should add and remove the same role multiple times', async ({ page }) => {
        console.log('Test: Add and remove same role multiple times');
        
        // Create an M2M client
        await addClient(page, m2mClientId, M2M_CLIENT_NAME, M2M_REDIRECT_URI, M2M_SCOPES);
        
        // Open roles view
        await toggleRolesView(page, m2mClientId);
        
        const roleName = 'test-role';
        
        // Add role
        await addRole(page, roleName);
        await verifyRoleExists(page, roleName, m2mClientId);
        
        // Remove role
        await removeRole(page, roleName);
        let count = await getRoleCount(page);
        expect(count).toBe(0);
        
        // Add role again
        await addRole(page, roleName);
        await verifyRoleExists(page, roleName, m2mClientId);
        
        // Remove role again
        await removeRole(page, roleName);
        count = await getRoleCount(page);
        expect(count).toBe(0);
        
        console.log('✓ Test passed: Add and remove same role multiple times');
    });
});
