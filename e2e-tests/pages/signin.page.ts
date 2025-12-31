import { expect, Page } from '@playwright/test';

// Test user credentials
export const ADMIN_EMAIL = 'admin@abstratium.dev';
export const ADMIN_PASSWORD = 'secretLong';
export const ADMIN_NAME = 'Admin';

export const MANAGER_EMAIL = 'manager@abstratium.dev';
export const MANAGER_PASSWORD = 'secretLong2';
export const MANAGER_NAME = 'Manager';

function _getUsernameInput(page: Page) {
    return page.locator("#username");
}

function _getPasswordInput(page: Page) {
    return page.locator("#password");
}

function _getSigninButton(page: Page) {
    return page.locator("#signin-button");
}

function _getApproveButton(page: Page) {
    return page.locator("#approve-button");
}

function _getSignupLink(page: Page) {
    return page.locator("#signup-link");
}

function _getEmailInput(page: Page) {
    return page.locator("#email");
}

function _getNameInput(page: Page) {
    return page.locator("#name");
}

function _getPassword2Input(page: Page) {
    return page.locator("#password2");
}

function _getCreateAccountButton(page: Page) {
    return page.locator("#create-account-button");
}

function _getUserLink(page: Page) {
    return page.locator("#user-link");
}

/**
 * Navigate to a URL with retry logic to handle flaky connections.
 * Retries up to 3 times with 1-second delays between attempts.
 */
export async function navigateWithRetry(page: Page, url: string) {
    let retries = 3;
    while (retries > 0) {
        try {
            await page.goto(url, { waitUntil: 'commit', timeout: 10000 });
            // Wait for load state to ensure page is ready (important for WebKit)
            await page.waitForLoadState('domcontentloaded', { timeout: 10000 });
            break;
        } catch (error) {
            retries--;
            if (retries === 0) {
                console.error(`Failed to navigate to ${url} after 3 attempts`);
                throw error;
            }
            console.log(`Navigation to ${url} failed, retrying... (${retries} attempts left)`);
            await page.waitForTimeout(1000);
        }
    }
}

function _getInvalidCredentialsError(page: Page) {
    return page.getByText('Invalid username or password');
}

function _getDenyButton(page: Page) {
    return page.locator("#deny-button");
}

/**
 * Reusable function to ensure a user is authenticated.
 * Attempts to sign in with the provided credentials.
 * If sign in fails, it signs up with the provided credentials and name.
 * 
 * @param page - Playwright Page object
 * @param email - User email address
 * @param password - User password
 * @param name - User display name (used for signup if needed)
 */
async function ensureAuthenticated(page: Page, email: string, password: string, name: string) {
    await navigateWithRetry(page, '/');

    // Wait for the signin page to be ready
    const usernameInput = _getUsernameInput(page);
    await usernameInput.waitFor({ state: 'visible', timeout: 10000 });
    
    // Try to sign in first
    await usernameInput.fill(email);
    await _getPasswordInput(page).fill(password);
    await _getSigninButton(page).click();

    // Wait for either the error message or the approve button to appear
    // This handles the race condition where the error might appear slowly
    const errorBox = _getInvalidCredentialsError(page);
    const approveButton = _getApproveButton(page);
    
    try {
        // Race between error appearing or approve button appearing
        await Promise.race([
            errorBox.waitFor({ state: 'visible', timeout: 5000 }),
            approveButton.waitFor({ state: 'visible', timeout: 5000 })
        ]);
    } catch (e) {
        // Neither appeared in time - this is unexpected
        console.log("Neither error nor approve button appeared");
        throw e;
    }

    // Check which one is actually visible
    const isErrorVisible = await errorBox.isVisible();
    console.log("isErrorVisible: " + isErrorVisible);
    
    if (isErrorVisible) {
        // Sign in failed, need to sign up
        await navigateWithRetry(page, '/');
        
        // WebKit enforces form validation on link clicks inside forms
        // Clear the form fields first to prevent validation errors
        await _getUsernameInput(page).clear();
        await _getPasswordInput(page).clear();
        
        // Click the signup link
        await _getSignupLink(page).click();
        
        // Wait for the signup form to appear
        await _getEmailInput(page).waitFor({ state: 'visible', timeout: 10000 });

        // Fill signup form
        await _getEmailInput(page).fill(email);
        await _getNameInput(page).fill(name);
        await _getPasswordInput(page).fill(password);
        await _getPassword2Input(page).fill(password);
        await _getCreateAccountButton(page).click();

        // Now sign in with the new account
        await _getSigninButton(page).click();
        
        // Wait for approve button after successful signup and signin
        await approveButton.waitFor({ state: 'visible', timeout: 5000 });
    }

    // At this point we should be at the authorization page
    await approveButton.click();

    // Wait for navigation and page load (important for WebKit)
    await page.waitForLoadState('domcontentloaded', { timeout: 10000 });

    // Verify we're authenticated
    await expect(_getUserLink(page)).toBeVisible({ timeout: 10000 });
    await expect(_getUserLink(page)).toContainText(name);
}

