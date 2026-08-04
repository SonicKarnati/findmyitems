package dev.smpb.findmyitems.craft;

import dev.smpb.findmyitems.model.StackKey;
import dev.smpb.findmyitems.observation.SlotReader;
import net.minecraft.core.HolderLookup;
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
        var stacks = new ArrayList<>(snapshot.copySlots());
        var keys = new ArrayList<>(snapshot.keys());
        var surplus = new LinkedHashMap<StackKey, Long>(plan.surplusDelta());

        for (var entry : plan.consumedDelta().entrySet()) {
            if (!remove(stacks, keys, entry.getKey(), entry.getValue())) {
                return unsafe(stacks, 0, surplus, "source " + entry.getKey() + " is absent or incompatible");
            }
        }
        for (var entry : plan.remainders().entrySet()) {
            var failure = insert(stacks, keys, snapshot.templates(), entry.getKey(), entry.getValue());
            if (failure != null) return unsafe(stacks, failure, surplus, "remainder " + entry.getKey());
        }
        for (var entry : plan.surplusDelta().entrySet()) {
            var failure = insert(stacks, keys, snapshot.templates(), entry.getKey(), entry.getValue());
            if (failure != null) return unsafe(stacks, failure, surplus, "surplus " + entry.getKey());
        }

        var output = plan.root().item();
        var failure = insert(stacks, keys, snapshot.templates(), output, plan.root().requested());
        if (failure != null) return unsafe(stacks, failure, surplus, "output " + output);
        return new CapacityResult(true, 0, stacks, surplus, "");
    }

    private static CapacityResult unsafe(List<ItemStack> stacks, int required, Map<StackKey, Long> surplus,
                                         String reason) {
        return new CapacityResult(false, required, stacks, surplus, reason);
    }

    private static boolean remove(List<ItemStack> stacks, List<StackKey> keys, StackKey key, long amount) {
        var remaining = amount;
        for (var index = 0; index < stacks.size(); index++) {
            var stack = stacks.get(index);
            if (stack.isEmpty() || !key.equals(keys.get(index))) continue;
            var removed = (int) Math.min(remaining, stack.getCount());
            stack.shrink(removed);
            remaining -= removed;
            if (remaining == 0) return true;
        }
        return remaining == 0;
    }

    private static Integer insert(List<ItemStack> stacks, List<StackKey> keys, Map<StackKey, ItemStack> templates,
                                  StackKey key, long amount) {
        if (amount <= 0) return null;
        var template = template(stacks, keys, templates, key);
        if (template.isEmpty()) return 1;
        var remaining = amount;
        for (var index = 0; index < stacks.size(); index++) {
            var stack = stacks.get(index);
            if (stack.isEmpty() || !key.equals(keys.get(index))) continue;
            var room = stack.getMaxStackSize() - stack.getCount();
            var added = (int) Math.min(remaining, room);
            stack.grow(added);
            remaining -= added;
            if (remaining == 0) return null;
        }
        for (var index = 0; index < stacks.size() && remaining > 0; index++) {
            if (!stacks.get(index).isEmpty()) continue;
            var added = (int) Math.min(remaining, template.getMaxStackSize());
            stacks.set(index, template.copyWithCount(added));
            keys.set(index, key);
            remaining -= added;
        }
        return remaining == 0 ? null : (int) ((remaining + template.getMaxStackSize() - 1)
                / template.getMaxStackSize());
    }

    private static ItemStack template(List<ItemStack> stacks, List<StackKey> keys, Map<StackKey, ItemStack> templates,
                                      StackKey key) {
        for (var index = 0; index < stacks.size(); index++) {
            if (!stacks.get(index).isEmpty() && key.equals(keys.get(index))) {
                return stacks.get(index).copyWithCount(1);
            }
        }
        var supplied = templates.get(key);
        if (supplied != null) return supplied.copyWithCount(1);
        if (!key.componentsJson().equals("{}")) return ItemStack.EMPTY;
        try {
            return BuiltInRegistries.ITEM.get(Identifier.parse(key.itemId()))
                    .map(ItemStack::new).orElse(ItemStack.EMPTY);
        } catch (LinkageError ignored) {
            return ItemStack.EMPTY;
        }
    }

    private static String itemId(ItemStack stack) {
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
    }

    public record PlayerInventorySnapshot(List<ItemStack> slots, List<StackKey> keys,
                                          Map<StackKey, ItemStack> templates) {
        public PlayerInventorySnapshot(List<ItemStack> slots) {
            this(slots, plainKeys(slots), Map.of());
        }

        public PlayerInventorySnapshot(List<ItemStack> slots, List<StackKey> keys) {
            this(slots, keys, Map.of());
        }

        public PlayerInventorySnapshot {
            if (slots.size() != STORAGE_SLOTS) throw new IllegalArgumentException("player storage must have 36 slots");
            if (keys.size() != STORAGE_SLOTS) throw new IllegalArgumentException("snapshot keys must have 36 slots");
            slots = slots.stream().map(ItemStack::copy).toList();
            keys = java.util.Collections.unmodifiableList(new ArrayList<>(keys));
            templates = templates.entrySet().stream().collect(java.util.stream.Collectors.toUnmodifiableMap(
                    Map.Entry::getKey, entry -> entry.getValue().copyWithCount(1)));
        }

        public static PlayerInventorySnapshot of(List<ItemStack> slots) {
            return new PlayerInventorySnapshot(slots);
        }

        public static PlayerInventorySnapshot of(List<ItemStack> slots, List<StackKey> keys) {
            return new PlayerInventorySnapshot(slots, keys);
        }

        public static PlayerInventorySnapshot of(List<ItemStack> slots, List<StackKey> keys,
                                                 Map<StackKey, ItemStack> templates) {
            return new PlayerInventorySnapshot(slots, keys, templates);
        }

        public static PlayerInventorySnapshot of(List<ItemStack> slots, HolderLookup.Provider registries) {
            var keys = slots.stream().map(stack -> stack.isEmpty() ? null : key(stack, registries)).toList();
            return new PlayerInventorySnapshot(slots, keys);
        }

        @Override
        public List<ItemStack> slots() {
            return copySlots();
        }

        private List<ItemStack> copySlots() {
            return slots.stream().map(ItemStack::copy).toList();
        }

        private static List<StackKey> plainKeys(List<ItemStack> slots) {
            if (slots.stream().anyMatch(stack -> !stack.isEmpty())) {
                throw new IllegalArgumentException("registry access is required for non-empty inventory snapshots");
            }
            return java.util.Collections.unmodifiableList(new ArrayList<>(java.util.Collections.nCopies(
                    slots.size(), (StackKey) null)));
        }

        private static StackKey key(ItemStack stack, HolderLookup.Provider registries) {
            return new StackKey(itemId(stack), SlotReader.serializeComponents(stack.getComponentsPatch(), registries));
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
