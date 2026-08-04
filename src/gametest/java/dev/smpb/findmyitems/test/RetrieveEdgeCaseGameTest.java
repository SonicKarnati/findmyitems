package dev.smpb.findmyitems.test;

import dev.smpb.findmyitems.observation.SlotReader;
import dev.smpb.findmyitems.retrieval.RetrieveHandler;
import dev.smpb.findmyitems.model.ContainerKind;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.properties.ChestType;

import java.util.List;

/**
 * The awkward cases: containers that are not one block entity, inventories that will not take what
 * is offered, and asks that cannot be satisfied.
 *
 * <p>The invariant every one of these is really checking is conservation — the count of an item in
 * the world before a retrieve or deposit equals the count after. A catalog that eats a stack when
 * your inventory happens to be nearly full is worse than one that refuses.
 */
public final class RetrieveEdgeCaseGameTest {
    private static final String EMPTY_STRUCTURE = "fabric-gametest-api-v1:empty";
    private static final BlockPos CHEST = new BlockPos(1, 1, 1);
    private static final BlockPos FAR_HALF = new BlockPos(2, 1, 1);

    @GameTest(structure = EMPTY_STRUCTURE, maxTicks = 40)
    public void retrieveReachesTheFarHalfOfADoubleChest(GameTestHelper helper) {
        var far = placeDoubleChest(helper);
        // Only the far half is stocked: a single-block-entity lookup sees 27 empty slots here.
        far.setItem(0, new ItemStack(Items.DIAMOND, 40));
        var player = playerNextToChest(helper);

        var took = RetrieveHandler.retrieve(player, helper.absolutePos(CHEST), dimension(helper),
                "minecraft:diamond", "{}", 40);

        helper.assertTrue(took, "a double chest is one container; the far half must be reachable");
        helper.assertTrue(player.getInventory().countItem(Items.DIAMOND) == 40,
                "player should hold 40 diamonds, holds " + player.getInventory().countItem(Items.DIAMOND));
        helper.succeed();
    }

    @GameTest(structure = EMPTY_STRUCTURE, maxTicks = 40)
    public void depositReachesTheFarHalfOfADoubleChest(GameTestHelper helper) {
        var far = placeDoubleChest(helper);
        far.setItem(0, new ItemStack(Items.OAK_LOG, 1));
        var player = playerNextToChest(helper);
        player.getInventory().add(new ItemStack(Items.OAK_LOG, 20));

        var moved = RetrieveHandler.deposit(player, helper.absolutePos(CHEST), "minecraft:oak_log", "{}", 20);

        helper.assertTrue(moved == 20, "should have deposited 20 logs, moved " + moved);
        helper.assertTrue(logsIn(helper) == 21, "the double chest should hold 21 logs, holds " + logsIn(helper));
        helper.succeed();
    }

    @GameTest(structure = EMPTY_STRUCTURE, maxTicks = 40)
    public void retrieveReadsTheEnderChestAsThePlayersOwnItems(GameTestHelper helper) {
        helper.setBlock(CHEST, Blocks.ENDER_CHEST);
        var player = playerNextToChest(helper);
        player.getEnderChestInventory().setItem(0, new ItemStack(Items.DIAMOND, 12));

        var took = RetrieveHandler.retrieve(player, helper.absolutePos(CHEST), dimension(helper),
                "minecraft:diamond", "{}", 12, 0, ContainerKind.ENDER_CHEST);

        helper.assertTrue(took, "an ender chest holds the player's own inventory, not a block entity's");
        helper.assertTrue(player.getInventory().countItem(Items.DIAMOND) == 12,
                "player should hold 12 diamonds, holds " + player.getInventory().countItem(Items.DIAMOND));
        helper.assertTrue(player.getEnderChestInventory().getItem(0).isEmpty(),
                "the ender chest slot should be empty now");
        helper.succeed();
    }

