package dev.smpb.findmyitems.test;

import dev.smpb.findmyitems.index.InMemoryContainerIndex;
import dev.smpb.findmyitems.craft.CraftingPlanner;
import dev.smpb.findmyitems.craft.PlanningInventory;
import dev.smpb.findmyitems.craft.PlanningPolicy;
import dev.smpb.findmyitems.craft.RecipeCatalog;
import dev.smpb.findmyitems.model.StackKey;
import dev.smpb.findmyitems.model.ContainerKind;
import dev.smpb.findmyitems.model.ContainerObservation;
import dev.smpb.findmyitems.model.BlockPosition;
import dev.smpb.findmyitems.model.SourceKey;
import dev.smpb.findmyitems.observation.SlotReader;
import dev.smpb.findmyitems.retrieval.RetrieveHandler;
import dev.smpb.findmyitems.retrieval.Reachability;
import dev.smpb.findmyitems.retrieval.TargetKind;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Server-side game tests: real level, real block entities, real player inventory.
 * Run with {@code ./gradlew runGameTest} (headless, no window).
 *
 * <p>These cover the parts of the mod that touch the world. The client-side flow
 * (interaction -> index -> catalog screen) lives in {@link FindMyItemsClientGameTest}.
 */
public final class FindMyItemsGameTest {
    private static final String EMPTY_STRUCTURE = "fabric-gametest-api-v1:empty";
    private static final BlockPos CHEST = new BlockPos(1, 1, 1);

    @GameTest(structure = EMPTY_STRUCTURE, maxTicks = 40)
    public void liveRecipeCatalogReturnsSelectedCraftingRemainders(GameTestHelper helper) {
        var level = helper.getLevel();
        var catalog = RecipeCatalog.from(level.getServer().getRecipeManager(), level);
        var cake = new StackKey("minecraft:cake", "{}");
        var recipe = catalog.recipesFor(cake).stream().findFirst().orElseThrow();
        var milk = new StackKey("minecraft:milk_bucket", "{}");
        var bucket = new StackKey("minecraft:bucket", "{}");
        helper.assertTrue(recipe.ingredientOptions().stream().anyMatch(options -> options.contains(milk)),
                "live catalog should contain the cake milk ingredient");
        var stock = PlanningInventory.of(Map.of(milk, 3L, new StackKey("minecraft:egg", "{}"), 1L,
                new StackKey("minecraft:sugar", "{}"), 2L, new StackKey("minecraft:wheat", "{}"), 3L));
        var plan = CraftingPlanner.plan(catalog, cake, 1, stock, PlanningPolicy.DEFAULT);
        helper.assertTrue(plan.missing().isEmpty(), "cake ingredients should be available");
        helper.assertTrue(plan.remainders().getOrDefault(bucket, 0L) == 3L,
                "three selected milk buckets should return three buckets: " + plan.remainders());
        helper.assertTrue(plan.consumedDelta().getOrDefault(milk, 0L) == 3L,
                "three milk buckets should be consumed");
        helper.assertTrue(plan.remainingInventory().count(bucket) == 3L,
                "returned buckets must be conserved");
        helper.succeed();
    }

    @GameTest(structure = EMPTY_STRUCTURE, maxTicks = 40)
    public void retrieveMovesRequestedAmountIntoInventory(GameTestHelper helper) {
        var chest = placeChest(helper, new ItemStack(Items.DIAMOND, 64));
        var player = playerNextToChest(helper);

        var took = RetrieveHandler.retrieve(player, helper.absolutePos(CHEST), dimension(helper),
                "minecraft:diamond", "{}", 32);

        helper.assertTrue(took, "retrieve should report success");
        helper.assertTrue(player.getInventory().countItem(Items.DIAMOND) == 32,
                "player should hold 32 diamonds, holds " + player.getInventory().countItem(Items.DIAMOND));
        helper.assertTrue(chest.getItem(0).getCount() == 32,
                "chest should keep 32 diamonds, kept " + chest.getItem(0).getCount());
        helper.succeed();
    }

    @GameTest(structure = EMPTY_STRUCTURE, maxTicks = 40)
    public void retrieveDrainsMultipleStacks(GameTestHelper helper) {
        var chest = placeChest(helper, new ItemStack(Items.DIAMOND, 64));
        chest.setItem(1, new ItemStack(Items.DIAMOND, 64));
        var player = playerNextToChest(helper);

        RetrieveHandler.retrieve(player, helper.absolutePos(CHEST), dimension(helper),
                "minecraft:diamond", "{}", 100);

        helper.assertTrue(player.getInventory().countItem(Items.DIAMOND) == 100,
                "player should hold 100 diamonds, holds " + player.getInventory().countItem(Items.DIAMOND));
        helper.succeed();
    }

