package dev.smpb.findmyitems.craft;

import dev.smpb.findmyitems.model.StackKey;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Pure capacity check for the player storage inventory. */
public final class InventorySimulation {
    private static final int STORAGE_SLOTS = 36;

    private InventorySimulation() {
    }

    public static CapacityResult simulate(PlayerInventorySnapshot snapshot, CraftingPlan plan) {
        var stacks = snapshot.copySlots();
        var surplus = new LinkedHashMap<StackKey, Long>(plan.surplusDelta());

        for (var entry : plan.consumedDelta().entrySet()) {
            if (!remove(stacks, entry.getKey(), entry.getValue())) {
                return unsafe(stacks, 0, surplus, "source " + entry.getKey() + " is not component-compatible");
            }
        }
        for (var entry : plan.remainders().entrySet()) {
            var failure = insert(stacks, entry.getKey(), entry.getValue());
            if (failure != null) return unsafe(stacks, failure, surplus, "remainder " + entry.getKey());
        }
        for (var entry : plan.surplusDelta().entrySet()) {
            var failure = insert(stacks, entry.getKey(), entry.getValue());
            if (failure != null) return unsafe(stacks, failure, surplus, "surplus " + entry.getKey());
        }

        var output = plan.root().item();
        var failure = insert(stacks, output, plan.root().requested());
        if (failure != null) return unsafe(stacks, failure, surplus, "output " + output);
        return new CapacityResult(true, 0, stacks, surplus, "");
    }

    private static CapacityResult unsafe(List<ItemStack> stacks, int required, Map<StackKey, Long> surplus,
                                         String reason) {
        return new CapacityResult(false, required, stacks, surplus, reason);
    }

    private static boolean remove(List<ItemStack> stacks, StackKey key, long amount) {
        var remaining = amount;
        var sawSameItem = false;
        for (var stack : stacks) {
            if (stack.isEmpty() || !itemId(stack).equals(key.itemId())) continue;
            sawSameItem = true;
            if (!matches(stack, key)) continue;
            var removed = (int) Math.min(remaining, stack.getCount());
            stack.shrink(removed);
            remaining -= removed;
            if (remaining == 0) return true;
        }
        return remaining == 0 || !sawSameItem;
    }

    private static Integer insert(List<ItemStack> stacks, StackKey key, long amount) {
        if (amount <= 0) return null;
        var template = template(stacks, key);
        if (template.isEmpty()) return 1;
        var remaining = amount;
        for (var stack : stacks) {
            if (stack.isEmpty() || !ItemStack.isSameItemSameComponents(stack, template)) continue;
            var room = stack.getMaxStackSize() - stack.getCount();
            var added = (int) Math.min(remaining, room);
            stack.grow(added);
            remaining -= added;
            if (remaining == 0) return null;
        }
        for (var index = 0; index < stacks.size() && remaining > 0; index++) {
            if (!stacks.get(index).isEmpty()) continue;
            var added = (int) Math.min(remaining, template.getMaxStackSize());
            var placed = template.copyWithCount(added);
            stacks.set(index, placed);
            remaining -= added;
        }
        return remaining == 0 ? null : (int) ((remaining + template.getMaxStackSize() - 1)
                / template.getMaxStackSize());
    }

    private static ItemStack template(List<ItemStack> stacks, StackKey key) {
        for (var stack : stacks) {
            if (!stack.isEmpty() && matches(stack, key)) return stack.copyWithCount(1);
        }
        try {
            return BuiltInRegistries.ITEM.get(Identifier.parse(key.itemId()))
                    .map(ItemStack::new).orElse(ItemStack.EMPTY);
        } catch (LinkageError ignored) {
            return ItemStack.EMPTY;
        }
    }

    private static boolean matches(ItemStack stack, StackKey key) {
        if (!itemId(stack).equals(key.itemId())) return false;
        if (key.componentsJson().equals("{}")) return stack.getComponents().isEmpty();
        return stack.getComponentsPatch().toString().equals(key.componentsJson());
    }

    private static String itemId(ItemStack stack) {
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
    }

    public record PlayerInventorySnapshot(List<ItemStack> slots) {
        public PlayerInventorySnapshot {
            if (slots.size() != STORAGE_SLOTS) throw new IllegalArgumentException("player storage must have 36 slots");
            slots = slots.stream().map(ItemStack::copy).toList();
        }

        public static PlayerInventorySnapshot of(List<ItemStack> slots) {
            return new PlayerInventorySnapshot(slots);
        }

        @Override
        public List<ItemStack> slots() {
            return copySlots();
        }

        private List<ItemStack> copySlots() {
            return slots.stream().map(ItemStack::copy).toList();
        }
    }

    public record CapacityResult(boolean safe, int requiredFreeSlots, List<ItemStack> finalStacks,
                                 Map<StackKey, Long> surplus, String failureReason) {
        public CapacityResult {
            finalStacks = finalStacks.stream().map(ItemStack::copy).toList();
            surplus = Map.copyOf(surplus);
        }

        @Override
        public List<ItemStack> finalStacks() {
            return finalStacks.stream().map(ItemStack::copy).toList();
        }
    }
}
