import { test, expect } from '@playwright/test';

test.afterEach(async ({ page }, testInfo) => {
  if (testInfo.status !== testInfo.expectedStatus) {
    const screenshot = await page.screenshot();
    await testInfo.attach('screenshot', { body: screenshot, contentType: 'image/png' });
  }
});

test('sign up and email already exists', async ({ page }) => {
  await page.goto('/');

  await page.locator("#signup-link").click();

  // generate a random username and password
  const email = Math.random().toString(36).substring(2, 15) + "@abstratium.dev";
  const name = Math.random().toString(36).substring(2, 15);
  const username = "test_" + Math.random().toString(36).substring(2, 15) + "_test";
  const password = Math.random().toString(36).substring(2, 15);

  await page.locator("#email").fill(email);
  await page.locator("#name").fill(name);
  await page.locator("#username").fill(username);
  await page.locator("#password").fill(password);
  
  await page.locator("#create-account-button").click();

  // Wait for navigation to signin page after successful signup
  await page.waitForURL('**/signin/**', { timeout: 10000 });
  
  // now go back to the home page and sign up again
  await page.goto('/', { waitUntil: 'networkidle' });

  await page.locator("#signup-link").click();


  await page.locator("#email").fill(email);
  await page.locator("#name").fill(name);
  await page.locator("#username").fill(username);
  await page.locator("#password").fill(password);

  await page.locator("#create-account-button").click();

  await expect(page.locator("#message")).toContainText("Email already exists");
});

test('sign up and username already exists', async ({ page }) => {
  await page.goto('/');

  await page.locator("#signup-link").click();

  // generate a random username and password
  const email = Math.random().toString(36).substring(2, 15) + "@abstratium.dev";
  const name = Math.random().toString(36).substring(2, 15);
  const username = "test_" + Math.random().toString(36).substring(2, 15) + "_test";
  const password = Math.random().toString(36).substring(2, 15);

  await page.locator("#email").fill(email);
  await page.locator("#name").fill(name);
  await page.locator("#username").fill(username);
  await page.locator("#password").fill(password);
  
  await page.locator("#create-account-button").click();

  // Wait for navigation to signin page after successful signup
  await page.waitForURL('**/signin/**', { timeout: 10000 });
  
  // now go back to the home page
  await page.goto('/', { waitUntil: 'networkidle' });

  await page.locator("#signup-link").click();


  await page.locator("#email").fill(email + "2"); // make it unique again
  await page.locator("#name").fill(name);
  await page.locator("#username").fill(username);
  await page.locator("#password").fill(password);

  await page.locator("#create-account-button").click();

  await expect(page.locator("#message")).toContainText("Username already exists");
});
