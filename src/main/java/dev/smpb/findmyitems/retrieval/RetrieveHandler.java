package dev.smpb.findmyitems.retrieval;

import dev.smpb.findmyitems.observation.SlotReader;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.EnderChestBlock;

import java.util.ArrayList;

public final class RetrieveHandler {
    /** Matches {@link dev.smpb.findmyitems.observation.SlotReader}'s indexing depth. */
    private static final int MAX_NESTING = 4;
    /** Extra slack on top of vanilla block reach, in blocks. */
    private static final double REACH_PADDING = 1.0;

    private RetrieveHandler() {}

    public static boolean retrieve(
            ServerPlayer player,
            BlockPos pos,
            String dimensionId,
            String itemId,
            String componentsJson,
            int amount
    ) {
        if (!inReach(player, pos)) return false;

        var container = containerAt(player, pos);
        if (container == null) return false;

        var remaining = amount;
        for (int i = 0; i < container.getContainerSize() && remaining > 0; i++) {
            var stack = container.getItem(i);
            if (stack.isEmpty()) continue;

            if (!matches(player, stack, itemId, componentsJson)) continue;

            // Offer a copy and only shrink the real stack by what landed. Splitting first and
            // growing the remainder back means briefly leaving a count-0 stack in the container.
            var toTake = Math.min(remaining, stack.getCount());
            var moved = toTake - give(player, stack.copyWithCount(toTake));
            if (moved > 0) {
                stack.shrink(moved);
                container.setChanged();
                remaining -= moved;
            }
            if (moved < toTake) return remaining < amount;
        }

        // Anything still owed may be sitting inside a shulker box in this container: the index
        // reports those items, so retrieval has to be able to reach them too.
        for (int i = 0; i < container.getContainerSize() && remaining > 0; i++) {
            remaining -= takeFromNested(player, container.getItem(i), itemId, componentsJson, remaining, 1);
        }
        if (remaining < amount) container.setChanged();

        return remaining < amount;
    }

    /**
     * Moves up to {@code amount} of an item from the player's inventory into the container.
     *
     * <p>Deliberately narrow: the container must already hold that exact item, components and all.
     * This is "put the rest of the wood back where the wood lives", not a general stash-anything
     * button — the mod has no idea where a thing you have never stored belongs, and guessing would
     * scatter your inventory across the nearest chest.
     *
     * @return how many items moved; 0 if the container does not stock this item or is full
     */
    public static int deposit(
            ServerPlayer player,
            BlockPos pos,
            String itemId,
            String componentsJson,
            int amount
    ) {
        if (!inReach(player, pos)) return 0;

        var container = containerAt(player, pos);
        if (container == null) return 0;
        if (!alreadyStocks(player, container, itemId, componentsJson)) return 0;

        var inventory = player.getInventory();
        var moved = 0;

        for (int slot = 0; slot < inventory.getContainerSize() && moved < amount; slot++) {
            var held = inventory.getItem(slot);
            if (held.isEmpty() || !matches(player, held, itemId, componentsJson)) continue;

            var offered = Math.min(amount - moved, held.getCount());
            var accepted = insert(container, held, offered);
            if (accepted == 0) continue;

            held.shrink(accepted);
            moved += accepted;
        }

        if (moved > 0) {
            container.setChanged();
            inventory.setChanged();
        }
        return moved;
    }

    /**
     * Hands a stack to the player and reports how many did not fit, so the caller can leave those
     * where they were.
     *
     * <p>Counted rather than taken from {@code add}'s own bookkeeping, which cannot be trusted for
     * this. It reports success when it placed <em>any</em> of the stack, and in creative mode it
     * zeroes the leftover outright — items are free there, so vanilla drops them on the floor of
     * the JVM. Believing either would delete the overflow out of the chest every time a nearly-full
     * inventory asks for a big stack.
     */
    private static int give(ServerPlayer player, ItemStack stack) {
        var offered = stack.getCount();
        var probe = stack.copy();

        var before = countMatching(player, probe);
        player.getInventory().add(stack);
        var placed = countMatching(player, probe) - before;

        return offered - placed;
    }

    /**
     * How many of {@code stack} the player's inventory could accept right now.
     *
     * <p>Deliberately per-item rather than a free-slot count: an inventory packed with dragon eggs
     * still has room for more dragon eggs if a stack is part-full, and a caller that only asked
     * "is any slot empty?" would refuse a take that would have worked perfectly.
     *
     * <p>Only the 36 storage slots count. Armour and the offhand are in {@code getContainerSize()}
     * but {@link net.minecraft.world.entity.player.Inventory#add} will never place into them, so
     * counting them promises room that no retrieval can use.
     */
    public static int roomFor(Player player, ItemStack stack) {
        if (stack.isEmpty()) return 0;

        var room = 0;
        for (var slot : player.getInventory().getNonEquipmentItems()) {
            if (slot.isEmpty()) {
                room += stack.getMaxStackSize();
            } else if (ItemStack.isSameItemSameComponents(slot, stack)) {
                room += Math.max(0, slot.getMaxStackSize() - slot.getCount());
            }
        }
        return room;
    }

