import { test, expect } from '@playwright/test';

test('sign up and in', async ({ page }) => {
  await page.goto('/');

  await expect(page).toHaveTitle(/Abstrauth/);

  // the page redirects to the login page

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

  await page.locator("#signin-button").click();

  await page.locator("#approve-button").click();

  await expect(page.locator("#user-link")).toContainText(email);

  // click the link to the clients route
  await page.locator("#clients-link").click();
  
  // Wait for the clients list to load
  await page.waitForSelector('.client-card', { timeout: 5000 });
  
  // Verify the default client from V01.006 migration is present
  const defaultClient = page.locator('[data-client-id="abstrauth_admin_app"]');
  await expect(defaultClient).toBeVisible();
  await expect(defaultClient).toContainText('abstrauth admin app');
});
