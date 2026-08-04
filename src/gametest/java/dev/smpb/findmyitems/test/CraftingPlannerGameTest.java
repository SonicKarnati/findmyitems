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
}
