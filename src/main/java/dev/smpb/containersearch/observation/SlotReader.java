package dev.smpb.containersearch.observation;

import com.mojang.serialization.JsonOps;
import dev.smpb.containersearch.model.CanonicalJson;
import dev.smpb.containersearch.model.SlotSnapshot;
import dev.smpb.containersearch.model.StackKey;
import dev.smpb.containersearch.model.StackSnapshot;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.ArrayList;
import java.util.List;

public final class SlotReader {
    /** Nested slots get indices from here up, so they never collide with real ones. */
    private static final int NESTED_SLOT_BASE = 10_000;
    /** A shulker in a shulker in a bundle is already absurd; stop there. */
    private static final int MAX_NESTING = 4;

    private SlotReader() {}

    public static List<SlotSnapshot> readContainerSlots(Container container, Player player) {
        var snapshots = new ArrayList<SlotSnapshot>(container.getContainerSize());
        var ctx = tooltipContext(player);
        var nested = new int[]{NESTED_SLOT_BASE};
        for (int i = 0; i < container.getContainerSize(); i++) {
            var stack = container.getItem(i);
            if (stack.isEmpty()) continue;
            snapshots.add(snapshotStack(stack, i, ctx, player));
            addNestedContents(snapshots, stack, ctx, player, nested, 1);
        }
        return List.copyOf(snapshots);
    }

    public static List<SlotSnapshot> readMenuSlots(AbstractContainerMenu menu, int containerSlots, Player player) {
        var snapshots = new ArrayList<SlotSnapshot>(containerSlots);
        var ctx = tooltipContext(player);
        var nested = new int[]{NESTED_SLOT_BASE};
        for (int i = 0; i < containerSlots; i++) {
            var slot = menu.getSlot(i);
            var stack = slot.getItem();
            if (stack.isEmpty()) continue;
            snapshots.add(snapshotStack(stack, i, ctx, player));
            addNestedContents(snapshots, stack, ctx, player, nested, 1);
        }
        return List.copyOf(snapshots);
    }

    /**
     * Indexes what is inside a shulker box (or any item carrying container contents) so that a
     * search finds items stashed in a shulker that itself sits in a chest. The shulker stays in
     * the index as an item in its own right; its contents are added alongside it.
     */
    private static void addNestedContents(List<SlotSnapshot> out, ItemStack stack,
                                          Item.TooltipContext ctx, Player player, int[] nextIndex, int depth) {
        if (depth > MAX_NESTING) return;
        var contents = stack.get(DataComponents.CONTAINER);
        if (contents == null) return;

        for (var inner : (Iterable<ItemStack>) contents.nonEmptyItemCopyStream()::iterator) {
            out.add(snapshotStack(inner, nextIndex[0]++, ctx, player));
            addNestedContents(out, inner, ctx, player, nextIndex, depth + 1);
        }
    }

    private static Item.TooltipContext tooltipContext(Player player) {
        return player != null && player.level() != null
                ? Item.TooltipContext.of(player.level())
                : Item.TooltipContext.EMPTY;
    }

    static SlotSnapshot snapshotStack(ItemStack stack, int slotIndex,
                                      Item.TooltipContext ctx, Player player) {
        var itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        var componentsJson = serializeComponents(stack.getComponentsPatch());
        var key = new StackKey(itemId, componentsJson);
        var count = stack.getCount();
        var displayName = stack.getHoverName().getString();
        var tooltip = getTooltipLines(stack, ctx, player);
        return new SlotSnapshot(slotIndex, new StackSnapshot(key, count, displayName, tooltip));
    }

    public static String serializeComponents(DataComponentPatch patch) {
        if (patch.isEmpty()) return "{}";
        try {
            var json = DataComponentPatch.CODEC
                    .encodeStart(JsonOps.INSTANCE, patch)
                    .getOrThrow();
            return CanonicalJson.stringify(json);
        } catch (Exception e) {
            return "{}";
        }
    }

    static List<String> getTooltipLines(ItemStack stack,
                                        Item.TooltipContext ctx, Player player) {
        try {
            return stack.getTooltipLines(ctx, player, TooltipFlag.NORMAL)
                    .stream()
                    .map(Component::getString)
                    .toList();
        } catch (Exception e) {
            return List.of();
        }
    }
}
