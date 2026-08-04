package dev.smpb.findmyitems.craft;

import dev.smpb.findmyitems.model.StackKey;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

final class CraftingPlannerTest {
    private static StackKey key(String id) {
        return new StackKey(id, "{}");
    }

    private static RecipeCatalog catalog(RecipeCatalog.RecipeDefinition... recipes) {
        return RecipeCatalog.of(List.of(recipes));
    }

    private static RecipeCatalog.RecipeDefinition recipe(String output, long batch, String[]... slots) {
        return RecipeCatalog.recipe(key(output), batch,
                List.of(slots).stream().map(slot -> List.of(slot).stream().map(CraftingPlannerTest::key).toList()).toList());
    }

    @Test
    void sharedStockIsNotClaimedByBothSiblingBranches() {
        var catalog = catalog(
                recipe("example:root", 1, new String[]{"example:left"}, new String[]{"example:right"}),
                recipe("example:left", 1, new String[]{"minecraft:oak_planks"}, new String[]{"minecraft:oak_planks"}, new String[]{"minecraft:oak_planks"}, new String[]{"minecraft:oak_planks"}),
                recipe("example:right", 1, new String[]{"minecraft:oak_planks"}, new String[]{"minecraft:oak_planks"}, new String[]{"minecraft:oak_planks"}, new String[]{"minecraft:oak_planks"}));

        var plan = CraftingPlanner.plan(catalog, key("example:root"), 1,
                PlanningInventory.of(Map.of(key("minecraft:oak_planks"), 4)), PlanningPolicy.DEFAULT);

        assertEquals(4, plan.missing("minecraft:oak_planks"));
    }

    @Test
    void missingIngredientsNeverInventIntermediateBlocks() {
        var catalog = catalog(
                recipe("minecraft:chest", 1, new String[]{"minecraft:iron_ingot"}, new String[]{"minecraft:iron_ingot"}, new String[]{"minecraft:iron_ingot"}, new String[]{"minecraft:iron_ingot"}, new String[]{"minecraft:iron_ingot"}),
                recipe("minecraft:iron_block", 1, new String[]{"minecraft:iron_ingot"}, new String[]{"minecraft:iron_ingot"}, new String[]{"minecraft:iron_ingot"}, new String[]{"minecraft:iron_ingot"}, new String[]{"minecraft:iron_ingot"}, new String[]{"minecraft:iron_ingot"}, new String[]{"minecraft:iron_ingot"}, new String[]{"minecraft:iron_ingot"}, new String[]{"minecraft:iron_ingot"}),
                recipe("minecraft:iron_ingot", 9, new String[]{"minecraft:iron_block"}));

        var plan = CraftingPlanner.plan(catalog, key("minecraft:chest"), 1,
                PlanningInventory.of(Map.of(key("minecraft:iron_ingot"), 2)), PlanningPolicy.DEFAULT);

        assertEquals(3, plan.missing("minecraft:iron_ingot"));
        assertFalse(plan.flattenedItemIds().contains("minecraft:iron_block"));
    }

    @Test
    void ownedConversionSourceCanBeUsedButMissingSourceCannotBootstrapIt() {
        var catalog = catalog(recipe("minecraft:iron_ingot", 9,
                new String[]{"minecraft:iron_block"}),
                recipe("minecraft:iron_block", 1,
                        new String[]{"minecraft:iron_ingot"}, new String[]{"minecraft:iron_ingot"}, new String[]{"minecraft:iron_ingot"},
                        new String[]{"minecraft:iron_ingot"}, new String[]{"minecraft:iron_ingot"}, new String[]{"minecraft:iron_ingot"},
                        new String[]{"minecraft:iron_ingot"}, new String[]{"minecraft:iron_ingot"}, new String[]{"minecraft:iron_ingot"}));

        var owned = CraftingPlanner.plan(catalog, key("minecraft:iron_ingot"), 9,
                PlanningInventory.of(Map.of(key("minecraft:iron_block"), 1)), PlanningPolicy.DEFAULT);
        var absent = CraftingPlanner.plan(catalog, key("minecraft:iron_ingot"), 9,
                PlanningInventory.empty(), PlanningPolicy.DEFAULT);

        assertTrue(owned.hasConversion("minecraft:iron_block", "minecraft:iron_ingot"));
        assertEquals(0, absent.conversionCount());
    }

