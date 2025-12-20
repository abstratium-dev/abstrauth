import { test, expect } from '@playwright/test';
import { ensureAdminIsAuthenticated, ADMIN_EMAIL, ADMIN_PASSWORD, ADMIN_NAME, denyAuthorization } from '../pages/signin.page';
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
  await page.goto('/');
  
  // Wait for page to load
  await page.locator("#username").waitFor({ state: 'visible', timeout: 10000 });
  
  // Fill credentials
  await page.locator("#username").fill(ADMIN_EMAIL);
  await page.locator("#password").fill(ADMIN_PASSWORD);
  await page.locator("#signin-button").click();
  
  // Step 4: Deny authorization
  console.log("Step 4: Denying authorization...");
  await denyAuthorization(page);
  
  // Step 5: Verify error message and URL
  console.log("Step 5: Verifying error...");
  await expect(page.locator(".error-box")).toContainText("Error: access_denied - User denied authorization");
  await expect(page.url()).toContain("/auth-callback?error=access_denied&error_description=User%20denied%20authorization");
  
  console.log("✓ Test completed successfully");
});

test('wrong username or password', async ({ page }) => {
  await page.goto('/');

  // Wait for the page to load and inputs to be visible
  await page.locator("#username").waitFor({ state: 'visible', timeout: 10000 });
  
  await page.locator("#username").fill("wrong@doesnt-exist.com");
  await page.locator("#password").fill("wrong");
  
  await page.locator("#signin-button").click();

  await expect(page.locator(".error-box")).toContainText("Invalid username or password");

});
