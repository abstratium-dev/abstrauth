---
trigger: glob
globs: e2e-tests/**/*.ts
---

- end to end (e2e) tests are written using playwright
- use the playwright-cli skill to help understand what is displayed in the browser and how to use it
- When writing playwright tests for end to end testing, in the `e2e-tests` folder, avoid `page.waitForTimeout`since the docs say "Note that page.waitForTimeout() should only be used for debugging. Tests using the timer in production are going to be flaky. Use signals such as network events, selectors becoming visible and others instead." Note that when using "waitUntil", the docs say "Default is 'load'; 'networkidle' is discouraged for testing."
- tests use the "page object model" pattern - each page is encapsulated in a file named `e2e-tests/pages`.
- page files should consist of low level functions used to select elements, and higher level functions that encapsulate functionality of the page, e.g. filling in a form or clicking a button.
- tests are found in files in the folder `e2e-tests/tests`.
- also use the source code of the angular ui in the folder `src/main/webui` to work out how to encapsulate the pages
- tests should include assertions to ensure that the page is displaying the correct information
- The tests should be meaningful, useful and they must test functionality.
- you don't need to start the server, it will be running already
- if you are writing a test and the description that the user has provide is causing errors, you might have to fix the source code. if you are not sure, ask for help.
- never set the URL directly with the `page.go` function; use buttons and links to navigate through the application. This is NOT true for starting the test which requires you to use the `go` function to get to the home page.
- when you run the tests (e.g. `npx playwright test happy.spec.ts`) run them so that playwright doesn't serve an html report at the end, because that blocks you from continuing until i kill the process. also, choose just one browser, chromium, rather than running the tests in firefox too.
- consider adding IDs to elements in the source html code, so that selectors are simpler