package dev.smpb.findmyitems.test;

import dev.smpb.findmyitems.FindMyItemsClient;
import dev.smpb.findmyitems.gui.CatalogScreen;
import dev.smpb.findmyitems.gui.ChestHighlighter;
import dev.smpb.findmyitems.index.ItemResult;
import dev.smpb.findmyitems.model.ContainerKind;
import net.minecraft.client.gui.components.AbstractSelectionList;
import net.minecraft.client.gui.layouts.LayoutElement;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/**
 * End-to-end test that boots the real Minecraft client, creates a world, and drives the mod
 * through actual input: right-click a chest, open the catalog with the keybind, type a query.
 *
 * <p>Run with {@code ./gradlew runClientGameTest}. Screenshots land in
 * {@code run/clientGameTest/screenshots/} — they are the "what does it look like" artifact,
 * so the assertions below stay on facts (index contents, which screen is open) rather than pixels.
 */
public final class FindMyItemsClientGameTest implements FabricClientGameTest {
    /** Platform + chest are built in the air so terrain generation cannot get in the way. */
    private static final BlockPos STAND = new BlockPos(0, 100, 0);
    private static final BlockPos CHEST = new BlockPos(0, 100, 2);
    private static final BlockPos ENDER = new BlockPos(2, 100, 2);

    private static final int DIAMONDS = 32;
    /** Sits inside a shulker box that sits inside the chest. */
    private static final int BURIED_GOLD = 5;
    /** Issue #14: emeralds split between a block chest and the ender inventory. */
    private static final int CHEST_EMERALDS = 5;
    private static final int ENDER_EMERALDS = 10;
    /** Mirrors {@code CatalogScreen.BUTTON_SIZE}, which is not visible from this package. */
    private static final int TAKE_BUTTON_SIZE = 20;
    /** {@code AbstractSelectionList.Entry.CONTENT_PADDING}, likewise not visible here. */
    private static final int ENTRY_CONTENT_PADDING = 2;

