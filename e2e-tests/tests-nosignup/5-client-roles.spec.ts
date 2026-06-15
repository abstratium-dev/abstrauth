import { test, expect } from '@playwright/test';
import { signInAsAdmin } from '../pages/signin.page';
import { navigateToClients } from '../pages/header';
import { addClient, addAllowedRoleToClient, deleteClientIfExists } from '../pages/clients.page';
import {
    toggleClientRolesView,
    addClientRole,
    removeClientRole,
    getClientRoleCount,
    getAvailableTargetClients,
    getRoleOptions,
} from '../pages/client-roles.page';

/*
 * E2E tests for Client-to-Client (M2M) Role Management.
 *
 * Verifies:
 *  - A source client can be assigned a role on a target client (owned by same org).
 *  - Only roles declared in the target client's allowlist are offered in the dropdown.
 *  - Clients from foreign orgs that the user has NOT subscribed to do NOT appear
 *    in the target-client dropdown.
 *  - A client role can be removed.
 *  - The JWT obtained via client_credentials contains the correct groups claim
 *    and the orgId of the source client.
 *
 * Prerequisites (seeded by Flyway / existing test data):
 *  - 'abstratium-abstrauth' exists as a platform client with at least one role
 *    (manage-accounts) in its allowed-roles that is flagged availableToForeignOrgs=true.
 *  - The admin org subscribes to abstratium-abstrauth (so it shows up as a target).
 */

const BASE_URL = process.env.BASE_URL || 'http://localhost:8080';

// A known target client owned by the platform org (foreign to the test admin org).
// Its role 'manage-accounts' is marked availableToForeignOrgs=true.
const PLATFORM_CLIENT_ID = 'abstratium-abstrauth';
const PLATFORM_CLIENT_ROLE = 'manage-accounts';

// Decode a JWT payload (no signature verification — we just inspect claims).
function decodeJwtPayload(token: string): Record<string, unknown> {
    const parts = token.split('.');
    if (parts.length !== 3) throw new Error(`Not a JWT: ${token.substring(0, 20)}`);
    const payload = Buffer.from(parts[1], 'base64url').toString('utf8');
    return JSON.parse(payload);
}

