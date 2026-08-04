# Task 3 Reviewer-Fix Report

## Atomicity Follow-Up

- Recipe candidate evaluation now rolls back to the candidate's starting inventory when any sibling child is missing. Child consumption, generated surplus, and remainders are committed only after all children succeed.
- Added a partial-child-success regression asserting the missing sibling leaves the original input inventory unchanged and all candidate deltas empty.

## Conservation Follow-Up

- Candidate output surplus is now committed only after every ingredient child is satisfied. Missing-input candidates return neither generated output nor recipe remainders.
- The missing-input regression now asserts both empty `remainders()` and empty `surplusDelta()`; the successful batch regression supplies all inputs before asserting generated surplus.

## Final Follow-Up

- Candidates with missing ingredient children no longer return recipe remainders. Generated output surplus behavior remains unchanged; only unsupported remainder returns are suppressed.
- Disallowed recipes are skipped with `continue`, so later allowed alternatives are evaluated under the active station policy.
- Added regressions for missing-input remainder conservation and mixed-policy recipe alternatives.

## Follow-Up Fix

- `RecipeCatalog.from` now derives remainder mappings per ingredient alternative by evaluating the live crafting recipe's remaining-item behavior. The planner applies only the mapping for the selected ingredient, rather than aggregating all alternatives.
- Added a pure alternative-remainder regression and a live cake-recipe GameTest. The live test plans with three milk buckets and verifies three consumed milk buckets, three returned buckets, and conservation in the remaining inventory.
- `CatalogScreen.craftingRows` now obtains the target identity through `RecipeCatalog.stackKey(new ItemStack(target), level)`, preserving registry-backed component identity.
- Reduced `PlanScore` to the dimensions currently computed by this task: missing quantity, missing kinds, craft operations, reversible conversions, and tree depth. No unsupported dimensions are hard-coded.

## Fixes

- Recipe definitions retain station, width, height, and per-craft remainder metadata. Planning policy now rejects disabled stations and recipes larger than the supported inventory/table grid.
- Recipe catalog extraction derives ingredient, output, and remainder `StackKey` values through registry-backed component serialization. No catalog path hard-codes `{}`.
- Ingredient alternatives are enumerated and scored as candidates. Shared stock is isolated per candidate and competing recipe alternatives are compared lexicographically.
- Long arithmetic uses checked add, subtract, multiply, and aggregation paths. Overflow rejects the candidate instead of wrapping.
- Crafting remainders are returned separately and added to the simulated remaining inventory. Tests cover water-bucket-style conservation.
- Cancellation is checked at candidate/ingredient evaluation boundaries. Memo keys include catalog generation, target, quantity, full component-aware inventory, policy, and active path.
- Display root and node IDs derive from item identity and semantic ancestry rather than list positions. Root nodes retain their `PlanScore`.
- Removed the old depth-limited, item-ID planner and migrated `CatalogScreen` to the catalog, immutable inventory, planner, and display flattening APIs.
- Removed unused catalog imports and added regressions for policy, alternatives, remainders, conservation, cancellation, memo state/invalidation, semantic IDs, score preservation, and overflow.

## Verification

Commands ran serially in the requested order after the final source change:

1. `./gradlew test --tests '*CraftingPlannerTest' --tests '*DisplayPlanTest'`
   - `BUILD SUCCESSFUL in 1s`
   - 21 focused planner/display tests passed, including the partial-child rollback regression.
2. `./gradlew test`
   - `BUILD SUCCESSFUL in 4s`
   - Full JUnit suite passed.
3. `./gradlew build`
   - `BUILD SUCCESSFUL in 11s`
   - Headless game tests passed: `All 28 required tests passed :)`.
4. `./gradlew runGameTest`
   - `BUILD SUCCESSFUL in 9s`
   - Headless game tests passed: `All 28 required tests passed :)`.

`git diff --check` passed.

## Concerns

- Client game tests were not run because they open a real client window.
- Gradle emits existing deprecation, `sun.misc.Unsafe`, and game-test server lag warnings; no test failures resulted.
- The unrelated pre-existing `.superpowers/sdd/task-1-report.md` modification remains untouched.
