---
trigger: model_decision
description: when working with angular files, typescript files, or any files inside the src/main/webui folder or its children
---

use `nvm use v24.11.1` to set the correct version of node and npm, when running tools like `ng test` or node or similar.

The general design pattern used here is that components write data to the model by using the `Controller` component and they subscribe to changes in the `Model` using its signals.

It is the controller which should generally make backend calls. Exceptions to that are the AuthService.

Whenever making changes, remember to make sure that tests are updated and test them using `ng test`