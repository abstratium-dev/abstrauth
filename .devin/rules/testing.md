---
trigger: glob
description: Used when running tests.
globs: src/test/java/**/.*,src/main/webui/**/*.spec.ts
---

- Run all backend java tests using `./scripts/run-java-tests.py` which gives you a summary of the errors so that you do not have to use tail! do not use `mvn test` or `mvn verify`.
  - To run a single test class: `./scripts/run-java-tests.py MyTestClass`
  - To run a single test method: `./scripts/run-java-tests.py MyTestClass#testMethod`
- Run angular tests with `./scripts/run-ng-tests.py` which gives a summary of the errors so that you do not have to use tail!
- Do NOT use `ng test` as it hangs.
- Fix tests which fail due to transactional errors last - those can be side effects of other tests failing. Fix the other failing tests first.
- Do not use things like "head -n 10" very vigourously since you end up having to rerun the tests again in order to find errors. It is inefficient and slows us down.

- if you do run tests using mvn, then do that in order to run all tests like they will in the final build pipeline.
**When you execute the tests with mvn, send the output to a file in the `tmp` directory of this project (it may need creating). search that file for results. use the same file for all test runs. That way, you will not run tests, and then need to run them again, in order to search for specific results!**

You must check that coverage is at 80% statement coverage and 70% branch coverage. Use coverage results to find missing tests.

Do not write senseless tests just to increase the coverage. Instead, make sure that all tests contain assertions and not just for rudimentary things. The tests should be meaningful, useful and they must test functionality.

It is EXTREMELY IMPORTANT that this project be tested using unit and integration tests.

Java Coverage can be measured using `./scripts/run-java-tests.py`. After it completes, run `./scripts/show-java-coverage.py` to display an LLM-friendly summary of the backend coverage results. It reads the JaCoCo XML from `target/jacoco-report/jacoco.xml`, shows overall and per-package coverage, highlights classes below the 80% statement / 70% branch thresholds, and includes instructions for drilling into specific packages or classes with grep if more detail is needed.

Front end coverage results are part of the output which is written when `./scripts/run-ng-tests.py` executes the Angular tests.

Tests annotated with `@QuarkusTest` are the primary kind of test for the backend.
You can also write plain unit tests, in order to test edge cases.

Unless the tests are explicitly testing cross-tenancy, the tests should use the default tenant (organisation id 00000000-0000-0000-0000-000000000000). Test should also use a fresh timestamp in names, etc., to create new test data that is isolated and causes no constraint violations. Tests should be designed to be isolated. Ensure that tests fulfil these non-functional requirements.

NEVER disable tests e.g. with the @org.junit.jupiter.api.Disabled annotation.

NEVER delete tests just because you cannot make them work.
Do ask for help if you are going in circles and not getting the tests to pass.

NEVER run `quarkus dev` or `mvn quarkus:dev` yourself. If you need the server to be running, ask the user to start it.
NEVER kill quarkus yourself, always ask the user to do that.

If the test requires transactions, consider the following, or alternatively use the class `TestTransactionHelper`. From the quarkus documentation:
  - You can use the standard Quarkus @Transactional annotation on tests, but this means that the changes your test makes to the database will be persistent. If you want any changes made to be rolled back at the end of the test you can use the `io.quarkus.test.TestTransaction` annotation. This will run the test method in a transaction, but roll it back once the test method is complete to revert any database changes.

Initial test data is seeded using `src/test/resources/db/migration/R__01__test_defaults.sql`. Also consider using the class `TestDatabaseResetHelper` to reset the database before each test, if the test is hard to make stable.
