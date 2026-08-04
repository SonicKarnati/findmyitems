package dev.smpb.findmyitems.test;

import dev.smpb.findmyitems.craft.CraftingPlan;
import dev.smpb.findmyitems.craft.CraftingPlanner;
import dev.smpb.findmyitems.craft.PlanningInventory;
import dev.smpb.findmyitems.craft.PlanningPolicy;
import dev.smpb.findmyitems.craft.RecipeCatalog;
import dev.smpb.findmyitems.model.StackKey;
import dev.smpb.findmyitems.retrieval.Reachability;
import dev.smpb.findmyitems.retrieval.TargetKind;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

import java.util.Map;
import java.util.List;

/** Planner/execution preconditions that must hold before a client may press Gather and craft. */
public final class CraftingPlannerGameTest {
    private static final String EMPTY_STRUCTURE = "fabric-gametest-api-v1:empty";
    private static final BlockPos TABLE = new BlockPos(1, 1, 1);

    @GameTest(structure = EMPTY_STRUCTURE, maxTicks = 40)
    public void diamondPickaxeNeedsAReachableTable(GameTestHelper helper) {
        helper.setBlock(TABLE, Blocks.CRAFTING_TABLE);
        var player = helper.makeMockServerPlayerInLevel();
        player.setPos(helper.absolutePos(TABLE).getX() + 0.5, helper.absolutePos(TABLE).getY() + 1,
                helper.absolutePos(TABLE).getZ() + 0.5);
        var reachable = Reachability.check(helper.getLevel(), player, helper.absolutePos(TABLE),
                helper.getLevel().dimension().identifier().toString(), TargetKind.CRAFTING_TABLE, 0);
        helper.assertTrue(reachable.actionable(), "a nearby table must be usable by the executor");
        helper.succeed();
    }

    @GameTest(structure = EMPTY_STRUCTURE, maxTicks = 40)
    public void diamondPickaxeHasNoCraftOutputWithoutItsMaterials(GameTestHelper helper) {
        var catalog = RecipeCatalog.from(helper.getLevel().getServer().getRecipeManager(), helper.getLevel());
        var output = new StackKey("minecraft:diamond_pickaxe", "{}");
        var plan = CraftingPlanner.plan(catalog, output, 1, PlanningInventory.of(Map.of(
                new StackKey("minecraft:stick", "{}"), 2L)), PlanningPolicy.DEFAULT);
        helper.assertTrue(plan.missing().values().stream().mapToLong(Long::longValue).sum() > 0,
                "missing diamonds must remain missing");
        helper.succeed();
    }

    @GameTest(structure = EMPTY_STRUCTURE, maxTicks = 40)
    public void aTableOutsideReachCannotFabricateThePickaxe(GameTestHelper helper) {
        helper.setBlock(TABLE, Blocks.CRAFTING_TABLE);
        var player = helper.makeMockServerPlayerInLevel();
        var pos = helper.absolutePos(TABLE);
        player.setPos(pos.getX() + 40.5, pos.getY() + 1, pos.getZ() + 0.5);
        var reachable = Reachability.check(helper.getLevel(), player, pos,
                helper.getLevel().dimension().identifier().toString(), TargetKind.CRAFTING_TABLE, 0);
        helper.assertTrue(!reachable.actionable(), "an unreachable table must stop execution");
        helper.assertTrue(player.getInventory().countItem(Items.DIAMOND_PICKAXE) == 0,
                "no table means no fabricated pickaxe");
        helper.succeed();
    }

    @GameTest(structure = EMPTY_STRUCTURE, maxTicks = 40)
    public void plannerExpandsMultipleRootDepths(GameTestHelper helper) {
        var leaf = key("example:leaf");
        var middle = key("example:middle");
        var root = key("example:root");
        var catalog = RecipeCatalog.of(List.of(
                RecipeCatalog.recipe(root, 1, List.of(List.of(middle))),
                RecipeCatalog.recipe(middle, 1, List.of(List.of(leaf)))));
        var plan = CraftingPlanner.plan(catalog, root, 1,
                PlanningInventory.of(Map.of(leaf, 1L)), PlanningPolicy.DEFAULT);

        helper.assertTrue(plan.missing().isEmpty(), "nested recipe should be covered by the leaf stock");
        helper.assertTrue(plan.root().children().size() == 1
                        && plan.root().children().getFirst().children().size() == 1,
                "planner should retain both recipe depths: " + plan.root());
        helper.succeed();
    }

    @GameTest(structure = EMPTY_STRUCTURE, maxTicks = 40)
    public void plannerStopsStronglyConnectedRecipeCycles(GameTestHelper helper) {
        var a = key("example:a");
        var b = key("example:b");
        var c = key("example:c");
        var catalog = RecipeCatalog.of(List.of(
                RecipeCatalog.recipe(a, 1, List.of(List.of(b))),
                RecipeCatalog.recipe(b, 1, List.of(List.of(c))),
                RecipeCatalog.recipe(c, 1, List.of(List.of(a)))));
        var plan = CraftingPlanner.plan(catalog, a, 1, PlanningInventory.empty(), PlanningPolicy.DEFAULT);

        helper.assertTrue(plan.missing(a.itemId()) == 1 && plan.flattenedItemIds().size() < 10,
                "cycle should terminate as a missing root: " + plan.flattenedItemIds());
        helper.succeed();
    }

    @GameTest(structure = EMPTY_STRUCTURE, maxTicks = 40)
    public void plannerSharesStockAcrossSiblingBranchesWithoutDoubleClaiming(GameTestHelper helper) {
        var plank = key("minecraft:oak_planks");
        var catalog = RecipeCatalog.of(List.of(
                RecipeCatalog.recipe(key("example:root"), 1,
                        List.of(List.of(key("example:left")), List.of(key("example:right")))),
                RecipeCatalog.recipe(key("example:left"), 1,
                        List.of(List.of(plank), List.of(plank), List.of(plank), List.of(plank))),
                RecipeCatalog.recipe(key("example:right"), 1,
                        List.of(List.of(plank), List.of(plank), List.of(plank), List.of(plank)))));
        var plan = CraftingPlanner.plan(catalog, key("example:root"), 1,
                PlanningInventory.of(Map.of(plank, 4L)), PlanningPolicy.DEFAULT);

        helper.assertTrue(plan.missing(plank.itemId()) == 4,
                "shared stock must not be claimed by both sibling branches: " + plan.missing());
        helper.succeed();
    }

    @GameTest(structure = EMPTY_STRUCTURE, maxTicks = 40)
    public void plannerReportsBatchSurplus(GameTestHelper helper) {
        var torch = key("example:torch");
        var coal = key("minecraft:coal");
        var stick = key("minecraft:stick");
        var catalog = RecipeCatalog.of(List.of(RecipeCatalog.recipe(torch, 4,
                List.of(List.of(coal), List.of(stick)))));
        var plan = CraftingPlanner.plan(catalog, torch, 5,
                PlanningInventory.of(Map.of(coal, 2L, stick, 2L)), PlanningPolicy.DEFAULT);

        helper.assertTrue(plan.missing().isEmpty() && plan.root().craftCount() == 2
                        && plan.generatedSurplus(torch) == 3,
                "batch output must retain surplus: " + plan.generatedSurplus(torch));
        helper.succeed();
    }

    private static StackKey key(String id) {
        return new StackKey(id, "{}");
    }
}
