---
trigger: model_decision
description: when working with angular files, typescript files, or any files inside the src/main/webui folder or its children
---

use `nvm use v24.11.1` to set the correct version of node and npm, when running tools like `ng test` or node or similar.

The general design pattern used here is that components write data to the model by using the `Controller` component and they subscribe to changes in the `Model` using its signals.

It is the controller which should generally make backend calls. Exceptions to that are the AuthService. It should never expose / return Observable objects, rather store the results of an http request in the ModelService which should expose the model parts using signals which the interested components can subscribe to in order to read data out of the model.

Whenever making changes, remember to make sure that tests are updated and test them using `ng test`.

code coverage is measured using `ng test --code-coverage --watch=false` - goals are 80% statement coverage and 70% branch coverage.