    private static int countMatching(ServerPlayer player, ItemStack probe) {
        var inventory = player.getInventory();
        var total = 0;
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            var held = inventory.getItem(slot);
            if (ItemStack.isSameItemSameComponents(held, probe)) total += held.getCount();
        }
        return total;
    }

    /**
     * The container a player right-clicking this block would actually get.
     *
     * <p>Not simply the block entity. A double chest's block entity is one half of it, holding 27
     * of the 54 slots the index recorded, so half the chest was unreachable. An ender chest's block
     * entity is only the lid animation — the items live on the player.
     *
     * @return null if there is nothing here to take from
     */
    private static Container containerAt(ServerPlayer player, BlockPos pos) {
        var world = player.level();
        var state = world.getBlockState(pos);
        var block = state.getBlock();

        if (block instanceof EnderChestBlock) return player.getEnderChestInventory();
        if (block instanceof ChestBlock chest) return ChestBlock.getContainer(chest, state, world, pos, true);

        var blockEntity = world.getBlockEntity(pos);
        return blockEntity instanceof Container container && !blockEntity.isRemoved() ? container : null;
    }

    /** Fills existing partial stacks first, then empty slots. Returns how many were taken. */
    private static int insert(Container container, ItemStack source, int offered) {
        var placed = 0;

        for (int i = 0; i < container.getContainerSize() && placed < offered; i++) {
            var existing = container.getItem(i);
            if (existing.isEmpty() || !ItemStack.isSameItemSameComponents(existing, source)) continue;
            var room = Math.min(container.getMaxStackSize(existing), existing.getMaxStackSize()) - existing.getCount();
            if (room <= 0) continue;
            var take = Math.min(room, offered - placed);
            existing.grow(take);
            placed += take;
        }

        for (int i = 0; i < container.getContainerSize() && placed < offered; i++) {
            if (!container.getItem(i).isEmpty()) continue;
            var copy = source.copy();
            var take = Math.min(offered - placed, Math.min(container.getMaxStackSize(copy), copy.getMaxStackSize()));
            copy.setCount(take);
            container.setItem(i, copy);
            placed += take;
        }
        return placed;
    }

    private static boolean alreadyStocks(Player player, Container container, String itemId, String componentsJson) {
        for (int i = 0; i < container.getContainerSize(); i++) {
            var stack = container.getItem(i);
            if (!stack.isEmpty() && matches(player, stack, itemId, componentsJson)) return true;
        }
        return false;
    }

    /**
     * Same item and same components — a Sharpness sword is not the same kind as a plain one.
     *
     * <p>The registries have to come from the player: without them the enchantment codec fails and
     * every enchanted stack answers to the plain stack's key, so one Take empties out every variant.
     */
    private static boolean matches(Player player, ItemStack stack, String itemId, String componentsJson) {
        if (!BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().equals(itemId)) return false;
        return SlotReader.serializeComponents(stack.getComponentsPatch(), SlotReader.registriesOf(player))
                .equals(componentsJson);
    }

    /** Pulls up to {@code wanted} of {@code itemId} out of a stack's container contents. Returns how many moved. */
    private static int takeFromNested(ServerPlayer player, ItemStack holder, String itemId,
                                      String componentsJson, int wanted, int depth) {
        if (depth > MAX_NESTING || wanted <= 0) return 0;
        var contents = holder.get(DataComponents.CONTAINER);
        if (contents == null) return 0;

        var items = new ArrayList<>(contents.nonEmptyItemCopyStream().toList());
        var moved = 0;

        for (var inner : items) {
            if (moved >= wanted) break;
            if (!matches(player, inner, itemId, componentsJson)) continue;

            var toTake = Math.min(wanted - moved, inner.getCount());
            var took = toTake - give(player, inner.copyWithCount(toTake));
            inner.shrink(took);
            moved += took;
            if (took < toTake) break;
        }

        for (var inner : items) {
            if (moved >= wanted) break;
            moved += takeFromNested(player, inner, itemId, componentsJson, wanted - moved, depth + 1);
        }

        if (moved > 0) {
            items.removeIf(ItemStack::isEmpty);
            holder.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(items));
        }
        return moved;
    }

    /**
     * Vanilla's own reach test, plus a block of slack.
     *
     * <p>The previous check measured feet-to-block-centre, which reads as roughly a block shorter
     * than the reach a player actually has: vanilla measures eye position to the nearest point of
     * the block's box. Deferring to {@link Player#isWithinBlockInteractionRange} fixes that at the
     * source, and {@link #REACH_PADDING} then buys back a little more so a chest you can plainly
     * click is never refused by the catalog.
     */
    public static boolean inReach(Player player, BlockPos pos) {
        return player.isWithinBlockInteractionRange(pos, REACH_PADDING);
    }

    public static int defaultAmount(String itemId) {
        var id = net.minecraft.resources.Identifier.parse(itemId);
        var itemHolder = BuiltInRegistries.ITEM.get(id);
        if (itemHolder.isEmpty()) return 1;
        var stack = new net.minecraft.world.item.ItemStack(itemHolder.get());
        return stack.getMaxStackSize();
    }
}