export async function ensureAdminIsAuthenticated(page: Page) {
    await ensureAuthenticated(page, ADMIN_EMAIL, ADMIN_PASSWORD, ADMIN_NAME);
}

/**
 * Signs in as admin with known credentials.
 * Assumes admin account already exists.
 */
export async function signInAsAdmin(page: Page) {
    
    console.log("Signing in as admin...");
    
    await _waitForSigninPage(page);

    await _clearLocalStorage(page);

    // Wait for the username input to be visible and editable
    // This implicitly waits for Angular to initialize
    const usernameInput = _getUsernameInput(page);
    await expect(usernameInput).toBeVisible({ timeout: 10000 });
    await expect(usernameInput).toBeEditable({ timeout: 10000 });
    console.log("Username input is visible and editable");
    
    // Fill in credentials
    await usernameInput.fill(ADMIN_EMAIL);
    await _getPasswordInput(page).fill(ADMIN_PASSWORD);
    console.log("Filled in credentials");
    
    // Wait for sign-in button to be enabled
    const signinButton = _getSigninButton(page);
    await expect(signinButton).toBeEnabled({ timeout: 5000 });
    await signinButton.click();
    console.log("Clicked sign-in button");
    
    // After clicking signin, wait for either:
    // 1. URL to change to authorize page
    // 2. Approve button to appear (might stay on /signin URL but show authorize content)
    // 3. Error message to appear
    
    const approveButton = _getApproveButton(page);
    const signinError = page.locator('.error-box, .error-message, #message');
    
    try {
        await Promise.race([
            page.waitForURL(url => url.toString().includes('/authorize'), { timeout: 15000 }),
            approveButton.waitFor({ state: 'visible', timeout: 15000 }),
            signinError.waitFor({ state: 'visible', timeout: 15000 })
        ]);
        
        // Check if error appeared
        if (await signinError.isVisible()) {
            const errorText = await signinError.textContent();
            throw new Error(`Sign-in failed: ${errorText}`);
        }
        
        // Success - either URL changed or approve button appeared
        console.log("Sign-in successful, approve button should be visible");
        
    } catch (error) {
        // Timeout - nothing happened
        console.log("Signin timeout. Current URL:", page.url());
        await page.screenshot({ path: 'signin-timeout.png', fullPage: true }).catch(() => {});
        
        const pageText = await page.locator('body').textContent().catch(() => '');
        console.log("Page text:", pageText?.substring(0, 500));
        
        throw new Error(`Sign-in timeout. URL: ${page.url()}`);
    }
    
    await approveButton.click();
    
    // Wait for navigation after approval
    await page.waitForURL('**', { timeout: 5000 }).catch(() => {
        // URL might not change if already on callback page
    });
    
    // Wait for page to be fully loaded (important for WebKit)
    await page.waitForLoadState('domcontentloaded', { timeout: 10000 });
    
    // Verify signed in
    await expect(_getUserLink(page)).toBeVisible({ timeout: 10000 });
    await expect(_getUserLink(page)).toContainText(ADMIN_NAME);
    
    console.log("Signed in as admin successfully");
}

async function _waitForSigninPage(page: Page) {
    // Check if we're already on a signin page (e.g., after logout)
    const currentUrl = page.url();
    if (!currentUrl.includes('/signin/')) {
        // Navigate to home page which will redirect to signin
        await navigateWithRetry(page, '/');
    }
    
    // Wait for the signin page to be ready by checking for the username input
    // This ensures Angular has initialized and the page is interactive
    try {
        await page.locator('#username').waitFor({ state: 'visible', timeout: 10000 });
        console.log("On signin page - ready");
    } catch (error) {
        console.error("Signin page not ready after navigation");
        throw error;
    }
}

async function _clearLocalStorage(page: Page) {
    // Clear both localStorage and sessionStorage - use a try-catch in case of navigation race
    try {
        await page.evaluate(() => {
            localStorage.clear();
            sessionStorage.clear();
        });
    } catch (e) {
        // Ignore if context was destroyed due to navigation
        console.log("Note: storage.clear() skipped due to navigation");
    }
}

export async function signInAsManager(page: Page) {
    
    console.log("Signing in as manager...");

    await _waitForSigninPage(page);

    await _clearLocalStorage(page);
    
    // Wait for the username input to be visible and editable
    // This implicitly waits for Angular to initialize
    const usernameInput = _getUsernameInput(page);
    await expect(usernameInput).toBeVisible({ timeout: 10000 });
    await expect(usernameInput).toBeEditable({ timeout: 10000 });
    
    // Fill in credentials
    await usernameInput.fill(MANAGER_EMAIL);
    await _getPasswordInput(page).fill(MANAGER_PASSWORD);
    
    // Wait for sign-in button to be enabled
    const signinButton = _getSigninButton(page);
    await expect(signinButton).toBeEnabled({ timeout: 5000 });
    await signinButton.click();
    
    // Wait for navigation or response
    await page.waitForLoadState('domcontentloaded');
    
    // Wait for either approve button or error message
    const approveButton = _getApproveButton(page);
    const errorBox = page.locator('.error-box');
    
    // Wait for either to appear (increased timeout for slower responses)
    await Promise.race([
        approveButton.waitFor({ state: 'visible', timeout: 15000 }),
        errorBox.waitFor({ state: 'visible', timeout: 15000 })
    ]);
    
    // Check if error appeared
    if (await errorBox.isVisible()) {
        const errorText = await errorBox.textContent();
        throw new Error(`Sign-in failed: ${errorText}`);
    }
    
    await approveButton.click();
    
    // Wait for navigation after approval
    await page.waitForURL('**', { timeout: 5000 }).catch(() => {
        // URL might not change if already on callback page
    });
    
    // Verify signed in
    await expect(_getUserLink(page)).toBeVisible({ timeout: 10000 });
    await expect(_getUserLink(page)).toContainText(MANAGER_NAME);
    
    console.log("Signed in as manager successfully");
}