    @GameTest(structure = EMPTY_STRUCTURE, maxTicks = 40)
    public void retrieveIgnoresOtherItems(GameTestHelper helper) {
        var chest = placeChest(helper, new ItemStack(Items.OAK_LOG, 64));
        var player = playerNextToChest(helper);

        var took = RetrieveHandler.retrieve(player, helper.absolutePos(CHEST), dimension(helper),
                "minecraft:diamond", "{}", 1);

        helper.assertTrue(!took, "retrieve should fail when the item is absent");
        helper.assertTrue(chest.getItem(0).getCount() == 64, "chest contents must be untouched");
        helper.succeed();
    }

    @SuppressWarnings("removal")
    @GameTest(structure = EMPTY_STRUCTURE, maxTicks = 40)
    public void retrieveRefusesOutOfReachChest(GameTestHelper helper) {
        var chest = placeChest(helper, new ItemStack(Items.DIAMOND, 64));
        var player = helper.makeMockServerPlayerInLevel();
        var far = helper.absoluteVec(new net.minecraft.world.phys.Vec3(50.5, 2.0, 50.5));
        player.setPos(far.x, far.y, far.z);

        var took = RetrieveHandler.retrieve(player, helper.absolutePos(CHEST), dimension(helper),
                "minecraft:diamond", "{}", 1);

        helper.assertTrue(!took, "retrieve should fail beyond 5 blocks");
        helper.assertTrue(chest.getItem(0).getCount() == 64, "chest contents must be untouched");
        helper.succeed();
    }

    @GameTest(structure = EMPTY_STRUCTURE, maxTicks = 40)
    public void retrieveRefusesChestBlockedByStone(GameTestHelper helper) {
        var chest = placeChest(helper, new ItemStack(Items.DIAMOND, 64));
        var pos = helper.absolutePos(CHEST);
        helper.setBlock(pos.north(), Blocks.STONE);
        var player = helper.makeMockServerPlayerInLevel();
        player.setPos(pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() - 2.5);

        var took = RetrieveHandler.retrieve(player, pos, dimension(helper),
                "minecraft:diamond", "{}", 1);

        helper.assertTrue(!took, "a chest hidden behind stone must not be actionable by radius alone");
        helper.assertTrue(chest.getItem(0).getCount() == 64, "blocked chest contents must be untouched");
        helper.succeed();
    }

    @GameTest(structure = EMPTY_STRUCTURE, maxTicks = 40)
    public void configuredReachCannotBypassObstruction(GameTestHelper helper) {
        var chest = placeChest(helper, new ItemStack(Items.DIAMOND, 64));
        var pos = helper.absolutePos(CHEST);
        helper.setBlock(pos.north(), Blocks.STONE);
        var player = helper.makeMockServerPlayerInLevel();
        player.setPos(pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() - 2.5);

        var took = RetrieveHandler.retrieve(player, pos, dimension(helper),
                "minecraft:diamond", "{}", 1, 64, ContainerKind.CHEST);

        helper.assertTrue(!took, "configured reach must not bypass a blocked interaction point");
        helper.assertTrue(chest.getItem(0).getCount() == 64, "extended-reach obstruction must conserve stock");
        helper.succeed();
    }

    @GameTest(structure = EMPTY_STRUCTURE, maxTicks = 40)
    public void reachabilityReportsUnloadedAndDifferentDimensionTargets(GameTestHelper helper) {
        var player = playerNextToChest(helper);
        var pos = helper.absolutePos(CHEST);
        var unloaded = new BlockPos(pos.getX() + 512, pos.getY(), pos.getZ());
        var missing = Reachability.check(helper.getLevel(), player, unloaded, dimension(helper),
                TargetKind.CONTAINER, ContainerKind.CHEST, 64);
        helper.assertTrue(!missing.actionable() && missing.reason() == Reachability.Reason.CHUNK_UNLOADED,
                "unloaded target must not be generated by reach checks: " + missing);

        var otherDimension = Reachability.check(helper.getLevel(), player, pos, "minecraft:the_nether",
                TargetKind.CONTAINER, ContainerKind.CHEST, 64);
        helper.assertTrue(!otherDimension.actionable()
                        && otherDimension.reason() == Reachability.Reason.DIFFERENT_DIMENSION,
                "cross-dimension target must be rejected before block reads: " + otherDimension);
        helper.succeed();
    }

