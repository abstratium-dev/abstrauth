---
trigger: model_decision
description: Used when running tests.
---

- Run `nvm use v24.11.1` in order to make sure the correct version of node and npm are installed.
- Run tests using `mvn verify`, which will run backend but also the angular tests.
- Do NOT use `ng test` as it hangs.
- Fix tests which fail due to transactional errors last - those can be side effects of other tests failing. fix the other failing tests first.
- Don't use things like "head -n 10" very vigourously since you end up having to rerun the tests again in order to find errors. it is inefficient and slows us down.
- when writing playwright tests, avoid `page.waitForTimeout`since the docs say "Note that page.waitForTimeout() should only be used for debugging. Tests using the timer in production are going to be flaky. Use signals such as network events, selectors becoming visible and others instead." Note that when using "waitUntil", the docs say "Default is 'load'; 'networkidle' is discouraged for testing."