    @Test
    void cyclesTerminateWithoutRecursionDepthSentinel() {
        var catalog = catalog(
                recipe("example:a", 1, new String[]{"example:b"}),
                recipe("example:b", 1, new String[]{"example:c"}),
                recipe("example:c", 1, new String[]{"example:a"}));

        var plan = CraftingPlanner.plan(catalog, key("example:a"), 1,
                PlanningInventory.empty(), PlanningPolicy.DEFAULT);

        assertEquals(1, plan.missing("example:a"));
        assertTrue(plan.flattenedItemIds().size() < 10);
    }

    @Test
    void batchOutputRoundsUpAndReportsGeneratedSurplus() {
        var catalog = catalog(recipe("example:torch", 4,
                new String[]{"minecraft:coal"}, new String[]{"minecraft:stick"}));
        var plan = CraftingPlanner.plan(catalog, key("example:torch"), 5,
                PlanningInventory.of(Map.of(key("minecraft:coal"), 2L, key("minecraft:stick"), 2L)),
                PlanningPolicy.DEFAULT);

        assertEquals(2, plan.root().craftCount());
        assertEquals(3, plan.generatedSurplus(key("example:torch")));
    }

    @Test
    void directStockIsPreferredOverCraftingAnAlternative() {
        var catalog = catalog(
                RecipeCatalog.recipe(key("example:widget"), 1,
                        List.of(List.of(key("example:direct"), key("example:crafted")))),
                recipe("example:crafted", 1, new String[]{"minecraft:diamond"}));
        var plan = CraftingPlanner.plan(catalog, key("example:widget"), 1,
                PlanningInventory.of(Map.of(key("example:direct"), 1, key("minecraft:diamond"), 1)), PlanningPolicy.DEFAULT);

        assertEquals(0, plan.missing("example:direct"));
        assertEquals(0, plan.missing("minecraft:diamond"));
        assertEquals(1, plan.remainingInventory().count(key("minecraft:diamond")));
    }

    @Test
    void componentBearingStacksRemainDistinct() {
        var plain = new StackKey("minecraft:diamond_sword", "{}");
        var enchanted = new StackKey("minecraft:diamond_sword", "{sharpness:5}");
        var inventory = PlanningInventory.of(Map.of(plain, 1L, enchanted, 2L));

        assertEquals(1, inventory.count(plain));
        assertEquals(2, inventory.count(enchanted));
    }

    @Test
    void longQuantitiesAndOverflowFailSafely() {
        var catalog = catalog(recipe("example:output", Long.MAX_VALUE,
                new String[]{"example:input"}));
        var plan = CraftingPlanner.plan(catalog, key("example:output"), 2,
                PlanningInventory.empty(), PlanningPolicy.DEFAULT);

        assertTrue(plan.failedCandidates() > 0);
        assertEquals(2, plan.missing("example:output"));
    }

    @Test
    void ingredientAggregationOverflowFailsTheCandidate() {
        var catalog = catalog(recipe("example:output", 1,
                new String[]{"example:input"}, new String[]{"example:input"}));
        var plan = CraftingPlanner.plan(catalog, key("example:output"), Long.MAX_VALUE,
                PlanningInventory.empty(), PlanningPolicy.DEFAULT);

        assertTrue(plan.failedCandidates() > 0);
        assertEquals(Long.MAX_VALUE, plan.missing("example:output"));
    }

    @Test
    void ingredientAlternativesAreEvaluatedAsCandidates() {
        var a = key("example:a");
        var b = key("example:b");
        var catalog = catalog(RecipeCatalog.recipe(key("example:widget"), 1,
                List.of(List.of(a, b), List.of(a, b))));

        var plan = CraftingPlanner.plan(catalog, key("example:widget"), 1,
                PlanningInventory.of(Map.of(a, 1L, b, 1L)), PlanningPolicy.DEFAULT);

        assertTrue(plan.missing().isEmpty());
        assertEquals(0, plan.remainingInventory().count(a));
        assertEquals(0, plan.remainingInventory().count(b));
    }

