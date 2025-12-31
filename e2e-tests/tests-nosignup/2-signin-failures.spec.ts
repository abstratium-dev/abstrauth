import { test, expect } from '@playwright/test';
import { ensureAdminIsAuthenticated, ADMIN_EMAIL, ADMIN_PASSWORD, ADMIN_NAME, denyAuthorization, navigateWithRetry } from '../pages/signin.page';
import { signout } from '../pages/header';

test('user denies access', async ({ page }) => {
  // Step 1: Ensure admin exists (creates if needed)
  console.log("Step 1: Ensuring admin exists...");
  await ensureAdminIsAuthenticated(page);
  
  // Step 2: Sign out
  console.log("Step 2: Signing out...");
  await signout(page);
  
  // Step 3: Sign in as admin again
  console.log("Step 3: Signing in as admin to test deny flow...");
  await navigateWithRetry(page, '/');
  
  // Wait for page to load
  await page.locator("#username").waitFor({ state: 'visible', timeout: 10000 });
  
  // Fill credentials
  await page.locator("#username").fill(ADMIN_EMAIL);
  await page.locator("#password").fill(ADMIN_PASSWORD);
  await page.locator("#signin-button").click();
  
  // Step 4: Deny authorization
  console.log("Step 4: Denying authorization...");
  await denyAuthorization(page);
  
  // Step 5: Wait for navigation to error page and verify
  console.log("Step 5: Waiting for error page...");
  await page.waitForURL('**/api/auth/error**', { timeout: 10000 });
  
  // Wait for error page elements to be visible
  await page.locator("#error-message").waitFor({ state: 'visible', timeout: 10000 });
  
  console.log("Step 6: Verifying error...");
  await expect(page.locator("#error-message")).toContainText("User denied authorization");
  await expect(page.locator("#error-code")).toContainText("access_denied");
  await expect(page.url()).toContain("/api/auth/error?error=access_denied");
  
  console.log("✓ Test completed successfully");
});

test('wrong username or password', async ({ page }) => {
  await navigateWithRetry(page, '/');

  // Wait for the page to load and inputs to be visible
  await page.locator("#username").waitFor({ state: 'visible', timeout: 10000 });
  
  await page.locator("#username").fill("wrong@doesnt-exist.com");
  await page.locator("#password").fill("wrong");
  
  await page.locator("#signin-button").click();

  await expect(page.locator(".error-box")).toContainText("Invalid username or password");

});
