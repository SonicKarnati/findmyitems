# Screen Eligibility Fix

## Red Test

Added a real client-game assertion that the opened chest screen contains the filter `EditBox` and the opened furnace screen does not. Before the production change, `./gradlew runClientGameTest` failed at the furnace assertion:

```text
java.lang.AssertionError: expected filter bar visible=false, but was true
    at dev.smpb.findmyitems.test.FindMyItemsClientGameTest.assertFilterBarVisible(FindMyItemsClientGameTest.java:197)
    at dev.smpb.findmyitems.test.FindMyItemsClientGameTest.runTest(FindMyItemsClientGameTest.java:68)
```

This demonstrated that the existing `AbstractContainerScreen` fallback incorrectly enabled the bar for furnaces.

## Fix

`InventorySearchController.isEnabled` now enables container filtering only for `ContainerScreen` and `ShulkerBoxScreen`. Survival `InventoryScreen` and creative exclusion remain handled separately, so inventory settings and creative behavior are preserved while furnace and workstation screens are excluded.

## Verification

- `./gradlew runClientGameTest`: passed after the fix; `BUILD SUCCESSFUL` in 30s.
- `./gradlew build`: passed; `BUILD SUCCESSFUL` in 9s.
- `./gradlew runGameTest`: passed; all 25 required tests passed and `BUILD SUCCESSFUL` in 9s.

The client run emitted expected environment warnings about missing optional `shulkerboxtooltip`, shader attributes, and unauthenticated profile keys; none affected the test result.