    @GameTest(structure = EMPTY_STRUCTURE, maxTicks = 40)
    public void aFullInventoryLosesNothingOnTheWayBack(GameTestHelper helper) {
        var chest = placeChest(helper, new ItemStack(Items.DIAMOND, 64));
        var player = playerNextToChest(helper);
        fillInventory(player);
        // One slot with room for exactly 2 more diamonds, so the take is partial rather than refused.
        player.getInventory().setItem(0, new ItemStack(Items.DIAMOND, 62));

        RetrieveHandler.retrieve(player, helper.absolutePos(CHEST), dimension(helper),
                "minecraft:diamond", "{}", 64);

        var carried = player.getInventory().countItem(Items.DIAMOND);
        var left = chest.getItem(0).getCount();
        helper.assertTrue(carried + left == 126,
                "126 diamonds existed; " + carried + " carried + " + left + " in the chest do not add up");
        helper.assertTrue(carried == 64, "the free room was 2 diamonds, so the player should hold 64");
        helper.succeed();
    }

    @GameTest(structure = EMPTY_STRUCTURE, maxTicks = 40)
    public void aFullInventoryLosesNothingFromInsideAShulker(GameTestHelper helper) {
        var shulker = new ItemStack(Items.SHULKER_BOX);
        shulker.set(DataComponents.CONTAINER,
                ItemContainerContents.fromItems(List.of(new ItemStack(Items.DIAMOND, 64))));

        var chest = placeChest(helper, shulker);
        var player = playerNextToChest(helper);
        fillInventory(player);
        player.getInventory().setItem(0, new ItemStack(Items.DIAMOND, 62));

        RetrieveHandler.retrieve(player, helper.absolutePos(CHEST), dimension(helper),
                "minecraft:diamond", "{}", 64);

        var contents = chest.getItem(0).get(DataComponents.CONTAINER);
        var left = contents == null ? 0 : contents.nonEmptyItemCopyStream().mapToInt(ItemStack::getCount).sum();
        var carried = player.getInventory().countItem(Items.DIAMOND);
        helper.assertTrue(carried + left == 126,
                "126 diamonds existed; " + carried + " carried + " + left + " in the shulker do not add up");
        helper.succeed();
    }

    @GameTest(structure = EMPTY_STRUCTURE, maxTicks = 40)
    public void depositStopsWhenTheContainerIsFull(GameTestHelper helper) {
        var chest = placeChest(helper, new ItemStack(Items.OAK_LOG, 64));
        for (int slot = 1; slot < chest.getContainerSize(); slot++) {
            chest.setItem(slot, new ItemStack(Items.OAK_LOG, 64));
        }
        var player = playerNextToChest(helper);
        player.getInventory().add(new ItemStack(Items.OAK_LOG, 30));

        var moved = RetrieveHandler.deposit(player, helper.absolutePos(CHEST), "minecraft:oak_log", "{}", 30);

        helper.assertTrue(moved == 0, "a full chest can take nothing, took " + moved);
        helper.assertTrue(player.getInventory().countItem(Items.OAK_LOG) == 30,
                "the logs must stay on the player, kept " + player.getInventory().countItem(Items.OAK_LOG));
        helper.succeed();
    }

    @GameTest(structure = EMPTY_STRUCTURE, maxTicks = 40)
    public void retrieveTakesWhatIsThereWhenAskedForMore(GameTestHelper helper) {
        var chest = placeChest(helper, new ItemStack(Items.DIAMOND, 5));
        var player = playerNextToChest(helper);

        var took = RetrieveHandler.retrieve(player, helper.absolutePos(CHEST), dimension(helper),
                "minecraft:diamond", "{}", 64);

        helper.assertTrue(took, "taking fewer than asked is still a success");
        helper.assertTrue(player.getInventory().countItem(Items.DIAMOND) == 5,
                "player should hold all 5, holds " + player.getInventory().countItem(Items.DIAMOND));
        helper.assertTrue(chest.getItem(0).isEmpty(), "the chest should be empty");
        helper.succeed();
    }

