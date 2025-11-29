import { test, expect } from '@playwright/test';

test('sign up and email already exists', async ({ page }) => {
  await page.goto('http://localhost:4200/');

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

  // now go back to the home page and sign up again
  await page.goto('http://localhost:4200/');

  await page.locator("#signup-link").click();


  await page.locator("#email").fill(email);
  await page.locator("#name").fill(name);
  await page.locator("#username").fill(username);
  await page.locator("#password").fill(password);

  await page.locator("#create-account-button").click();

  await expect(page.locator("#message")).toContainText("Email already exists");
});

test('sign up and username already exists', async ({ page }) => {
  await page.goto('http://localhost:4200/');

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

  // now go back to the home page
  await page.goto('http://localhost:4200/');

  await page.locator("#signup-link").click();


  await page.locator("#email").fill(email + "2"); // make it unique again
  await page.locator("#name").fill(name);
  await page.locator("#username").fill(username);
  await page.locator("#password").fill(password);

  await page.locator("#create-account-button").click();

  await expect(page.locator("#message")).toContainText("Username already exists");
});