    @Override
    public void runTest(ClientGameTestContext context) {
        try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
            singleplayer.getClientLevel().waitForChunksRender();

            var server = singleplayer.getServer();
            server.runCommand("gamemode creative");
            server.runCommand("gamerule doDaylightCycle false");
            server.runCommand("gamerule doMobSpawning false");
            server.runCommand("time set noon");
            buildScene(server);

            context.waitTicks(20);
            singleplayer.getClientLevel().waitForChunksRender();

            openChest(context);
            assertIndexed(context);

            context.setScreen(() -> null);
            context.waitTicks(5);

            enderChestTotalsStayHonest(context, server);

            openCatalog(context);
            search(context, "diamond");
            context.takeScreenshot("items-list-search");

            assertNestedShulkerIsSearchable(context);

            clearSearch(context, "diamond".length());

            click(context, "screen.findmyitems.layout.grid");
            context.takeScreenshot("items-grid");
            click(context, "screen.findmyitems.layout.list");

            click(context, "screen.findmyitems.view.containers");
            context.takeScreenshot("containers-list");
            click(context, "screen.findmyitems.layout.grid");
            context.takeScreenshot("containers-grid");
            click(context, "screen.findmyitems.layout.list");

            // Ctrl+3 is the shortcut for the third view; the tab buttons are already covered above.
            switchViewByShortcut(context, GLFW.GLFW_KEY_3);
            context.takeScreenshot("crafting-index");
            assertCraftingIndexIsPopulated(context);

            context.getInput().typeChars("stone pickaxe");
            context.waitTicks(5);
            context.takeScreenshot("crafting-tree");

            switchViewByShortcut(context, GLFW.GLFW_KEY_1);
            assertShowingItems(context);

            context.setScreen(() -> null);
            highlightTheChest(context);
            context.takeScreenshot("chest-highlighted");
        }
    }

    private static void switchViewByShortcut(ClientGameTestContext context, int digit) {
        context.getInput().holdControl();
        context.getInput().pressKey(digit);
        context.getInput().releaseControl();
        context.waitTicks(5);
    }

    /** With an empty box the crafting view lists the whole item registry to pick from. */
    private static void assertCraftingIndexIsPopulated(ClientGameTestContext context) {
        var rows = context.computeOnClient(mc -> {
            var screen = mc.gui.screen();
            if (!(screen instanceof CatalogScreen)) return -1;
            return screen.children().stream()
                    .filter(child -> child instanceof AbstractSelectionList<?>)
                    .mapToInt(child -> ((AbstractSelectionList<?>) child).children().size())
                    .max()
                    .orElse(0);
        });
        if (rows < 500) {
            throw new AssertionError("crafting view should list the item registry, listed " + rows + " rows");
        }
    }

    private static void assertShowingItems(ClientGameTestContext context) {
        var open = context.computeOnClient(mc -> mc.gui.screen() instanceof CatalogScreen);
        if (!open) {
            throw new AssertionError("ctrl+1 should have stayed on the catalog screen");
        }
    }

    /** Drives the highlight the locate button uses, so the glow render path is exercised for real. */
    private static void highlightTheChest(ClientGameTestContext context) {
        context.waitTicks(5);
        context.runOnClient(mc -> ChestHighlighter.highlight(
                List.of(CHEST), mc.level.dimension().identifier().toString()));
        context.waitTicks(10);
    }

    /** Stone platform at y=100 with a stocked chest two blocks in front of the player. */
    private static void buildScene(net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext server) {
        server.runOnServer(s -> {
            var level = s.overworld();
            for (int x = -2; x <= 2; x++) {
                for (int z = -2; z <= 3; z++) {
                    level.setBlockAndUpdate(new BlockPos(x, STAND.getY() - 1, z), Blocks.STONE.defaultBlockState());
                    level.setBlockAndUpdate(new BlockPos(x, STAND.getY(), z), Blocks.AIR.defaultBlockState());
                    level.setBlockAndUpdate(new BlockPos(x, STAND.getY() + 1, z), Blocks.AIR.defaultBlockState());
                }
            }

            level.setBlockAndUpdate(CHEST, Blocks.CHEST.defaultBlockState());
            if (level.getBlockEntity(CHEST) instanceof ChestBlockEntity chest) {
                chest.setItem(0, new ItemStack(Items.DIAMOND, DIAMONDS));
                chest.setItem(1, new ItemStack(Items.OAK_LOG, 12));
                chest.setItem(2, shulkerHolding(new ItemStack(Items.GOLD_INGOT, BURIED_GOLD)));
                chest.setItem(3, new ItemStack(Items.EMERALD, CHEST_EMERALDS));
                chest.setChanged();
            }

            level.setBlockAndUpdate(ENDER, Blocks.ENDER_CHEST.defaultBlockState());

            for (var player : s.getPlayerList().getPlayers()) {
                player.teleportTo(STAND.getX() + 0.5, STAND.getY(), STAND.getZ() + 0.5);
                player.getInventory().clearContent();
                player.getEnderChestInventory().clearContent();
                player.getEnderChestInventory().setItem(0, new ItemStack(Items.EMERALD, ENDER_EMERALDS));
            }
        });
    }

    /** Right-clicks the chest for real, so PositionCache and ObservationCollector both run. */
    private static void openChest(ClientGameTestContext context) {
        context.getInput().lookAt(CHEST);
        context.waitTicks(2);
        context.getInput().holdKeyFor(options -> options.keyUse, 2);
        context.waitForScreen(ContainerScreen.class);
        // ObservationCollector indexes on the client tick after the screen is initialised.
        context.waitTicks(5);
        context.takeScreenshot("chest-opened");
    }

    /**
     * Issue #14, end to end: an item split between a block chest and the ender inventory.
     *
     * <p>The ender chest is the one container whose contents outlive every block that opens it, so
     * it is the one that can end up counted in a total and reachable from nothing. Nothing here is
     * broken or dug up to get there — the way it happens in a played world is simply standing next
     * to the chest long enough for a rescan. After every step the same question is asked: does the
     * row's total equal what the row can name, and does Take move what the button promised?
     */
    private static void enderChestTotalsStayHonest(
            ClientGameTestContext context,
            net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext server) {
        openEnderChest(context);
        context.setScreen(() -> null);
        context.waitTicks(5);
        assertEmeraldRowAddsUp(context, "after opening both containers", CHEST_EMERALDS + ENDER_EMERALDS);

        // Nothing is touched here but the clock. A rescan reads the ender chest's block entity,
        // which is only the lid — the items live on the player — and used to take that for "the
        // chest is gone", stranding the contents while the block stood there in plain sight.
        context.runOnClient(mc -> FindMyItemsClient.config().rescanIntervalSeconds = 1);
        context.waitTicks(80);

        var enderContainers = context.computeOnClient(mc -> (int) FindMyItemsClient.index().snapshot().containers()
                .stream()
                .filter(c -> c.contentsKey().kind() == ContainerKind.ENDER_CHEST)
                .count());
        if (enderContainers != 1) {
            throw new AssertionError("rescanning the ender chest should leave one indexed container, found "
                    + enderContainers);
        }
        assertEmeraldRowAddsUp(context, "after a rescan", CHEST_EMERALDS + ENDER_EMERALDS);

        var reachable = context.computeOnClient(mc -> emeraldRow().sources().stream()
                .allMatch(source -> !source.source().positions().isEmpty()));
        if (!reachable) {
            throw new AssertionError("waiting out one rescan next to a standing ender chest left its "
                    + "contents unreachable — nothing was broken, so nothing should have been lost");
        }

        // Now empty both, nearest first. The ender chest is the second take, and it only works
        // because the rescan above left its access source alone.
        takeTheNearestEmeralds(context, server, "the block chest",
                CHEST_EMERALDS, ENDER_EMERALDS, "items-emerald-take-chest");
        takeTheNearestEmeralds(context, server, "the ender chest",
                CHEST_EMERALDS + ENDER_EMERALDS, 0, "items-emerald-take-ender");

        strandedEnderStockIsStillCounted(context, server);
    }

    /**
     * Restocks the ender inventory, then takes its block away.
     *
     * <p>This is the one honest route to a container with no way in: the remembered contents are
     * still true — they are on the player — but no block can open them. They must stay counted and
     * stay labelled, never silently folded into a total the rest of the row cannot account for.
     */
    private static void strandedEnderStockIsStillCounted(
            ClientGameTestContext context,
            net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext server) {
        server.runOnServer(s -> s.getPlayerList().getPlayers().forEach(p ->
                p.getEnderChestInventory().setItem(0, new ItemStack(Items.EMERALD, ENDER_EMERALDS))));
        // No reopening: the rescan is expected to pick the restock up through the standing block.
        context.waitTicks(80);
        assertEmeraldRowAddsUp(context, "after a rescan found the ender chest restocked", ENDER_EMERALDS);

        server.runOnServer(s -> s.overworld().setBlockAndUpdate(ENDER, Blocks.AIR.defaultBlockState()));
        context.waitTicks(80);
        assertEmeraldRowAddsUp(context, "after the ender chest block was removed", ENDER_EMERALDS);

        var stranded = context.computeOnClient(mc -> emeraldRow().sources().stream()
                .filter(source -> source.source().positions().isEmpty())
                .mapToInt(source -> source.count())
                .sum());
        if (stranded != ENDER_EMERALDS) {
            throw new AssertionError("the remembered ender stock should still be listed, as "
                    + ENDER_EMERALDS + " with no position; row lists " + stranded);
        }

        openCatalog(context);
        context.getInput().typeChars("emer");
        context.waitTicks(3);
        context.takeScreenshot("items-emerald-unreachable");
        context.setScreen(() -> null);
        context.waitTicks(5);

        // With no ender chest anywhere in the world, the count still follows the player's own
        // data: nothing is opened, nothing is placed, no chunk is read.
        server.runOnServer(s -> s.getPlayerList().getPlayers().forEach(p ->
                p.getEnderChestInventory().setItem(0, new ItemStack(Items.EMERALD, ENDER_EMERALDS * 2))));
        context.waitTicks(120);
        assertEmeraldRowAddsUp(context, "after the ender inventory changed with no chest placed",
                ENDER_EMERALDS * 2);
    }

    /**
     * Clicks the row's real Take button and checks what moved against what it offered.
     *
     * <p>This is the half of issue #14 no assertion on the index can reach. The clamp the player
     * acts on comes from the nearest source's count, so a total padded with stock that has no
     * source shows up here and nowhere else: the button says one number, the chest gives another.
     */
    private static void takeTheNearestEmeralds(
            ClientGameTestContext context,
            net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext server,
            String from,
            int expectedCarried,
            int expectedRemaining,
            String screenshot) {
        openCatalog(context);
        context.getInput().typeChars("emer");
        context.waitTicks(3);

        // MouseHandler is fed raw window coordinates, so the button's GUI position is scaled back.
        var cursor = context.computeOnClient(mc -> {
            var list = mc.gui.screen().children().stream()
                    .filter(child -> child instanceof AbstractSelectionList<?>)
                    .map(child -> (AbstractSelectionList<?>) child)
                    .findFirst()
                    .orElseThrow();
            // AbstractSelectionList.Entry is protected, so the row is read through LayoutElement
            // and its content box re-derived rather than widening a vanilla class for a test.
            var row = (LayoutElement) list.children().getFirst();
            var window = mc.getWindow();
            var takeCentreX = row.getX() + row.getWidth() - ENTRY_CONTENT_PADDING - TAKE_BUTTON_SIZE / 2;
            var takeCentreY = row.getY() + row.getHeight() / 2;
            return new double[] {
                    takeCentreX * (double) window.getScreenWidth() / window.getGuiScaledWidth(),
                    takeCentreY * (double) window.getScreenHeight() / window.getGuiScaledHeight(),
            };
        });

        context.getInput().setCursorPos(cursor[0], cursor[1]);
        context.waitTicks(3);
        context.takeScreenshot(screenshot);
        context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
        // Retrieval goes out through a ghost container open and back from the server.
        context.waitTicks(40);
        context.setScreen(() -> null);
        context.waitTicks(10);

        // The server's copy is where the items actually are; the client mirror follows it.
        var carried = server.computeOnServer(s -> {
            var total = 0;
            for (var p : s.getPlayerList().getPlayers()) {
                var inventory = p.getInventory();
                for (int i = 0; i < inventory.getContainerSize(); i++) {
                    if (inventory.getItem(i).is(Items.EMERALD)) total += inventory.getItem(i).getCount();
                }
            }
            return total;
        });
        var remaining = context.computeOnClient(mc -> emeraldRow() == null ? 0 : emeraldRow().totalCount());
        var report = " (carried " + carried + ", row total " + remaining + ")";

        if (carried != expectedCarried) {
            throw new AssertionError("taking from " + from + " should have brought the inventory to "
                    + expectedCarried + " emeralds" + report);
        }
        if (remaining != expectedRemaining) {
            throw new AssertionError("after taking from " + from + " the row should count "
                    + expectedRemaining + report);
        }
    }

    private static ItemResult emeraldRow() {
        return FindMyItemsClient.index().search("emerald").stream()
                .filter(r -> r.key().itemId().equals("minecraft:emerald"))
                .findFirst()
                .orElse(null);
    }

    /** The one invariant issue #14 is about: the headline total is the sum of what the row lists. */
    private static void assertEmeraldRowAddsUp(ClientGameTestContext context, String stage, int expectedTotal) {
        var complaint = context.computeOnClient(mc -> {
            var row = emeraldRow();
            if (row == null) return "no emerald row at all";
            var listed = row.sources().stream().mapToInt(source -> source.count()).sum();
            if (row.totalCount() != expectedTotal) {
                return "total is " + row.totalCount() + ", expected " + expectedTotal;
            }
            if (listed != row.totalCount()) {
                return "total is " + row.totalCount() + " but its sources account for only " + listed;
            }
            return null;
        });
        if (complaint != null) {
            throw new AssertionError("emerald row " + stage + ": " + complaint);
        }
    }

    private static void openEnderChest(ClientGameTestContext context) {
        context.getInput().lookAt(ENDER);
        context.waitTicks(2);
        context.getInput().holdKeyFor(options -> options.keyUse, 2);
        context.waitForScreen(ContainerScreen.class);
        context.waitTicks(5);
        context.takeScreenshot("ender-chest-opened");
    }

    private static void assertIndexed(ClientGameTestContext context) {
        var count = context.computeOnClient(mc -> {
            var index = FindMyItemsClient.index();
            if (index == null) return -1;
            return index.search("diamond").stream().mapToInt(r -> r.totalCount()).sum();
        });
        if (count != DIAMONDS) {
            throw new AssertionError("opening the chest should have indexed " + DIAMONDS
                    + " diamonds, index reports " + count);
        }
    }

    /** A shulker box item whose container component holds the given stack. */
    private static ItemStack shulkerHolding(ItemStack inner) {
        var shulker = new ItemStack(Items.SHULKER_BOX);
        shulker.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(List.of(inner)));
        return shulker;
    }

    /** The gold is inside a shulker inside the chest — indexing has to walk into it. */
    private static void assertNestedShulkerIsSearchable(ClientGameTestContext context) {
        var gold = context.computeOnClient(mc -> FindMyItemsClient.index().search("gold ingot").stream()
                .filter(r -> r.key().itemId().equals("minecraft:gold_ingot"))
                .mapToInt(r -> r.totalCount())
                .sum());
        if (gold != BURIED_GOLD) {
            throw new AssertionError("gold inside the nested shulker should be indexed as "
                    + BURIED_GOLD + ", index reports " + gold);
        }
    }

    private static void click(ClientGameTestContext context, String translationKey) {
        context.clickScreenButton(translationKey);
        context.waitTicks(3);
    }

    private static void clearSearch(ClientGameTestContext context, int characters) {
        for (int i = 0; i < characters; i++) {
            context.getInput().pressKey(GLFW.GLFW_KEY_BACKSPACE);
        }
        context.waitTicks(3);
    }

    private static void openCatalog(ClientGameTestContext context) {
        context.getInput().pressKey(GLFW.GLFW_KEY_B);
        context.waitForScreen(CatalogScreen.class);
        context.waitTicks(2);
        context.takeScreenshot("catalog-open");
    }

    private static void search(ClientGameTestContext context, String query) {
        context.getInput().typeChars(query);
        context.waitTicks(2);

        var typed = context.computeOnClient(mc -> mc.gui.screen() instanceof CatalogScreen);
        if (!typed) {
            throw new AssertionError("catalog screen closed while typing");
        }

        var matches = context.computeOnClient(mc -> FindMyItemsClient.index().search(query).size());
        if (matches != 1) {
            throw new AssertionError("expected exactly one match for '" + query + "', got " + matches);
        }
    }
}