    @GameTest(structure = EMPTY_STRUCTURE, maxTicks = 40)
    public void retrieveFailsWhenTheChestIsGone(GameTestHelper helper) {
        placeChest(helper, new ItemStack(Items.DIAMOND, 64));
        var player = playerNextToChest(helper);
        // Mined between the last index scan and the click — the catalog still lists it.
        helper.setBlock(CHEST, Blocks.AIR);

        var took = RetrieveHandler.retrieve(player, helper.absolutePos(CHEST), dimension(helper),
                "minecraft:diamond", "{}", 1);

        helper.assertTrue(!took, "a chest that no longer exists cannot be retrieved from");
        helper.assertTrue(player.getInventory().countItem(Items.DIAMOND) == 0, "and nothing may appear");
        helper.succeed();
    }

    @GameTest(structure = EMPTY_STRUCTURE, maxTicks = 40)
    public void retrieveStopsAtTheNestingLimit(GameTestHelper helper) {
        // Five shulkers deep: past both the index's reading depth and retrieval's, so it is
        // consistently invisible rather than listed-but-unreachable.
        var buried = new ItemStack(Items.GOLD_INGOT, 5);
        for (int depth = 0; depth < 5; depth++) {
            var box = new ItemStack(Items.SHULKER_BOX);
            box.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(List.of(buried)));
            buried = box;
        }

        placeChest(helper, buried);
        var player = playerNextToChest(helper);

        var took = RetrieveHandler.retrieve(player, helper.absolutePos(CHEST), dimension(helper),
                "minecraft:gold_ingot", "{}", 5);

