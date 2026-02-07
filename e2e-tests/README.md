# End-to-End Tests

This directory contains Playwright-based end-to-end tests for the Abstrauth OAuth authorization server.

**IMPORTANT**: E2E tests require the application to be built with H2 database support. 
Since `quarkus.datasource.db-kind` is a build-time property, you must use the `e2e` profile:

    source /w/abstratium-abstrauth.env
    mvn verify -Pe2e

This will:
1. Build the application with H2 database configured
2. Run unit tests, including angular tests
3. Package the JAR with H2 database drivers too
4. Start Quarkus with H2 via `start-e2e-server.sh`
5. Run Playwright tests **twice**:
   - First with `ALLOW_SIGNUP=false` (tests from `tests-nosignup/` directory)
   - Then with `ALLOW_SIGNUP=true` (tests from `tests-signup/` directory)
6. Stop the server

**Note**: Running `mvn verify` without `-Pe2e` will skip the Playwright tests.


## Directory Structure

### `/pages`
Contains Page Object Model (POM) files that encapsulate page interactions and element locators. These provide reusable functions for common operations:
- `signin.page.ts` - Sign-in, sign-up, and authentication flows
- `accounts.page.ts` - Account management operations
- `clients.page.ts` - OAuth client management
- `authorize.page.ts` - Authorization approval flows
- `change-password.page.ts` - Password change operations
- `header.ts` - Header navigation and user menu interactions

### `/tests-nosignup`
Tests that run when signup is **disabled** (`ALLOW_SIGNUP=false`). These tests assume:
- User signup is not available through the UI
- Accounts must be created by administrators via invite links
- Tests focus on invite-based account creation, role management, and authorization flows

⚠️ **IMPORTANT**: Tests in this folder **must run in order** (see `tests-nosignup/README.md`):
1. `1-happy2.spec.ts` - Creates admin account and sets up initial state
2. `2-signin-failures.spec.ts` - Tests authentication errors with existing admin
3. `3-client-integration.spec.ts` - Tests complete OAuth flow

The numeric prefixes ensure Playwright runs them alphabetically in the correct order.

### `/tests-signup`
Tests that run when signup is **enabled** (`ALLOW_SIGNUP=true`). These tests verify:
- Self-service user registration through the signup form
- Signup validation and error handling

**Files:**
- `signup-failures.spec.ts` - Signup failure scenarios (validation errors, duplicate accounts)

### `/start-e2e-server.sh`
Shell script that starts the Quarkus application for e2e testing. It:
- Runs the built JAR file with the `e2e` profile (uses H2 in-memory database)
- Accepts the `ALLOW_SIGNUP` environment variable and passes it to Quarkus
- Is automatically invoked by Playwright when `BASE_URL` is set

## Environment Variables

### `BASE_URL`
Controls whether Playwright starts the server automatically:
- **Not set**: Assumes server is already running (manual testing mode)
- **Set to `http://localhost:8080`**: Playwright will execute `start-e2e-server.sh` to start the server

### `ALLOW_SIGNUP`
Controls which test directory runs and configures the Quarkus application:
- **`false` or unset**: Runs tests in `tests-nosignup/` directory, signup is disabled in the application
- **`true`**: Runs tests in `tests-signup/` directory, signup is enabled in the application

## Running Tests

### Manual Testing (Development)
When developing tests or debugging, you can run the server manually and then run tests:

```bash
# Start Quarkus in dev mode
source /w/abstratium-abstrauth.env
mvn quarkus:dev

# Start the client example
cd client-example
npm start

# In another terminal, run tests
cd e2e-tests
npx playwright test
```

By default, this runs the `tests-nosignup/` directory. To run signup tests:

```bash
ALLOW_SIGNUP=true npx playwright test
```

### Maven Integration (CI/CD)

#### Run all e2e tests:
```bash
mvn verify -Pe2e
```

This will:
1. Build the application with H2 database support
2. Package the JAR
3. Start the server via `start-e2e-server.sh`
4. Run tests from `tests-nosignup/` directory with `ALLOW_SIGNUP=false`
5. Run tests from `tests-signup/` directory with `ALLOW_SIGNUP=true`
6. Stop the server

The `e2e` profile runs both test suites sequentially to ensure complete coverage of both signup-enabled and signup-disabled scenarios.

## Configuration

### `playwright.config.ts`
Main Playwright configuration file. Key settings:
- `testDir`: Dynamically set based on `ALLOW_SIGNUP` env var
- `baseURL`: Defaults to `http://localhost:8080`
- `webServer`: Conditionally starts server when `BASE_URL` is set
- `workers: 1`: Tests run sequentially (not in parallel) to avoid database conflicts
- `forbidOnly`: Prevents `test.only` from being committed to CI

### `pom.xml` Profiles
- **`e2e`**: Runs e2e tests twice in sequence:
  1. First with `ALLOW_SIGNUP=false` (tests from `tests-nosignup/`)
  2. Then with `ALLOW_SIGNUP=true` (tests from `tests-signup/`)

The profile activates the `e2e` Quarkus profile which configures H2 database at build time.

## Writing Tests

### Use Page Objects
Always use functions from the `/pages` directory instead of directly interacting with elements:

```typescript
// ❌ Bad - direct element interaction
await page.locator("#username").fill("admin@abstratium.dev");
await page.locator("#password").fill("password");
await page.locator("#signin-button").click();

// ✅ Good - use page object functions
import { signInAsAdmin } from '../pages/signin.page';
await signInAsAdmin(page);
```

### Wait for Elements
Always wait for elements to be visible before interacting:

```typescript
await page.locator("#username").waitFor({ state: 'visible', timeout: 10000 });
await page.locator("#username").fill("test@example.com");
```

### Test Isolation
Each test should be independent and not rely on state from other tests. The `happy2.spec.ts` test includes database cleanup to ensure a clean state.

## Playwright Commands

    cd e2e-tests

    npx playwright test --ui
     # Starts the interactive UI mode.

    npx playwright test --project=chromium
     # Runs the tests only on Desktop Chrome.

    npx playwright test example
     # Runs the tests in a specific file.

    npx playwright test --debug
     # Runs the tests in debug mode.

    npx playwright codegen
     # Auto generate tests with Codegen.


## Debugging Tests

### Run in headed mode:
```bash
npx playwright test --headed
```

### Run specific test file:
```bash
npx playwright test tests-nosignup/happy.spec.ts
```

### Run with debug mode:
```bash
npx playwright test --debug
```

### View test report:
```bash
npx playwright show-report
```

## Common Issues

### `ERR_CONNECTION_REFUSED`
The server is not running. Either:
- Start Quarkus manually with `mvn quarkus:dev`, or
- Set `BASE_URL=http://localhost:8080` to let Playwright start the server

### Test timeout waiting for elements
The Angular application may not have loaded yet. Ensure you're waiting for elements with appropriate timeouts:
```typescript
await page.locator("#username").waitFor({ state: 'visible', timeout: 10000 });
```

### Port 8080 already in use
Another instance of Quarkus is running. Stop it before running e2e tests via Maven.

## test.only

Playwright's `test.only` allows running a single test during development:

```typescript
test.only('this test will run', async ({ page }) => {
  // only this test runs
});
```

The `forbidOnly: !!process.env.CI` configuration prevents `test.only` from being accidentally committed, as it would cause other tests to be skipped in CI.
