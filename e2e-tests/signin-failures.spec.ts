import { test, expect } from '@playwright/test';

test('user denies access', async ({ page }) => {
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

  await page.locator("#signin-button").click();

  await page.locator("#deny-button").click();

  await expect(page.locator(".error-box")).toContainText("Error: access_denied - User denied authorization");

  await expect(page.url()).toContain("/auth-callback?error=access_denied&error_description=User%20denied%20authorization");
});

test('wrong username or password', async ({ page }) => {
  await page.goto('/');

  await page.getByRole('textbox', { name: 'Username' }).fill("wrong@doesnt-exist.com");
  await page.getByRole('textbox', { name: 'Password' }).fill("wrong");
  
  await page.locator("#signin-button").click();

  await expect(page.locator(".error-box")).toContainText("Invalid username or password");

});
