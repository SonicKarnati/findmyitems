package dev.smpb.containersearch.test;

import dev.smpb.containersearch.retrieval.RetrieveHandler;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemContainerContents;
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
                "minecraft:diamond", "{}", 12);

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

    // ---------------------------------------------------------------- helpers

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
