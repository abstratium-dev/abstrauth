import { defineConfig, devices } from '@playwright/test';

console.log("BASE_URL: ", process.env.BASE_URL);

/**
 * Read environment variables from file.
 * https://github.com/motdotla/dotenv
 */
// import dotenv from 'dotenv';
// import path from 'path';
// dotenv.config({ path: path.resolve(__dirname, '.env') });

/**
 * Playwright Configuration
 * 
 * Usage:
 * - Manual testing: Run `mvn quarkus:dev` then `npx playwright test`
 *   Tests will use http://localhost:4200 (Quinoa Angular dev server)
 * 
 * - Maven integration: Run `mvn verify -Pe2e`
 *   Tests will use http://localhost:8080 (built Quarkus jar with H2 and static Angular files)
 *   The BASE_URL environment variable is set by Maven to trigger jar startup
 * 
 * See https://playwright.dev/docs/test-configuration.
 */
export default defineConfig({
  testDir: './e2e-tests',
  /* Run tests in files in parallel */
  fullyParallel: false,
  /* Fail the build on CI if you accidentally left test.only in the source code. */
  forbidOnly: !!process.env.CI,
  /* Retry on CI only */
  retries: process.env.CI ? 2 : 0,
  /* Run tests in series - use 1 worker */
  workers: 1,
  /* Reporter to use. See https://playwright.dev/docs/test-reporters */
  reporter: 'html',
  /* Shared settings for all the projects below. See https://playwright.dev/docs/api/class-testoptions. */
  use: {
    /* Base URL to use in actions like `await page.goto('')`. */
    baseURL: process.env.BASE_URL || 'http://localhost:4200',

    /* Collect trace when retrying the failed test. See https://playwright.dev/docs/trace-viewer */
    trace: 'on-first-retry',
  },

  /* Configure projects for major browsers */
  projects: [
    {
      name: 'chromium',
      use: { ...devices['Desktop Chrome'] },
    },

    {
      name: 'firefox',
      use: { ...devices['Desktop Firefox'] },
    },

    {
      name: 'webkit',
      use: { ...devices['Desktop Safari'] },
    },

    /* Test against mobile viewports. */
    // {
    //   name: 'Mobile Chrome',
    //   use: { ...devices['Pixel 5'] },
    // },
    // {
    //   name: 'Mobile Safari',
    //   use: { ...devices['iPhone 12'] },
    // },

    /* Test against branded browsers. */
    // {
    //   name: 'Microsoft Edge',
    //   use: { ...devices['Desktop Edge'], channel: 'msedge' },
    // },
    // {
    //   name: 'Google Chrome',
    //   use: { ...devices['Desktop Chrome'], channel: 'chrome' },
    // },
  ],

  /* Run your local dev server before starting the tests */
  webServer: process.env.BASE_URL ? {
    // When BASE_URL is set (Maven integration), start the built jar
    command: './start-e2e-server.sh',
    url: 'http://localhost:8080/q/health/ready',
    reuseExistingServer: !process.env.CI,
    timeout: 12000,
    stdout: 'pipe',
    stderr: 'pipe',
  } : undefined, // When BASE_URL is not set (manual), assume server is already running
});