        helper.assertTrue(!took, "gold five shulkers deep is past the nesting limit");
        helper.assertTrue(player.getInventory().countItem(Items.GOLD_INGOT) == 0, "and must not appear");
        helper.succeed();
    }

    @GameTest(structure = EMPTY_STRUCTURE, maxTicks = 40)
    public void retrievePathMovesThroughMultipleNestedContainersWithoutTouchingCursor(GameTestHelper helper) {
        var player = playerNextToChest(helper);
        var buried = new ItemStack(Items.GOLD_INGOT, 5);
        for (int depth = 0; depth < 3; depth++) {
            var box = new ItemStack(Items.SHULKER_BOX);
            box.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(List.of(buried)));
            buried = box;
        }
        var chest = placeChest(helper, buried);
        player.containerMenu.setCarried(new ItemStack(Items.STICK, 2));
        var beforeGold = chest.getItem(0).get(DataComponents.CONTAINER);

        var moved = RetrieveHandler.retrievePath(player, helper.absolutePos(CHEST), dimension(helper),
                ContainerKind.CHEST, List.of(0, 0, 0, 0), "minecraft:gold_ingot", "{}", 5, 0);

        helper.assertTrue(moved == 5, "all nested gold should move, moved " + moved);
        helper.assertTrue(player.getInventory().countItem(Items.GOLD_INGOT) == 5,
                "player should receive all nested gold");
        helper.assertTrue(player.containerMenu.getCarried().is(Items.STICK)
                        && player.containerMenu.getCarried().getCount() == 2,
                "the menu cursor must be preserved");
        helper.assertTrue(beforeGold != null && !chest.getItem(0).isEmpty(),
                "the outer container must remain present after the transfer");
        helper.succeed();
    }

    @GameTest(structure = EMPTY_STRUCTURE, maxTicks = 40)
    public void retrievePathRejectsPathsBeyondTheNestingLimit(GameTestHelper helper) {
        var player = playerNextToChest(helper);
        var buried = new ItemStack(Items.GOLD_INGOT, 5);
        for (int depth = 0; depth < 5; depth++) {
            var box = new ItemStack(Items.SHULKER_BOX);
            box.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(List.of(buried)));
            buried = box;
        }
        placeChest(helper, buried);

        var moved = RetrieveHandler.retrievePath(player, helper.absolutePos(CHEST), dimension(helper),
                ContainerKind.CHEST, List.of(0, 0, 0, 0, 0, 0), "minecraft:gold_ingot", "{}", 5, 0);

        helper.assertTrue(moved == 0, "retrievePath must reject paths deeper than MAX_NESTING");
        helper.assertTrue(player.getInventory().countItem(Items.GOLD_INGOT) == 0,
                "over-deep nested contents must not be retrieved");
        helper.succeed();
    }

    /**
     * Three diamond swords — plain, Smite IV, Sharpness V — and a take for 64 of the plain one.
     *
     * <p>The enchanted two used to come out with it: their components key failed to encode and
     * silently degraded to a plain stack's {@code "{}"}, so every variant answered to every key.
     */
    @GameTest(structure = EMPTY_STRUCTURE, maxTicks = 40)
    public void retrieveTakesOnlyTheVariantItWasAskedFor(GameTestHelper helper) {
        var player = playerNextToChest(helper);
        var registries = player.registryAccess();

        var plain = new ItemStack(Items.DIAMOND_SWORD);
        var smite = enchanted(helper, Enchantments.SMITE, 4);
        var sharpness = enchanted(helper, Enchantments.SHARPNESS, 5);

        var chest = placeChest(helper, plain);
        chest.setItem(1, smite);
        chest.setItem(2, sharpness);

        var took = RetrieveHandler.retrieve(player, helper.absolutePos(CHEST), dimension(helper),
                "minecraft:diamond_sword",
                SlotReader.serializeComponents(plain.getComponentsPatch(), registries),
                64);

        helper.assertTrue(took, "the plain sword is there and should have been taken");
        helper.assertTrue(player.getInventory().countItem(Items.DIAMOND_SWORD) == 1,
                "only the plain sword may be taken, took "
                        + player.getInventory().countItem(Items.DIAMOND_SWORD));
        helper.assertTrue(chest.getItem(1).getEnchantments().equals(smite.getEnchantments()),
                "the Smite sword must still be in the chest");
        helper.assertTrue(chest.getItem(2).getEnchantments().equals(sharpness.getEnchantments()),
                "the Sharpness sword must still be in the chest");
        helper.succeed();
    }

    /** Asking for the enchanted one must not drag the plain one along either. */
    @GameTest(structure = EMPTY_STRUCTURE, maxTicks = 40)
    public void retrieveFindsTheEnchantedVariantOnItsOwnKey(GameTestHelper helper) {
        var player = playerNextToChest(helper);
        var smite = enchanted(helper, Enchantments.SMITE, 4);

        var chest = placeChest(helper, new ItemStack(Items.DIAMOND_SWORD));
        chest.setItem(1, smite);

        var took = RetrieveHandler.retrieve(player, helper.absolutePos(CHEST), dimension(helper),
                "minecraft:diamond_sword",
                SlotReader.serializeComponents(smite.getComponentsPatch(), player.registryAccess()),
                64);

        helper.assertTrue(took, "the Smite sword should have been taken");
        helper.assertTrue(player.getInventory().countItem(Items.DIAMOND_SWORD) == 1,
                "exactly one sword should have moved");
        helper.assertTrue(!chest.getItem(0).isEmpty(), "the plain sword must stay in the chest");
        helper.succeed();
    }

    /**
     * The gate the catalog's Take button reads. A full inventory has to report zero room, or the
     * click goes through, moves nothing, and looks exactly like success.
     */
    @GameTest(structure = EMPTY_STRUCTURE, maxTicks = 40)
    public void roomForIsZeroWhenEverySlotIsFullOfSomethingElse(GameTestHelper helper) {
        var player = playerNextToChest(helper);
        fillInventory(player);

        helper.assertTrue(RetrieveHandler.roomFor(player, new ItemStack(Items.BOOK)) == 0,
                "an inventory full of stone has no room for a book");
        helper.succeed();
    }

    /** ...but a part-filled stack of the same item is still room, which is what keeps big takes working. */
    @GameTest(structure = EMPTY_STRUCTURE, maxTicks = 40)
    public void roomForCountsPartialStacksOfTheSameItem(GameTestHelper helper) {
        var player = playerNextToChest(helper);
        fillInventory(player);
        player.getInventory().setItem(0, new ItemStack(Items.DIAMOND, 62));

        var room = RetrieveHandler.roomFor(player, new ItemStack(Items.DIAMOND));

        helper.assertTrue(room == 2, "62 of a 64-stack leaves room for 2, reported " + room);
        helper.succeed();
    }

    /** Armour and the offhand are in {@code getContainerSize()} but retrieval can never fill them. */
    @GameTest(structure = EMPTY_STRUCTURE, maxTicks = 40)
    public void roomForIgnoresSlotsRetrievalCannotFill(GameTestHelper helper) {
        var player = playerNextToChest(helper);
        var inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getNonEquipmentItems().size(); slot++) {
            inventory.setItem(slot, new ItemStack(Items.STONE, 64));
        }

        helper.assertTrue(RetrieveHandler.roomFor(player, new ItemStack(Items.BOOK)) == 0,
                "empty armour slots are not room a take can use");
        helper.succeed();
    }

    @GameTest(structure = EMPTY_STRUCTURE, maxTicks = 40)
    public void exactSlotTransferStopsWhenTheIndexedSourceChanges(GameTestHelper helper) {
        var chest = placeChest(helper, new ItemStack(Items.DIAMOND, 12));
        var player = playerNextToChest(helper);
        chest.setItem(0, new ItemStack(Items.OAK_LOG, 12));

        var moved = RetrieveHandler.retrieveSlot(player, helper.absolutePos(CHEST), dimension(helper),
                ContainerKind.CHEST, 0, "minecraft:diamond", "{}", 12, 0);

        helper.assertTrue(moved == 0, "a changed source slot must not satisfy the old plan");
        helper.assertTrue(player.getInventory().countItem(Items.DIAMOND) == 0,
                "stale source validation must not fabricate diamonds");
        helper.assertTrue(chest.getItem(0).is(Items.OAK_LOG), "the replacement stack must stay put");
        helper.succeed();
    }

    @GameTest(structure = EMPTY_STRUCTURE, maxTicks = 40)
    public void exactSlotTransferConservesOverflowInCreative(GameTestHelper helper) {
        var chest = placeChest(helper, new ItemStack(Items.DIAMOND, 12));
        var player = playerNextToChest(helper);
        player.getAbilities().instabuild = true;
        fillInventory(player);

        var moved = RetrieveHandler.retrieveSlot(player, helper.absolutePos(CHEST), dimension(helper),
                ContainerKind.CHEST, 0, "minecraft:diamond", "{}", 12, 0);

        helper.assertTrue(moved == 0, "creative inventory.add must not claim to accept overflow");
        helper.assertTrue(chest.getItem(0).getCount() == 12,
                "creative overflow must remain in the source chest");
        helper.succeed();
    }

    @GameTest(structure = EMPTY_STRUCTURE, maxTicks = 40)
    public void exactNestedPathRefusesAChangedInnerSlot(GameTestHelper helper) {
        var outer = new ItemStack(Items.SHULKER_BOX);
        outer.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(List.of(
                ItemStack.EMPTY, new ItemStack(Items.DIAMOND, 7))));
        var chest = placeChest(helper, outer);
        var player = playerNextToChest(helper);

        var moved = RetrieveHandler.retrievePath(player, helper.absolutePos(CHEST), dimension(helper),
                ContainerKind.CHEST, List.of(0, 1), "minecraft:diamond", "{}", 7, 0);
        helper.assertTrue(moved == 7, "the exact physical nested path should transfer its item");

        chest.getItem(0).set(DataComponents.CONTAINER, ItemContainerContents.fromItems(List.of(
                ItemStack.EMPTY, new ItemStack(Items.OAK_LOG, 7))));
        var stale = RetrieveHandler.retrievePath(player, helper.absolutePos(CHEST), dimension(helper),
                ContainerKind.CHEST, List.of(0, 1), "minecraft:diamond", "{}", 7, 0);
        helper.assertTrue(stale == 0, "a changed nested slot must not satisfy the old path");
        helper.assertTrue(player.getInventory().countItem(Items.DIAMOND) == 7,
                "stale nested retrieval must not fabricate a second stack");
        helper.succeed();
    }

    /**
     * A configured reach opens a chest your arm cannot, and only when it is configured.
     *
     * <p>The two halves are one test on purpose: a reach setting that quietly did nothing and one
     * that quietly applied to everybody look identical from either assertion alone.
     */
    @GameTest(structure = EMPTY_STRUCTURE, maxTicks = 40)
    public void retrieveHonoursTheConfiguredReach(GameTestHelper helper) {
        helper.setBlock(CHEST, Blocks.CHEST);
        helper.getBlockEntity(CHEST, ChestBlockEntity.class).setItem(0, new ItemStack(Items.DIAMOND, 12));
        var player = playerNextToChest(helper);
        var pos = helper.absolutePos(CHEST);
        player.setPos(pos.getX() + 6.5, pos.getY() + 1.0, pos.getZ() + 0.5);

        var refused = RetrieveHandler.retrieve(player, pos, dimension(helper), "minecraft:diamond", "{}", 12);
        helper.assertTrue(!refused, "6 blocks is out of arm's reach; the default must refuse it");
        helper.assertTrue(player.getInventory().countItem(Items.DIAMOND) == 0,
                "a refused retrieval must move nothing");

        var took = RetrieveHandler.retrieve(player, pos, dimension(helper), "minecraft:diamond", "{}", 12, 8);
        helper.assertTrue(took, "an 8 block reach covers a chest 6 blocks away when visible");
        helper.assertTrue(player.getInventory().countItem(Items.DIAMOND) == 12,
                "player should hold 12 diamonds, holds " + player.getInventory().countItem(Items.DIAMOND));

        // The setting raises a ceiling; it never lowers one.
        player.setPos(pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5);
        helper.assertTrue(RetrieveHandler.inReach(player, pos, 1),
                "a reach of 1 must not take away the chest you are standing on");
        helper.succeed();
    }

    // ---------------------------------------------------------------- helpers

    private static ItemStack enchanted(GameTestHelper helper, ResourceKey<Enchantment> enchantment, int level) {
        var stack = new ItemStack(Items.DIAMOND_SWORD);
        var holder = helper.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(enchantment);
        stack.enchant(holder, level);
        return stack;
    }

    /** Two connected chest halves at {@link #CHEST} and {@link #FAR_HALF}. Returns the far one. */
    private static ChestBlockEntity placeDoubleChest(GameTestHelper helper) {
        var left = Blocks.CHEST.defaultBlockState()
                .setValue(ChestBlock.FACING, Direction.NORTH)
                .setValue(ChestBlock.TYPE, ChestType.LEFT);
        helper.setBlock(CHEST, left);
        helper.setBlock(FAR_HALF, left.setValue(ChestBlock.TYPE, ChestType.RIGHT));

        var state = helper.getBlockState(CHEST);
        helper.assertTrue(ChestBlock.getConnectedDirection(state) == Direction.EAST,
                "test setup: the halves must connect, or this is testing nothing");

        return helper.getBlockEntity(FAR_HALF, ChestBlockEntity.class);
    }

    private static int logsIn(GameTestHelper helper) {
        var near = helper.getBlockEntity(CHEST, ChestBlockEntity.class);
        var far = helper.getBlockEntity(FAR_HALF, ChestBlockEntity.class);
        return countLogs(near) + countLogs(far);
    }

    private static int countLogs(ChestBlockEntity chest) {
        var total = 0;
        for (int slot = 0; slot < chest.getContainerSize(); slot++) {
            var stack = chest.getItem(slot);
            if (stack.is(Items.OAK_LOG)) total += stack.getCount();
        }
        return total;
    }

    private static ChestBlockEntity placeChest(GameTestHelper helper, ItemStack contents) {
        helper.setBlock(CHEST, Blocks.CHEST);
        var chest = helper.getBlockEntity(CHEST, ChestBlockEntity.class);
        chest.setItem(0, contents);
        return chest;
    }

    /** Every slot occupied by something that is not what we are about to retrieve. */
    private static void fillInventory(ServerPlayer player) {
        var inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            inventory.setItem(slot, new ItemStack(Items.STONE, 64));
        }
    }

    @SuppressWarnings("removal")
    private static ServerPlayer playerNextToChest(GameTestHelper helper) {
        var player = helper.makeMockServerPlayerInLevel();
        var pos = helper.absolutePos(CHEST);
        player.setPos(pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5);
        return player;
    }

    private static String dimension(GameTestHelper helper) {
        return helper.getLevel().dimension().identifier().toString();
    }
}
