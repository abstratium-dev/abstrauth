---
trigger: glob
globs: src/main/webui/**/*.ts, src/main/webui/**/*.html
---

This project uses Angular for the frontend, deployed as part of a Quarkus server using the Quinoa extension.
the Angular source code is in the folder `src/main/webui`.

The general design pattern used for this Angular project is that components write data to the model by using the `Controller` service and they subscribe to changes in the `ModelService` using its signals.

It is the controller which should generally make backend calls. Exceptions to that are the AuthService. It should never expose / return Observable or Promise objects, rather store the results of an http request in the ModelService which should expose the model parts using signals which the interested components can subscribe to in order to read data out of the model.

Whenever making changes, remember to make sure that tests are updated and test them.

DO NOT USE `ng test`, as it hangs. 
Instead use `mvn test -DskipTests` to run angular tests.

If you are generating forms, use `https://html.spec.whatwg.org/multipage/form-control-infrastructure.html#autofill` to make the form use use autocomplete with values that a user has used before.

If you do anything with node or npm, run `nvm use v26.4.0` first.

## Zoneless change detection

This app uses `provideZonelessChangeDetection()` (no Zone.js). State changes must explicitly notify Angular.

- **New components must use `ChangeDetectionStrategy.OnPush`** — never `Eager` or `Default`. Convert existing components to `OnPush` when you modify them.
- **Template-bound mutable state must be a signal.** Use `signal()` for flags, form state, and copy-to-clipboard states; use `model()` for two-way bindings. Read in templates with `()`, and write with `.set()` / `.update()`. Plain fields that change after first render will cause `ExpressionChangedAfterItHasBeenCheckedError` or stale UI.
- **Keep async results in the model.** HTTP results go through the `Controller` into `ModelService` signals; components read them via `modelService.foo$()`.
- **React to signal changes with `effect()`**, not `ngOnChanges` or manual subscriptions.
- **Avoid `NgZone` APIs** such as `onStable`, `onMicrotaskEmpty`, `onUnstable`, and `isStable` — they do not emit in zoneless mode.
- **In tests, always provide `provideZonelessChangeDetection()`** in `TestBed`. Prefer `await fixture.whenStable()` over `fixture.detectChanges()`. If a test mutates plain state, expect `ExpressionChangedAfterItHasBeenCheckedError`.