    @GameTest(structure = EMPTY_STRUCTURE, maxTicks = 40)
    public void reachabilityAcceptsTargetVisibleThroughDoorway(GameTestHelper helper) {
        placeChest(helper, new ItemStack(Items.DIAMOND, 1));
        var target = helper.absolutePos(CHEST);
        helper.setBlock(new BlockPos(CHEST.getX() - 1, CHEST.getY(), CHEST.getZ() - 1), Blocks.STONE);
        helper.setBlock(new BlockPos(CHEST.getX() + 1, CHEST.getY(), CHEST.getZ() - 1), Blocks.STONE);
        var player = helper.makeMockServerPlayerInLevel();
        player.setPos(target.getX() + 0.5, target.getY() + 1.0, target.getZ() - 0.5);

        var result = Reachability.check(helper.getLevel(), player, target, dimension(helper),
                TargetKind.CONTAINER, ContainerKind.CHEST, 0);
        helper.assertTrue(result.actionable(), "a doorway-visible target should remain actionable: " + result);
        helper.succeed();
    }

    @GameTest(structure = EMPTY_STRUCTURE, maxTicks = 40)
    public void retrieveRefusesWrongContainerHandler(GameTestHelper helper) {
        var chest = placeChest(helper, new ItemStack(Items.DIAMOND, 64));
        var pos = helper.absolutePos(CHEST);
        helper.setBlock(CHEST, Blocks.BARREL);
        helper.assertTrue(helper.getBlockState(CHEST).is(Blocks.BARREL), "fixture should replace the chest with a barrel");
        var player = playerNextToChest(helper);

        var took = RetrieveHandler.retrieve(player, pos, dimension(helper),
                "minecraft:diamond", "{}", 1, 0, ContainerKind.CHEST);

        helper.assertTrue(!took, "a changed target handler must not satisfy the old chest target");
        helper.assertTrue(player.getInventory().countItem(Items.DIAMOND) == 0,
                "changed target must not move items into the player");
        helper.succeed();
    }

    @GameTest(structure = EMPTY_STRUCTURE, maxTicks = 40)
    public void reachabilityClassifiesCraftingTableAndRange(GameTestHelper helper) {
        helper.setBlock(CHEST, Blocks.CRAFTING_TABLE);
        var pos = helper.absolutePos(CHEST);
        var player = playerNextToChest(helper);
        var dimension = dimension(helper);

        var nearby = Reachability.check(helper.getLevel(), player, pos, dimension,
                TargetKind.CRAFTING_TABLE, 0);
        helper.assertTrue(nearby.actionable() && nearby.reason() == Reachability.Reason.ACTIONABLE,
                "nearby crafting table should be actionable: " + nearby);
        helper.assertTrue(nearby.handlerExpectation() == Reachability.HandlerExpectation.CRAFTING_TABLE,
                "crafting table should carry its expected handler");

        player.setPos(pos.getX() + 40.5, pos.getY() + 1.0, pos.getZ() + 0.5);
        var far = Reachability.check(helper.getLevel(), player, pos, dimension,
                TargetKind.CRAFTING_TABLE, 0);
        helper.assertTrue(!far.actionable() && far.reason() == Reachability.Reason.OUT_OF_RANGE,
                "crafting table outside vanilla range must be rejected: " + far);

        helper.setBlock(CHEST, Blocks.STONE);
        var wrong = Reachability.check(helper.getLevel(), player, pos, dimension,
                TargetKind.CRAFTING_TABLE, 64);
        helper.assertTrue(!wrong.actionable() && wrong.reason() == Reachability.Reason.WRONG_BLOCK,
                "wrong block must not satisfy a crafting-table target: " + wrong);
        helper.succeed();
    }

