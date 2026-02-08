---
trigger: glob
description: Used when running tests.
globs: src/test/java/**/.*,src/main/webui/**/*.spec.ts
---

- Run tests using `mvn verify`, which will run backend but also the Angular tests.
- Run just the angular tests with `mvn exec:exec@run-angular-tests`
- Do NOT use `ng test` as it hangs.
- Fix tests which fail due to transactional errors last - those can be side effects of other tests failing. Fix the other failing tests first.
- Don't use things like "head -n 10" very vigourously since you end up having to rerun the tests again in order to find errors. It is inefficient and slows us down.

**When you execute the tests with mvn, send the output to a file in the `/tmp` directory. search that file for results. use the same file for all test runs. That way, you will not run tests, and then need to run them again, in order to search for specific results!**

You must check that coverage is at 80% statement coverage and 70% branch coverage. Use coverage results to find missing tests.

Do not write senseless tests just to increase the coverage.

Make sure that all tests contain assertions and not just for rudimentary things. The tests should be meaningful, useful and they must test functionality.

It is EXTREMELY IMPORTANT that this project be tested using unit and integration tests.

Coverage can be measured using `mvn verify`. The backend coverage results in xml files from the folder `target/jacoco-report`. Front end coverage results are part of the output which is written when mvn executes the Angular tests as part of `mvn verify`.

Tests annotated with `@QuarkusTest` are the primary kind of test for the backend.
You can also write plain unit tests, in order to test edge cases.

NEVER disable tests e.g. with the @org.junit.jupiter.api.Disabled annotation.
NEVER delete tests just because you cannot make them work.
Do ask for help if you are going in circles and not getting the tests to pass.