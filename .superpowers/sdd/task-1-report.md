# Task 1 Report

## Files Changed

- `src/main/java/dev/smpb/findmyitems/config/ModConfig.java`: Added persisted `filterInventory` and `filterContainers` booleans, both defaulting to `true`.
- `src/client/java/dev/smpb/findmyitems/config/ConfigScreen.java`: Added general-category boolean toggles that update the shared config passed to `create` and save through the existing runnable.
- `src/main/resources/assets/findmyitems/lang/en_us.json`: Added labels and tooltips for both toggles.
- `src/test/java/dev/smpb/findmyitems/config/ModConfigTest.java`: Added default-value and false-value JSON round-trip coverage.

## Tests and Commands

- `./gradlew test --tests '*ModConfigTest'` before implementation: failed as expected during test compilation because the new fields did not exist.
- `./gradlew test --tests '*ModConfigTest'` after implementation: passed. Gradle reported `BUILD SUCCESSFUL` with 6 actionable tasks.
- `git diff --check`: passed with no whitespace errors.

## Concerns

- No focused-test concerns. The broader build and game tests were not run because this task brief specifies the focused `ModConfigTest` command.

## Commit

- `9735731 feat: add filter bar visibility settings`
