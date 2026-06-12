import { expect, Page } from '@playwright/test';

// Element accessors
function _getOrganisationsLink(page: Page) {
    return page.locator('#organisations-link');
}

function _getCreateOrgButton(page: Page) {
    return page.locator('#create-org-button');
}

function _getOrgNameInput(page: Page) {
    return page.locator('#org-name');
}

function _getSubmitCreateOrgButton(page: Page) {
    return page.locator('#submit-create-org-button');
}

function _getOrgTiles(page: Page) {
    return page.locator('.tile');
}

/**
 * Navigate to the Organisations page via header link.
 */
export async function navigateToOrganisations(page: Page) {
    console.log('Navigating to Organisations page...');
    await _getOrganisationsLink(page).click();
    console.log('✓ Navigated to Organisations page');
}

/**
 * Create a new organisation.
 * Assumes we're on the organisations page.
 */
export async function createOrganisation(page: Page, name: string): Promise<void> {
    console.log(`Creating organisation '${name}'...`);

    // Open the create form
    const createButton = _getCreateOrgButton(page);
    await expect(createButton).toBeVisible({ timeout: 5000 });
    await createButton.click();

    // Fill the form
    const nameInput = _getOrgNameInput(page);
    await expect(nameInput).toBeVisible({ timeout: 5000 });
    await nameInput.fill(name);

    // Submit
    const submitButton = _getSubmitCreateOrgButton(page);
    await expect(submitButton).toBeEnabled({ timeout: 5000 });
    await submitButton.click();

    // Wait for form to close (success)
    await expect(nameInput).not.toBeVisible({ timeout: 10000 });

    console.log(`✓ Created organisation '${name}'`);
}

/**
 * Get the names of all organisations displayed on the page.
 * Assumes we're on the organisations page.
 */
export async function getOrganisationNames(page: Page): Promise<string[]> {
    const tiles = _getOrgTiles(page);
    const count = await tiles.count();
    const names: string[] = [];
    for (let i = 0; i < count; i++) {
        const name = await tiles.nth(i).locator('.tile-title').textContent();
        if (name) names.push(name.trim());
    }
    console.log(`Found organisations: ${names.join(', ')}`);
    return names;
}

/**
 * Verify that the organisation with the given name is marked as current.
 * Assumes we're on the organisations page.
 */
export async function verifyCurrentOrganisation(page: Page, expectedName: string): Promise<void> {
    const currentTile = page.locator('.tile.highlighted-tile');
    await expect(currentTile).toBeVisible({ timeout: 5000 });
    const name = await currentTile.locator('.tile-title').textContent();
    expect(name?.trim()).toBe(expectedName);
    console.log(`✓ Current organisation is '${expectedName}'`);
}

/**
 * Select an organisation on the org-selection page.
 * Assumes we're on the /org-selection/ page.
 */
export async function selectOrganisation(page: Page, orgName: string): Promise<void> {
    console.log(`Selecting organisation '${orgName}' on org-selection page...`);

    // Find the label containing the org name and click it (checks the radio)
    const orgLabel = page.locator('.org-option').filter({ hasText: orgName });
    await expect(orgLabel).toBeVisible({ timeout: 10000 });
    await orgLabel.click();

    // Click Continue
    const continueButton = page.locator('#select-org-button');
    await expect(continueButton).toBeEnabled({ timeout: 5000 });
    await continueButton.click();

    console.log(`✓ Selected organisation '${orgName}'`);
}

/**
 * Get the names of all organisations displayed on the org-selection page.
 * Assumes we're on the /org-selection/ page.
 */
export async function getOrgSelectionNames(page: Page): Promise<string[]> {
    const options = page.locator('.org-option .org-name');
    const count = await options.count();
    const names: string[] = [];
    for (let i = 0; i < count; i++) {
        const name = await options.nth(i).textContent();
        if (name) names.push(name.trim());
    }
    console.log(`Org-selection options: ${names.join(', ')}`);
    return names;
}
