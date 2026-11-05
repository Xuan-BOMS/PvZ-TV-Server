# Dashboard browser regression tests

From the repository root:

```sh
npm ci
npx playwright install chromium
npm test
```

These tests load the actual dashboard HTML, CSS and JavaScript in Chromium with
controlled API responses. They cover asynchronous chart updates, complete deck
contents, filter changes, empty results, live updates and responsive layouts.
No database or running Java server is required. CI runs them alongside Maven.

To use an existing Chromium-based browser, set `PLAYWRIGHT_EXECUTABLE_PATH` to its
executable path before running `npm test`.
