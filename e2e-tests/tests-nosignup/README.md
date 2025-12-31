# Tests-NoSignup - Ordered Test Suite

## Overview

This folder contains E2E tests that **must run in a specific order** because they share database state and build upon each other's results.

## Test Execution Order

The tests are prefixed with numbers to ensure they run in the correct order:

1. **`1-happy2.spec.ts`** - Creates admin account and sets up initial state
   - Creates admin account (first account gets admin privileges)
   - Creates manager account via invite
   - Tests role-based access control
   - Creates test OAuth client
   - Sets up accounts with various roles

2. **`2-signin-failures.spec.ts`** - Tests authentication error conditions
   - Tests invalid credentials
   - Tests error page rendering
   - Requires admin account to exist (created in test 1)

3. **`3-client-integration.spec.ts`** - Tests complete OAuth flow
   - Creates OAuth client
   - Configures example client server
   - Tests full authorization code flow with PKCE
   - Requires admin account and clean state (from test 1)

## Running the Tests

### Run all tests in order:
```bash
npx playwright test tests-nosignup/
```

Playwright runs test files in **alphabetical order by filename**, so the numeric prefixes ensure correct execution.

### Run a specific test:
```bash
npx playwright test tests-nosignup/1-happy2.spec.ts
npx playwright test tests-nosignup/2-signin-failures.spec.ts
npx playwright test tests-nosignup/3-client-integration.spec.ts
```

### Run with UI mode (for debugging):
```bash
npx playwright test tests-nosignup/ --ui
```

## Important Notes

⚠️ **Do NOT run these tests in parallel** - they share database state and will interfere with each other.

⚠️ **Do NOT rename the files** - the numeric prefixes are critical for ordering.

⚠️ **Run test 1 first** - The other tests depend on the state created by `1-happy2.spec.ts`.

## Test Dependencies

```
1-happy2.spec.ts
    ↓ Creates admin account
    ↓ Creates manager account
    ↓ Creates test client
    ↓
2-signin-failures.spec.ts
    ↓ Uses admin account
    ↓
3-client-integration.spec.ts
    Uses admin account
    Creates new client
    Tests OAuth flow
```

## Playwright Configuration

The tests automatically run in order because:
- Playwright runs test files alphabetically by default
- Files are prefixed with `1-`, `2-`, `3-` to enforce order
- No special configuration needed

## Troubleshooting

If tests fail:

1. **Check test order**: Ensure files have numeric prefixes
2. **Clean database**: Run test 1 first to reset state
3. **Check for parallel execution**: Ensure `--workers=1` if needed
4. **Review logs**: Check console output for specific errors

## Related Documentation

- See `happy2.md` for detailed flow of test 1
- See main `e2e-tests/README.md` for overall test setup
