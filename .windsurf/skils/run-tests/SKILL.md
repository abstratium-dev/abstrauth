---
name: run-tests
description: Runs unit and integration tests, Angular tests, and optionally also E2E tests.
---

# Run Tests

## When to use this skill
Use this skill when the user needs to run tests.

## How to run tests
1. Use `mvn test -DskipTests -DskipITs` to run just the Angular frontend tests.
2. Use `mvn test` to run unit and integration tests of the Quarkus backend, as well as the Angular frontend tests.
3. Use `mvn verify -DskipTests -DskipITs -Pe2e` to run the E2E tests (which also run the Angular tests, but that is OK).
