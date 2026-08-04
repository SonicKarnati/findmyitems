package dev.smpb.findmyitems.test;

import dev.smpb.findmyitems.craft.CraftingPlan;
import dev.smpb.findmyitems.craft.InventorySimulation;
import dev.smpb.findmyitems.craft.PlanScore;
import dev.smpb.findmyitems.craft.PlanningInventory;
import dev.smpb.findmyitems.craft.RecipeCatalog;
import dev.smpb.findmyitems.model.StackKey;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public final class InventorySimulationGameTest {
    private static final String EMPTY_STRUCTURE = "fabric-gametest-api-v1:empty";

    private static StackKey key(String id) {
        return new StackKey(id, "{}");
    }

    private static CraftingPlan plan(StackKey output, long requested, Map<StackKey, Long> consumed,
                                     Map<StackKey, Long> surplus, Map<StackKey, Long> remainders) {
        return CraftingPlan.of(CraftingPlan.node(output, requested, 0, 1, List.of(), consumed, surplus,
                        remainders, null, new PlanScore(0, 0, 0, 0, 0)), PlanningInventory.empty(), consumed,
                surplus, Map.of(), remainders, new PlanScore(0, 0, 0, 0, 0), 0, false);
    }

    private static InventorySimulation.PlayerInventorySnapshot snapshot(List<ItemStack> stacks,
                                                                         List<StackKey> keys,
                                                                         Map<StackKey, ItemStack> templates,
                                                                         GameTestHelper helper) {
        return InventorySimulation.PlayerInventorySnapshot.of(stacks, keys, templates,
                helper.getLevel().registryAccess());
    }

    private static List<ItemStack> emptySlots() {
        return new ArrayList<>(Collections.nCopies(36, ItemStack.EMPTY));
    }

    @GameTest(structure = EMPTY_STRUCTURE)
    public void partialStackRemainderSurplusAndConservation(GameTestHelper helper) {
        var input = key("minecraft:diamond");
        var output = key("minecraft:emerald");
        var remainder = key("minecraft:bucket");
        var stacks = emptySlots();
        stacks.set(0, new ItemStack(Items.EMERALD, 60));
        stacks.set(1, new ItemStack(Items.DIAMOND));
        var keys = new ArrayList<StackKey>(Collections.nCopies(36, null));
        keys.set(0, output);
        keys.set(1, input);
        var result = InventorySimulation.simulate(snapshot(stacks, keys, Map.of(
                output, new ItemStack(Items.EMERALD), remainder, new ItemStack(Items.BUCKET)), helper),
                plan(output, 10, Map.of(input, 1L), Map.of(output, 3L), Map.of(remainder, 1L)));

        helper.assertTrue(result.safe(), result.failureReason());
        helper.assertTrue(result.finalStacks().get(0).getCount() == 64, "partial stack was not filled");
        helper.assertTrue(result.finalStacks().stream().anyMatch(stack -> stack.is(Items.EMERALD) && stack.getCount() == 9),
                "requested output was not conserved");
        helper.assertTrue(result.finalStacks().stream().anyMatch(stack -> stack.is(Items.BUCKET)), "remainder was lost");
        helper.assertTrue(result.surplus().get(output) == 3L, "batch surplus was lost");
        helper.succeed();
    }

    @GameTest(structure = EMPTY_STRUCTURE)
    public void absentAndIncompatibleSourcesAreUnsafe(GameTestHelper helper) {
        var output = key("minecraft:emerald");
        var absent = key("minecraft:diamond");
        var stacks = emptySlots();
        var keys = new ArrayList<StackKey>(Collections.nCopies(36, null));
        var result = InventorySimulation.simulate(snapshot(stacks, keys, Map.of(output, new ItemStack(Items.EMERALD)), helper),
                plan(output, 1, Map.of(absent, 1L), Map.of(), Map.of()));
        helper.assertTrue(!result.safe() && result.failureReason().contains("source"), "absent source was accepted");

        var enchanted = new ItemStack(Items.DIAMOND_SWORD);
        enchanted.enchant(helper.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT)
                .getOrThrow(Enchantments.SHARPNESS), 5);
        var enchantedKey = RecipeCatalog.stackKey(enchanted, helper.getLevel());
        stacks.set(0, enchanted);
        keys.set(0, enchantedKey);
        var incompatible = InventorySimulation.simulate(snapshot(stacks, keys, Map.of(output, new ItemStack(Items.EMERALD)), helper),
                plan(output, 1, Map.of(key("minecraft:diamond_sword"), 1L), Map.of(), Map.of()));
        helper.assertTrue(!incompatible.safe(), "incompatible component source was accepted");
        helper.succeed();
    }

    @GameTest(structure = EMPTY_STRUCTURE)
    public void fullInventoryNeedsAFreeFinalOutputSlot(GameTestHelper helper) {
        var output = key("minecraft:emerald");
        var filler = key("minecraft:cobblestone");
        var stacks = new ArrayList<ItemStack>();
        var keys = new ArrayList<StackKey>();
        for (var index = 0; index < 36; index++) {
            stacks.add(new ItemStack(Items.COBBLESTONE, 64));
            keys.add(filler);
        }
        var result = InventorySimulation.simulate(snapshot(stacks, keys, Map.of(output, new ItemStack(Items.EMERALD)), helper),
                plan(output, 1, Map.of(), Map.of(), Map.of()));
        helper.assertTrue(!result.safe(), "full inventory was accepted");
        helper.assertTrue(result.requiredFreeSlots() == 1, "wrong free slot count");
        helper.succeed();
    }

    @GameTest(structure = EMPTY_STRUCTURE)
    public void mismatchedTemplateCannotSatisfyRequestedKey(GameTestHelper helper) {
        var output = key("minecraft:emerald");
        var rejected = false;
        try {
            InventorySimulation.PlayerInventorySnapshot.of(emptySlots(),
                    new ArrayList<>(Collections.nCopies(36, null)),
                    Map.of(output, new ItemStack(Items.DIAMOND)), helper.getLevel().registryAccess());
        } catch (IllegalArgumentException expected) {
            rejected = true;
        }
        helper.assertTrue(rejected, "mismatched template was trusted");
        helper.succeed();
    }

    @GameTest(structure = EMPTY_STRUCTURE)
    public void bottleRemainderIsConserved(GameTestHelper helper) {
        var potion = new ItemStack(Items.POTION);
        var input = RecipeCatalog.stackKey(potion, helper.getLevel());
        var output = key("minecraft:emerald");
        var bottle = key("minecraft:glass_bottle");
        var stacks = emptySlots();
        stacks.set(0, potion);
        var keys = new ArrayList<StackKey>(Collections.nCopies(36, null));
        keys.set(0, input);
        var result = InventorySimulation.simulate(snapshot(stacks, keys, Map.of(
                output, new ItemStack(Items.EMERALD), bottle, new ItemStack(Items.GLASS_BOTTLE)), helper),
                plan(output, 1, Map.of(input, 1L), Map.of(), Map.of(bottle, 1L)));

        helper.assertTrue(result.safe(), result.failureReason());
        helper.assertTrue(result.finalStacks().stream().anyMatch(stack -> stack.is(Items.GLASS_BOTTLE)),
                "bottle remainder was lost");
        helper.assertTrue(result.finalStacks().stream().mapToInt(ItemStack::getCount).sum() == 2,
                "potion to output plus bottle was not conserved");
        helper.succeed();
    }
}
