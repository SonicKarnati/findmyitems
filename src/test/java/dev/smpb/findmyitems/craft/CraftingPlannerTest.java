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
        var catalog = catalog(recipe("example:torch", 4, new String[]{"minecraft:coal", "minecraft:stick"}));
        var plan = CraftingPlanner.plan(catalog, key("example:torch"), 5,
                PlanningInventory.empty(), PlanningPolicy.DEFAULT);

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
}
