import { test, expect } from '@playwright/test';
import { navigateWithRetry } from '../pages/signin.page';

test.afterEach(async ({ page }, testInfo) => {
  if (testInfo.status !== testInfo.expectedStatus) {
    const screenshot = await page.screenshot();
    await testInfo.attach('screenshot', { body: screenshot, contentType: 'image/png' });
  }
});

test('sign up and email already exists', async ({ page }) => {
  await navigateWithRetry(page, '/');

  await page.locator("#signup-link").click();

  // generate a random username and password
  const email = Math.random().toString(36).substring(2, 15) + "@abstratium.dev";
  const name = Math.random().toString(36).substring(2, 15);
  const password = Math.random().toString(36).substring(2, 15);

  await page.locator("#email").fill(email);
  await page.locator("#name").fill(name);
  await page.locator("#password").fill(password);
  await page.locator("#password2").fill(password);
  
  await page.locator("#create-account-button").click();

  // Wait for either navigation to signin page OR error message
  await Promise.race([
    page.waitForURL('**/signin/**', { timeout: 10000 }),
    page.locator("#message").waitFor({ state: 'visible', timeout: 10000 })
  ]);
  
  // If we got an error message instead of navigation, fail the test
  const errorMessage = page.locator("#message");
  if (await errorMessage.isVisible()) {
    const errorText = await errorMessage.textContent();
    throw new Error(`Signup failed unexpectedly: ${errorText}`);
  }
  
  // now go back to the home page and sign up again
  await navigateWithRetry(page, '/');
  await expect(page.locator("#signup-link")).toBeVisible({ timeout: 5000 });

  await page.locator("#signup-link").click();


  await page.locator("#email").fill(email);
  await page.locator("#name").fill(name);
  await page.locator("#password").fill(password);
  await page.locator("#password2").fill(password);

  await page.locator("#create-account-button").click();

  // Wait for error message to appear
  await expect(page.locator("#message")).toBeVisible({ timeout: 10000 });
  await expect(page.locator("#message")).toContainText("Email already exists");
});

test('sign up with mismatched passwords', async ({ page }) => {
  await navigateWithRetry(page, '/');

  await page.locator("#signup-link").click();

  // generate random credentials
  const email = Math.random().toString(36).substring(2, 15) + "@abstratium.dev";
  const name = Math.random().toString(36).substring(2, 15);
  const password = Math.random().toString(36).substring(2, 15);
  const password2 = Math.random().toString(36).substring(2, 15); // Different password

  await page.locator("#email").fill(email);
  await page.locator("#name").fill(name);
  await page.locator("#password").fill(password);
  await page.locator("#password2").fill(password2);

  await page.locator("#create-account-button").click();

  // Wait for error message to appear
  await expect(page.locator("#message")).toBeVisible({ timeout: 10000 });
  await expect(page.locator("#message")).toContainText("Passwords do not match");
});

