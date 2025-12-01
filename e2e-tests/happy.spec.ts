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

  // Click the user email link to view token claims
  await page.locator("#user-link").click();
  
  // Verify we're on the user profile page
  await expect(page.locator('h1')).toContainText('User Profile');
  await expect(page.locator('h2')).toContainText('Token Claims');
  
  // Verify key token claims are displayed
  await expect(page.locator('[data-claim="iss"] .table-cell-value')).toContainText('https://abstrauth.abstratium.dev');
  await expect(page.locator('[data-claim="email"] .table-cell-value')).toContainText(email);
  await expect(page.locator('[data-claim="name"] .table-cell-value')).toContainText(name);
  await expect(page.locator('[data-claim="email_verified"] .table-cell-value')).toContainText('false');
  await expect(page.locator('[data-claim="client_id"] .table-cell-value')).toContainText('abstratium-abstrauth');
  
  // Verify groups/roles are present (should include default roles with full client prefix)
  const groupsClaim = page.locator('[data-claim="groups"]');
  await expect(groupsClaim).toBeVisible();
  await expect(groupsClaim).toContainText('abstratium-abstrauth_user');
  await expect(groupsClaim).toContainText('abstratium-abstrauth_manage-clients');
  
  // Verify token has sub (subject) claim
  await expect(page.locator('[data-claim="sub"]')).toBeVisible();
  
  // Verify token has exp (expiration) claim
  await expect(page.locator('[data-claim="exp"]')).toBeVisible();
  
  // Verify token has iat (issued at) claim
  await expect(page.locator('[data-claim="iat"]')).toBeVisible();

  // Click the clients link to view clients page
  await page.locator("#clients-link").click();

  // Verify we're on the clients page
  await expect(page.locator('h1')).toContainText('OAuth Clients');

  // Verify the client is present
  const clientCard = page.locator('[data-client-id="abstratium-abstrauth"]');
  await expect(clientCard).toBeVisible();

  // Verify the client has the expected redirect urls (there are multiple)
  const redirectUris = clientCard.locator('.simple-list li');
  await expect(redirectUris).toHaveCount(3);
  await expect(redirectUris.nth(0)).toContainText('http://localhost:8080/auth-callback');
  await expect(redirectUris.nth(1)).toContainText('http://localhost:4200/auth-callback');
  await expect(redirectUris.nth(2)).toContainText('https://auth.abstratium.dev/auth-callback');

  // Verify the client has the expected scopes (should contain openid, profile, email)
  await expect(clientCard.locator('.badge-success').nth(0)).toContainText('openid');
  await expect(clientCard.locator('.badge-success').nth(1)).toContainText('profile');
  await expect(clientCard.locator('.badge-success').nth(2)).toContainText('email');
});
