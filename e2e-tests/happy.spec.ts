import { test, expect } from '@playwright/test';

test('sign up and in', async ({ page }) => {
  await page.goto('http://localhost:4200/');

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

  await expect(page.locator("#email")).toContainText(email);
});