    @GameTest(structure = EMPTY_STRUCTURE, maxTicks = 40)
    public void indexAndRetrieveReachIntoAShulkerInsideTheChest(GameTestHelper helper) {
        var shulker = new ItemStack(Items.SHULKER_BOX);
        shulker.set(DataComponents.CONTAINER,
                ItemContainerContents.fromItems(List.of(new ItemStack(Items.GOLD_INGOT, 20))));

        var chest = placeChest(helper, shulker);
        var player = playerNextToChest(helper);

        var slots = SlotReader.readContainerSlots(chest, player);
        var gold = slots.stream()
                .filter(s -> s.stack().key().itemId().equals("minecraft:gold_ingot"))
                .mapToInt(s -> s.stack().count())
                .sum();
        helper.assertTrue(gold == 20, "nested gold should be indexed, saw " + gold);

        var took = RetrieveHandler.retrieve(player, helper.absolutePos(CHEST), dimension(helper),
                "minecraft:gold_ingot", "{}", 8);

        helper.assertTrue(took, "retrieve should reach into the shulker");
        helper.assertTrue(player.getInventory().countItem(Items.GOLD_INGOT) == 8,
                "player should hold 8 gold, holds " + player.getInventory().countItem(Items.GOLD_INGOT));

        var left = chest.getItem(0).get(DataComponents.CONTAINER);
        var remaining = left == null ? 0 : left.nonEmptyItemCopyStream().mapToInt(ItemStack::getCount).sum();
        helper.assertTrue(remaining == 12, "shulker should keep 12 gold, kept " + remaining);
        helper.succeed();
    }