export async function ensureManagerIsAuthenticated(page: Page) {
    await ensureAuthenticated(page, MANAGER_EMAIL, MANAGER_PASSWORD, MANAGER_NAME);
}

/**
 * Attempts to sign in as admin. Returns true if successful, false if failed.
 * Does NOT sign up if sign in fails.
 */
export async function trySignInAsAdmin(page: Page): Promise<boolean> {
    
    await navigateWithRetry(page, '/');
    await _getUsernameInput(page).fill(ADMIN_EMAIL);
    await _getPasswordInput(page).fill(ADMIN_PASSWORD);
    await _getSigninButton(page).click();

    const errorBox = _getInvalidCredentialsError(page);
    const approveButton = _getApproveButton(page);
    
    try {
        await Promise.race([
            errorBox.waitFor({ state: 'visible', timeout: 5000 }),
            approveButton.waitFor({ state: 'visible', timeout: 5000 })
        ]);
    } catch (e) {
        console.log("Neither error nor approve button appeared during admin sign in attempt");
        return false;
    }

    const isErrorVisible = await errorBox.isVisible();
    
    if (isErrorVisible) {
        console.log("Admin sign in failed - account doesn't exist");
        return false;
    }
    
    // Sign in succeeded, click approve
    await approveButton.click();
    await expect(_getUserLink(page)).toBeVisible();
    console.log("Admin sign in succeeded");
    return true;
}

/**
 * Signs in using an invite link.
 * The invite link contains pre-filled credentials.
 */
export async function signInViaInviteLink(page: Page, inviteLink: string, expectedEmail: string) {
    console.log("Signing in via invite link...");
    
    // Navigate to invite link with retry logic
    // The page will immediately redirect to /authorize, so we use waitUntil: 'commit'
    // to avoid waiting for the full page load that will be aborted by the redirect
    let retries = 3;
    while (retries > 0) {
        try {
            await page.goto(inviteLink, { waitUntil: 'commit', timeout: 10000 });
            break;
        } catch (error) {
            retries--;
            if (retries === 0) {
                console.error(`Failed to navigate to invite link after 3 attempts`);
                throw error;
            }
            console.log(`Navigation to invite link failed, retrying... (${retries} attempts left)`);
            await page.waitForTimeout(1000);
        }
    }
    
    // The invite link will redirect to /authorize and auto-fill credentials
    // Wait for the signin page to load with pre-filled credentials
    await expect(_getSigninButton(page)).toBeVisible({ timeout: 5000 });
    
    // Verify the email is pre-filled from invite
    const usernameValue = await _getUsernameInput(page).inputValue();
    expect(usernameValue).toBe(expectedEmail);
    console.log("Email pre-filled from invite: " + usernameValue);
    
    // Just click sign in - password is already filled from invite token
    await _getSigninButton(page).click();
    
    console.log("Sign in button clicked");
}

/**
 * Signs up a new user and signs them in.
 * Returns the user credentials.
 */
export async function signUpAndSignIn(page: Page, email: string, name: string, password: string) {
    console.log(`Signing up new user: ${email}`);
    
    await navigateWithRetry(page, '/');
    await _getSignupLink(page).click();
    
    await _getEmailInput(page).fill(email);
    await _getNameInput(page).fill(name);
    await _getPasswordInput(page).fill(password);
    await _getPassword2Input(page).fill(password);
    
    await _getCreateAccountButton(page).click();
    
    // Sign in
    await _getSigninButton(page).click();
    
    // Approve authorization
    await _getApproveButton(page).waitFor({ state: 'visible', timeout: 5000 });
    await _getApproveButton(page).click();
    
    // Verify signed in
    await expect(_getUserLink(page)).toBeVisible({ timeout: 5000 });
    
    console.log(`✓ User ${email} signed up and signed in`);
}

/**
 * Denies authorization after signing in.
 * Assumes we're at the authorization approval page.
 */
export async function denyAuthorization(page: Page) {
    console.log("Denying authorization...");
    
    await expect(_getDenyButton(page)).toBeVisible({ timeout: 5000 });
    await _getDenyButton(page).click();
    
    console.log("Authorization denied");
}