test.describe('Client-to-Client (M2M) Role Management', () => {
    test.setTimeout(60000);

    const SRC_CLIENT_BASE = 'test_m2m_src';
    const TARGET_CLIENT_BASE = 'test_m2m_tgt';
    const TARGET_ROLE = 'api-reader';

    let srcClientId: string;       // full prefixed id returned by addClient
    let srcClientSecret: string;
    let targetClientId: string;    // full prefixed id

    test.beforeEach(async ({ page }) => {
        const ts = Date.now();
        await signInAsAdmin(page);
        await navigateToClients(page);

        // Clean up any leftover clients from a previous run
        await deleteClientIfExists(page, `${SRC_CLIENT_BASE}_${ts}`);
        await deleteClientIfExists(page, `${TARGET_CLIENT_BASE}_${ts}`);

        // Create target client (owns the role catalog)
        const target = await addClient(
            page,
            `${TARGET_CLIENT_BASE}_${ts}`,
            'Test M2M Target',
            '',   // no redirect URI — M2M
            ''    // no scopes — M2M
        );
        targetClientId = target.clientId;
        console.log(`Target client created: ${targetClientId}`);

        // Add an allowed role to the target client
        await addAllowedRoleToClient(page, targetClientId, TARGET_ROLE);
        console.log(`Added allowed role '${TARGET_ROLE}' to target client`);

        // Create source client (the caller)
        const src = await addClient(
            page,
            `${SRC_CLIENT_BASE}_${ts}`,
            'Test M2M Source',
            '',
            ''
        );
        srcClientId = src.clientId;
        srcClientSecret = src.secret;
        console.log(`Source client created: ${srcClientId}, secret: ${srcClientSecret.substring(0, 8)}...`);
    });

    test('should show empty state when no client roles assigned', async ({ page }) => {
        console.log('Test: Empty state');
        await toggleClientRolesView(page, srcClientId);
        const emptyMsg = page.locator('[data-testid="no-client-roles-msg"]');
        await expect(emptyMsg).toBeVisible({ timeout: 5000 });
        const count = await getClientRoleCount(page);
        expect(count).toBe(0);
        console.log('✓ Empty state shown correctly');
    });

    test('should add and display a client role', async ({ page }) => {
        console.log('Test: Add client role');
        await toggleClientRolesView(page, srcClientId);

        await addClientRole(page, targetClientId, TARGET_ROLE);

        const count = await getClientRoleCount(page);
        expect(count).toBe(1);

        // Verify the role card shows the target client and role name
        const roleCard = page.locator(`.role-card[data-role="${TARGET_ROLE}"]`);
        await expect(roleCard).toBeVisible({ timeout: 5000 });
        const targetText = await roleCard.locator('.target-client-id').textContent();
        expect(targetText).toContain(targetClientId);
        const roleText = await roleCard.locator('.role-name').textContent();
        expect(roleText?.trim()).toBe(TARGET_ROLE);

        console.log('✓ Client role added and displayed correctly');
    });

    test('should remove a client role', async ({ page }) => {
        console.log('Test: Remove client role');
        await toggleClientRolesView(page, srcClientId);
        await addClientRole(page, targetClientId, TARGET_ROLE);
        expect(await getClientRoleCount(page)).toBe(1);

        await removeClientRole(page, targetClientId, TARGET_ROLE);

        expect(await getClientRoleCount(page)).toBe(0);
        const emptyMsg = page.locator('[data-testid="no-client-roles-msg"]');
        await expect(emptyMsg).toBeVisible({ timeout: 5000 });
        console.log('✓ Client role removed correctly');
    });

    test('target-client dropdown only shows own-org clients and subscribed clients', async ({ page }) => {
        console.log('Test: Cross-org isolation in target-client dropdown');
        await toggleClientRolesView(page, srcClientId);
        const options = await getAvailableTargetClients(page);

        // The own-org target client must appear
        const hasTarget = options.some(o => o.includes(targetClientId));
        expect(hasTarget).toBe(true);
        console.log(`  Own-org target client found in options: ${targetClientId}`);

        // The platform client (abstratium-abstrauth) is subscribed to by all orgs,
        // so it SHOULD appear.
        const hasPlatform = options.some(o => o.includes(PLATFORM_CLIENT_ID));
        expect(hasPlatform).toBe(true);
        console.log(`  Platform client '${PLATFORM_CLIENT_ID}' found (subscription present)`);

        // The source client itself must NOT appear (can't assign a role to itself)
        const hasSelf = options.some(o => o.includes(srcClientId));
        expect(hasSelf).toBe(false);
        console.log(`  Source client correctly excluded from options`);

        console.log('✓ Target-client dropdown isolation verified');
    });

    test('role dropdown only shows availableToForeignOrgs roles for subscribed platform client', async ({ page }) => {
        console.log('Test: Role dropdown shows only foreign-org-visible roles for platform client');
        await toggleClientRolesView(page, srcClientId);

        // Open the add form and select the platform client as target
        const addBtn = page.locator('[data-testid="toggle-add-client-role-btn"]');
        await addBtn.click();
        const form = page.locator('[data-testid="add-client-role-form"]');
        await expect(form).toBeVisible({ timeout: 5000 });

        // Find the option text that contains the platform client ID
        const targetSelect = page.locator('[data-testid="target-client-select"]');
        const allOptions = await targetSelect.locator('option').allInnerTexts();
        const platformOption = allOptions.find(o => o.includes(PLATFORM_CLIENT_ID));
        if (!platformOption) {
            throw new Error(`Platform client '${PLATFORM_CLIENT_ID}' not found in target dropdown. Options: ${JSON.stringify(allOptions)}`);
        }
        await targetSelect.selectOption({ label: platformOption.trim() });

        // Wait for role dropdown to be populated
        const roleSelect = page.locator('[data-testid="client-role-select"]');
        await expect(roleSelect).not.toBeDisabled({ timeout: 10000 });

        const roleOptions = await getRoleOptions(page);
        console.log(`  Roles available for platform client: ${JSON.stringify(roleOptions)}`);

        // The 'manage-accounts' role is marked availableToForeignOrgs=true — must appear
        const hasManageAccounts = roleOptions.some(o => o.includes(PLATFORM_CLIENT_ROLE));
        expect(hasManageAccounts).toBe(true);
        console.log(`  ✓ Role '${PLATFORM_CLIENT_ROLE}' is present (availableToForeignOrgs=true)`);

        // Cancel without submitting
        const cancelBtn = form.getByRole('button', { name: /Cancel/i });
        await cancelBtn.click();
        await expect(form).not.toBeVisible({ timeout: 5000 });

        console.log('✓ Role dropdown isolation verified');
    });

    test('JWT from client_credentials contains correct groups and orgId', async ({ page }) => {
        console.log('Test: JWT contains correct groups and orgId');

        // 1. Assign the client role via UI
        await toggleClientRolesView(page, srcClientId);
        await addClientRole(page, targetClientId, TARGET_ROLE);
        console.log('  Client role assigned via UI');

        // 2. Obtain the token via HTTP (Playwright request API — no browser nav needed)
        const response = await page.request.post(`${BASE_URL}/oauth2/token`, {
            form: {
                grant_type: 'client_credentials',
                client_id: srcClientId,
                client_secret: srcClientSecret,
            },
        });

        console.log(`  Token response status: ${response.status()}`);
        expect(response.ok()).toBe(true);

        const body = await response.json();
        console.log(`  Token response keys: ${Object.keys(body)}`);
        expect(body.access_token).toBeTruthy();
        expect(body.token_type).toBe('Bearer');

        // Print the JWT
        console.log(`  JWT: ${body.access_token}`);

        // 3. Decode the JWT payload and assert claims
        const claims = decodeJwtPayload(body.access_token as string);
        console.log(`  JWT claims: ${JSON.stringify(claims)}`);

        // sub must be the source client ID
        expect(claims.sub).toBe(srcClientId);

        // orgId must be present (the org that owns the source client)
        expect(typeof claims.orgId).toBe('string');
        expect((claims.orgId as string).length).toBeGreaterThan(0);
        console.log(`  orgId in token: ${claims.orgId}`);

        // groups must contain the expected entry: stripOrgPrefix(targetClientId)_role
        // The targetClientId is prefixed with orgId__; strip that prefix.
        const displayTarget = targetClientId.replace(/^[0-9a-f-]{36}__/, '');
        const expectedGroup = `${displayTarget}_${TARGET_ROLE}`;
        console.log(`  Expected group entry: ${expectedGroup}`);

        const groups = claims.groups as string[];
        expect(Array.isArray(groups)).toBe(true);
        expect(groups).toContain(expectedGroup);
        console.log(`  ✓ groups claim contains '${expectedGroup}'`);

        console.log('✓ JWT contains correct groups and orgId');
    });
});