    @GameTest(structure = EMPTY_STRUCTURE, maxTicks = 40)
    public void nestedSlotReaderRetainsCompletePathAndOutermostHolder(GameTestHelper helper) {
        var gold = new ItemStack(Items.GOLD_INGOT, 20);
        var inner = new ItemStack(Items.SHULKER_BOX);
        inner.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(List.of(gold)));
        var outer = new ItemStack(Items.SHULKER_BOX);
        outer.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(List.of(inner)));
        var chest = placeChest(helper, outer);
        var player = playerNextToChest(helper);

        var slots = SlotReader.readContainerSlots(chest, player);
        var goldSnapshot = slots.stream()
                .filter(slot -> slot.stack().key().itemId().equals("minecraft:gold_ingot"))
                .findFirst()
                .orElseThrow()
                .stack();

         helper.assertTrue(goldSnapshot.provenance().slots().equals(List.of(0, 0, 0)),
                 "nested gold path should include every holder slot: "
                         + goldSnapshot.provenance().slots());
        helper.assertTrue(goldSnapshot.provenance().holderSlot() == 0,
                "nested gold should retain the outermost holder slot");
        var index = new InMemoryContainerIndex();
        var absolute = helper.absolutePos(CHEST);
        var sourceKey = SourceKey.storage(dimension(helper), ContainerKind.CHEST,
                List.of(new BlockPosition(absolute.getX(), absolute.getY(), absolute.getZ())));
        index.observe(new ContainerObservation(sourceKey, List.of(sourceKey), slots, Instant.now()));
        var location = index.search("gold_ingot").getFirst().sources().getFirst().locations().getFirst();
        helper.assertTrue(location.stack().provenance().slots().equals(List.of(0, 0, 0)),
                "index source locations must retain the exact nested physical path");
        helper.succeed();
    }

    @GameTest(structure = EMPTY_STRUCTURE, maxTicks = 40)
    public void depositPutsCarriedItemsBackWhereTheyLive(GameTestHelper helper) {
        var chest = placeChest(helper, new ItemStack(Items.OAK_LOG, 10));
        var player = playerNextToChest(helper);
        player.getInventory().add(new ItemStack(Items.OAK_LOG, 30));

        var moved = RetrieveHandler.deposit(player, helper.absolutePos(CHEST), "minecraft:oak_log", "{}", 20);

        helper.assertTrue(moved == 20, "should have deposited 20 logs, moved " + moved);
        helper.assertTrue(chest.getItem(0).getCount() == 30,
                "chest should hold 30 logs, holds " + chest.getItem(0).getCount());
        helper.assertTrue(player.getInventory().countItem(Items.OAK_LOG) == 10,
                "player should keep 10 logs, kept " + player.getInventory().countItem(Items.OAK_LOG));
        helper.succeed();
    }

    @GameTest(structure = EMPTY_STRUCTURE, maxTicks = 40)
    public void depositRefusesItemsTheContainerDoesNotStock(GameTestHelper helper) {
        var chest = placeChest(helper, new ItemStack(Items.OAK_LOG, 10));
        var player = playerNextToChest(helper);
        player.getInventory().add(new ItemStack(Items.DIAMOND_SWORD));

        var moved = RetrieveHandler.deposit(player, helper.absolutePos(CHEST), "minecraft:diamond_sword", "{}", 1);

        helper.assertTrue(moved == 0, "a sword the chest never held must not be deposited");
        helper.assertTrue(player.getInventory().countItem(Items.DIAMOND_SWORD) == 1, "the sword stays on the player");
        helper.assertTrue(chest.getItem(1).isEmpty(), "the chest gains nothing");
        helper.succeed();
    }

    @GameTest(structure = EMPTY_STRUCTURE, maxTicks = 40)
    public void retrieveDistinguishesItemsByComponents(GameTestHelper helper) {
        var named = new ItemStack(Items.DIAMOND_SWORD);
        named.set(DataComponents.CUSTOM_NAME, Component.literal("Bee Stinger"));

        var chest = placeChest(helper, named);
        chest.setItem(1, new ItemStack(Items.DIAMOND_SWORD));
        var player = playerNextToChest(helper);

        // "{}" is the plain sword's component signature, so the named one must be left alone.
        var took = RetrieveHandler.retrieve(player, helper.absolutePos(CHEST), dimension(helper),
                "minecraft:diamond_sword", "{}", 1);

        helper.assertTrue(took, "the plain sword should be retrievable");
        helper.assertTrue(chest.getItem(0).has(DataComponents.CUSTOM_NAME),
                "the named sword must stay in the chest");
        helper.assertTrue(chest.getItem(1).isEmpty(), "the plain sword should have left the chest");
        helper.succeed();
    }

    @GameTest(structure = EMPTY_STRUCTURE, maxTicks = 40)
    public void enchantmentLevelsAreSearchableAsDigits(GameTestHelper helper) {
        var sword = new ItemStack(Items.DIAMOND_SWORD);
        sword.set(DataComponents.CUSTOM_NAME, Component.literal("Bee Stinger"));
        var smite = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
        smite.set(helper.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT)
                .getOrThrow(Enchantments.SMITE), 4);
        sword.set(DataComponents.ENCHANTMENTS, smite.toImmutable());

        placeChest(helper, sword);
        var player = playerNextToChest(helper);
        var chest = helper.getBlockEntity(CHEST, ChestBlockEntity.class);
        var absolute = helper.absolutePos(CHEST);

        var index = new InMemoryContainerIndex();
        var positions = List.of(new BlockPosition(absolute.getX(), absolute.getY(), absolute.getZ()));
        var key = SourceKey.storage(dimension(helper), ContainerKind.CHEST, positions);
        index.observe(new ContainerObservation(key, List.of(key),
                SlotReader.readContainerSlots(chest, player), Instant.now()));

        helper.assertTrue(index.search("smite iv").size() == 1, "roman spelling should match");
        helper.assertTrue(index.search("smite 4").size() == 1, "arabic spelling should match");
        helper.assertTrue(index.search("smite 5").isEmpty(), "the wrong level should not match");
        helper.succeed();
    }

    @GameTest(structure = EMPTY_STRUCTURE, maxTicks = 40)
    public void indexFindsItemsReadFromARealChest(GameTestHelper helper) {
        placeChest(helper, new ItemStack(Items.DIAMOND, 12));
        var player = playerNextToChest(helper);
        var absolute = helper.absolutePos(CHEST);
        var chest = helper.getBlockEntity(CHEST, ChestBlockEntity.class);

        var slots = SlotReader.readContainerSlots(chest, player);
        var positions = List.of(new BlockPosition(absolute.getX(), absolute.getY(), absolute.getZ()));
        var key = SourceKey.storage(dimension(helper), ContainerKind.CHEST, positions);

        var index = new InMemoryContainerIndex();
        index.observe(new ContainerObservation(key, List.of(key), slots, Instant.now()));

        var results = index.search("diamond");
        helper.assertTrue(results.size() == 1, "expected exactly one match, got " + results.size());
        helper.assertTrue(results.getFirst().totalCount() == 12,
                "expected 12 diamonds, got " + results.getFirst().totalCount());
        helper.assertTrue(index.search("netherite").isEmpty(), "unrelated query must not match");
        helper.succeed();
    }

    private static ChestBlockEntity placeChest(GameTestHelper helper, ItemStack contents) {
        helper.setBlock(CHEST, Blocks.CHEST);
        var chest = helper.getBlockEntity(CHEST, ChestBlockEntity.class);
        helper.assertTrue(chest != null, "chest block entity should exist");
        chest.setItem(0, contents);
        return chest;
    }

    /** Mock player standing on top of the chest, well inside the 5-block reach. */
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
