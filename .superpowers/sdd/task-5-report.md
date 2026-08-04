# Task 5 Report

## Status

Fixed Task 5 review findings. Crafting browse now uses recipe roots without planner calls, the custom viewport renders only visible rows plus one-row overscan, and `ViewportLayout` owns clipping, hit testing, and scroll bounds. Selection and hover use typed component-aware identities; asynchronous plans are rejected after query, amount, view, layout, index, recipe, or selection generation changes. Dead craftable-item caches and reflection/toString test probes were removed, and crafting statuses use the added translations.

## Verification

- `./gradlew test`: passed; 6 viewport unit tests and the full JUnit suite passed.
- `./gradlew build`: passed; included compilation and `34` headless GameTests, all required tests passed.
- `./gradlew runClientGameTest`: passed; client GameTests completed successfully.
- Final client assertions covered lazy empty crafting browse, root-only rows, zero planner requests while browsing, one plan request per selection, stable hover identity, stale async-result rejection, amount/view/layout/index/recipe generation invalidation, scroll preservation, visible-plus-overscan rendering, bottom clipping, and viewport hit testing.

## Screenshots

No screenshot files were present in `run/clientGameTest/screenshots/` after the final client run. The existing client test still requests its named screenshots during execution.

## Concerns

- Minecraft emitted pre-existing environment warnings for missing `shulkerboxtooltip`, invalid anisotropic filtering, shader attributes, and unauthenticated profile-key requests. They did not fail the tests.
- The worktree contains a pre-existing modification to `.superpowers/sdd/task-1-report.md`; it was not changed or staged.
