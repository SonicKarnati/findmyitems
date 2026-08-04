# Task 3 Reviewer-Fix Report

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
   - Focused planner/display tests passed, including the selected-alternative remainder regression.
2. `./gradlew test`
   - `BUILD SUCCESSFUL in 3s`
   - Full JUnit suite passed.
3. `./gradlew build`
   - `BUILD SUCCESSFUL in 9s`
   - Headless game tests passed: `All 28 required tests passed :)`.
4. `./gradlew runGameTest`
   - `BUILD SUCCESSFUL in 8s`
   - Headless game tests passed: `All 28 required tests passed :)`.

`git diff --check` passed.

## Concerns

- Client game tests were not run because they open a real client window.
- Gradle emits existing deprecation, `sun.misc.Unsafe`, and game-test server lag warnings; no test failures resulted.
- The unrelated pre-existing `.superpowers/sdd/task-1-report.md` modification remains untouched.