    @Test
    void policyRejectsUnsupportedStationAndRecipeSize() {
        var tableRecipe = RecipeCatalog.recipe(key("example:table_output"), 1,
                List.of(List.of(key("example:material"))), RecipeCatalog.Station.CRAFTING_TABLE, 3, 3, Map.of());
        var inventoryRecipe = RecipeCatalog.recipe(key("example:inventory_output"), 1,
                List.of(List.of(key("example:material"))), RecipeCatalog.Station.INVENTORY, 2, 2, Map.of());
        var catalog = catalog(tableRecipe, inventoryRecipe);
        var stock = PlanningInventory.of(Map.of(key("example:material"), 1L));

        var tableDenied = CraftingPlanner.plan(catalog, key("example:table_output"), 1, stock,
                new PlanningPolicy(false, true, 64));
        var inventoryAllowed = CraftingPlanner.plan(catalog, key("example:inventory_output"), 1, stock,
                new PlanningPolicy(false, true, 64));

        assertEquals(1, tableDenied.missing("example:table_output"));
        assertTrue(inventoryAllowed.missing().isEmpty());
    }

    @Test
    void recipeRemaindersAreReturnedAndConservationIsExplicit() {
        var bucket = key("minecraft:bucket");
        var waterBucket = key("minecraft:water_bucket");
        var catalog = catalog(RecipeCatalog.recipe(key("example:stew"), 1,
                List.of(List.of(waterBucket)), RecipeCatalog.Station.INVENTORY, 2, 2,
                Map.of(bucket, 1L)));
        var initial = PlanningInventory.of(Map.of(waterBucket, 1L));
        var plan = CraftingPlanner.plan(catalog, key("example:stew"), 1, initial, PlanningPolicy.DEFAULT);

        assertEquals(1, plan.remainders().get(bucket));
        assertEquals(1, plan.consumedDelta().get(waterBucket));
        assertEquals(1, plan.remainingInventory().count(bucket));
        assertEquals(initial.count(waterBucket), plan.consumedDelta().get(waterBucket));
    }

    @Test
    void selectedIngredientAlternativeReturnsOnlyItsRemainder() {
        var empty = key("example:empty_container");
        var filled = key("example:filled_container");
        var output = key("example:output");
        var catalog = catalog(RecipeCatalog.recipeWithAlternativeRemainders(output, 1,
                List.of(List.of(empty, filled)), RecipeCatalog.Station.INVENTORY, 2, 2,
                Map.of(empty, Map.of(empty, 1L), filled, Map.of(filled, 1L))));

        var plan = CraftingPlanner.plan(catalog, output, 1,
                PlanningInventory.of(Map.of(empty, 1L)), PlanningPolicy.DEFAULT);

        assertEquals(Map.of(empty, 1L), plan.remainders());
        assertEquals(1, plan.remainingInventory().count(empty));
        assertEquals(0, plan.remainingInventory().count(filled));
    }

    @Test
    void planRetainsTheSelectedRecipeAlternativeAndItsBatchData() {
        var output = key("example:output");
        var unavailable = key("example:unavailable");
        var selected = key("example:selected");
        var remainder = key("example:remainder");
        var first = RecipeCatalog.recipeWithAlternativeRemainders(output, 2,
                List.of(List.of(unavailable, selected)), RecipeCatalog.Station.INVENTORY, 2, 2,
                Map.of(unavailable, Map.of(unavailable, 1L), selected, Map.of(remainder, 1L)));
        var catalog = catalog(first);

        var plan = CraftingPlanner.plan(catalog, output, 3,
                PlanningInventory.of(Map.of(selected, 2L)), PlanningPolicy.DEFAULT);

        assertEquals(first, plan.root().selectedRecipe());
        assertEquals(List.of(selected), plan.root().selectedIngredients());
        assertEquals(2, plan.root().craftCount());
        assertEquals(Map.of(remainder, 2L), plan.root().generatedRemainders());
    }

