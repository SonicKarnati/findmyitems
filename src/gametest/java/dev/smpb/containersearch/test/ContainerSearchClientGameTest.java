package dev.smpb.containersearch.test;

import dev.smpb.containersearch.ContainerSearchClient;
import dev.smpb.containersearch.gui.CatalogScreen;
import dev.smpb.containersearch.gui.ChestHighlighter;
import net.minecraft.client.gui.components.AbstractSelectionList;
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
public final class ContainerSearchClientGameTest implements FabricClientGameTest {
    /** Platform + chest are built in the air so terrain generation cannot get in the way. */
    private static final BlockPos STAND = new BlockPos(0, 100, 0);
    private static final BlockPos CHEST = new BlockPos(0, 100, 2);

    private static final int DIAMONDS = 32;
    /** Sits inside a shulker box that sits inside the chest. */
    private static final int BURIED_GOLD = 5;

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

            openCatalog(context);
            search(context, "diamond");
            context.takeScreenshot("items-list-search");

            assertNestedShulkerIsSearchable(context);

            clearSearch(context, "diamond".length());
            click(context, "screen.container-search.layout.grid");
            context.takeScreenshot("items-grid");
            click(context, "screen.container-search.layout.list");

            click(context, "screen.container-search.view.containers");
            context.takeScreenshot("containers-list");
            click(context, "screen.container-search.layout.grid");
            context.takeScreenshot("containers-grid");
            click(context, "screen.container-search.layout.list");

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
                chest.setChanged();
            }

            for (var player : s.getPlayerList().getPlayers()) {
                player.teleportTo(STAND.getX() + 0.5, STAND.getY(), STAND.getZ() + 0.5);
                player.getInventory().clearContent();
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

    private static void assertIndexed(ClientGameTestContext context) {
        var count = context.computeOnClient(mc -> {
            var index = ContainerSearchClient.index();
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
        var gold = context.computeOnClient(mc -> ContainerSearchClient.index().search("gold ingot").stream()
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

        var matches = context.computeOnClient(mc -> ContainerSearchClient.index().search(query).size());
        if (matches != 1) {
            throw new AssertionError("expected exactly one match for '" + query + "', got " + matches);
        }
    }
}