    @Test
    void missingIngredientDoesNotReturnRecipeRemainders() {
        var input = key("example:input");
        var remainder = key("example:remainder");
        var output = key("example:output");
        var catalog = catalog(RecipeCatalog.recipe(output, 2,
                List.of(List.of(input)), RecipeCatalog.Station.INVENTORY, 2, 2,
                Map.of(remainder, 1L)));

        var plan = CraftingPlanner.plan(catalog, output, 1, PlanningInventory.empty(), PlanningPolicy.DEFAULT);

        assertEquals(1, plan.missing("example:input"));
        assertTrue(plan.remainders().isEmpty());
        assertTrue(plan.surplusDelta().isEmpty());
        assertEquals(0, plan.remainingInventory().count(remainder));
    }

    @Test
    void laterAllowedRecipeIsEvaluatedAfterDisallowedRecipe() {
        var output = key("example:output");
        var material = key("example:material");
        var catalog = catalog(
                RecipeCatalog.recipe(output, 1, List.of(List.of(material)),
                        RecipeCatalog.Station.CRAFTING_TABLE, 3, 3, Map.of()),
                RecipeCatalog.recipe(output, 1, List.of(List.of(material)),
                        RecipeCatalog.Station.INVENTORY, 2, 2, Map.of()));

        var plan = CraftingPlanner.plan(catalog, output, 1,
                PlanningInventory.of(Map.of(material, 1L)),
                new PlanningPolicy(false, true, 64));

        assertTrue(plan.missing().isEmpty());
        assertEquals(1, plan.root().craftCount());
    }

    @Test
    void partialChildSuccessRollsBackTheWholeCandidate() {
        var output = key("example:output");
        var good = key("example:good");
        var goodInput = key("example:good_input");
        var missing = key("example:missing");
        var goodRemainder = key("example:good_remainder");
        var catalog = catalog(
                recipe("example:output", 1, new String[]{"example:good"}, new String[]{"example:missing"}),
                RecipeCatalog.recipe(good, 2, List.of(List.of(goodInput)), RecipeCatalog.Station.INVENTORY, 2, 2,
                        Map.of(goodRemainder, 1L)));
        var initial = PlanningInventory.of(Map.of(goodInput, 1L));

        var plan = CraftingPlanner.plan(catalog, output, 1, initial, PlanningPolicy.DEFAULT);

        assertEquals(1, plan.missing("example:missing"));
        assertTrue(plan.consumedDelta().isEmpty());
        assertTrue(plan.surplusDelta().isEmpty());
        assertTrue(plan.remainders().isEmpty());
        assertEquals(1, plan.remainingInventory().count(goodInput));
        assertTrue(plan.root().children().isEmpty());
        assertTrue(plan.root().consumed().isEmpty());
        assertTrue(plan.root().generatedSurplus().isEmpty());
        assertTrue(plan.root().generatedRemainders().isEmpty());
        var rows = DisplayPlan.flatten(plan);
        assertEquals(1, rows.size());
        assertEquals(0, rows.getFirst().depth());
        assertEquals(1, rows.getFirst().missing());
    }

    @Test
    void cancellationStopsCandidateEvaluation() {
        var plan = CraftingPlanner.plan(catalog(recipe("example:output", 1, new String[]{"example:input"})),
                key("example:output"), 1, PlanningInventory.empty(), PlanningPolicy.DEFAULT, () -> true);

        assertTrue(plan.cancelled());
        assertEquals(1, plan.missing("example:output"));
    }

    @Test
    void catalogGenerationInvalidatesMemoizationAndInventoryIsPartOfState() {
        var first = catalog(recipe("example:output", 1, new String[]{"example:a"}));
        var second = catalog(recipe("example:output", 1, new String[]{"example:b"}));
        assertNotEquals(first.generation(), second.generation());

        var firstPlan = CraftingPlanner.plan(first, key("example:output"), 1,
                PlanningInventory.of(Map.of(key("example:a"), 1L)), PlanningPolicy.DEFAULT);
        var secondPlan = CraftingPlanner.plan(second, key("example:output"), 1,
                PlanningInventory.of(Map.of(key("example:b"), 1L)), PlanningPolicy.DEFAULT);
        assertTrue(firstPlan.missing().isEmpty());
        assertTrue(secondPlan.missing().isEmpty());
    }
}
