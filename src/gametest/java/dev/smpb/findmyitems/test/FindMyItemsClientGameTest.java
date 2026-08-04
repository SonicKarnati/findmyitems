package dev.smpb.findmyitems.test;

import dev.smpb.findmyitems.FindMyItemsClient;
import dev.smpb.findmyitems.gui.CatalogScreen;
import dev.smpb.findmyitems.gui.CatalogScreenTestAccess;
import dev.smpb.findmyitems.gui.ChestHighlighter;
import dev.smpb.findmyitems.craft.CraftingPlan;
import dev.smpb.findmyitems.craft.PlanScore;
import dev.smpb.findmyitems.craft.PlanningInventory;
import dev.smpb.findmyitems.index.ItemResult;
import dev.smpb.findmyitems.model.ContainerKind;
import dev.smpb.findmyitems.model.BlockPosition;
import dev.smpb.findmyitems.model.SourceKey;
import dev.smpb.findmyitems.model.StackKey;
import dev.smpb.findmyitems.retrieval.CraftingExecutor;
import dev.smpb.findmyitems.retrieval.ExecutionStatus;
import dev.smpb.findmyitems.retrieval.GhostOpen;
import dev.smpb.findmyitems.search.InventorySearchController;
import net.minecraft.client.gui.components.EditBox;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.client.gui.screens.inventory.FurnaceScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.Map;

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
    private static final BlockPos FURNACE = new BlockPos(-2, 100, 2);

    private static final int DIAMONDS = 32;
    /** Sits inside a shulker box that sits inside the chest. */
    private static final int BURIED_GOLD = 5;
    /** Issue #14: emeralds split between a block chest and the ender inventory. */
    private static final int CHEST_EMERALDS = 5;
    private static final int ENDER_EMERALDS = 10;

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
            assertFilterBarVisible(context, true);
            assertIndexed(context);
            assertContainerFilterSearchesTooltips(context);

            context.setScreen(() -> null);
            openFurnace(context);
            assertFilterBarVisible(context, false);

            context.setScreen(() -> null);
            context.waitTicks(5);

            enderChestTotalsStayHonest(context, server);

            openCatalog(context);
            assertTickDrivenDiamondPickaxe(context);
            assertLocateAndAutomaticRetrievalLabels(context);
            assertDefaultCatalogAmount(context);
            search(context, "diamond");
            context.takeScreenshot("items-list-search");

            assertNestedShulkerIsSearchable(context);

            clearSearch(context, "diamond".length());

            click(context, "screen.findmyitems.layout.grid");
            context.takeScreenshot("items-grid");
            hoverFirstGridCell(context);
            context.takeScreenshot("items-grid-detail-reachable");

            // The emerald is the interesting cell: its stock is remembered with no chest to open.
            context.getInput().typeChars("emer");
            context.waitTicks(3);
            hoverFirstGridCell(context);
            context.takeScreenshot("items-grid-detail");
            clearSearch(context, "emer".length());

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
            assertCraftingBrowseIsLazyAndRootBased(context);
            assertCraftingViewportAndScroll(context);
            context.runOnClient(mc -> requireCatalog(mc).mouseScrolled(200, 100, 0, 20));
            context.waitTicks(2);

            clickFirstCraftingRow(context);
            assertSingleSelectedPlan(context);
            assertGenerationInvalidation(context);

            context.getInput().typeChars("not-a-real-item");
            context.waitTicks(5);
            assertSelectionClearsAfterFilter(context);
            context.takeScreenshot("crafting-tree");

            switchViewByShortcut(context, GLFW.GLFW_KEY_1);
            assertShowingItems(context);

            context.setScreen(() -> null);
            assertGhostOpenRefusesBlockedChest(context, server);
            highlightTheChest(context);
            context.takeScreenshot("chest-highlighted");
            assertExecutorBusyGuard(context);
        }
    }

    private static void switchViewByShortcut(ClientGameTestContext context, int digit) {
        context.getInput().holdControl();
        context.getInput().pressKey(digit);
        context.getInput().releaseControl();
        context.waitTicks(5);
    }

    private static void assertExecutorBusyGuard(ClientGameTestContext context) {
        var result = context.computeOnClient(mc -> {
            var key = new StackKey("minecraft:diamond_pickaxe", "{}");
            var plan = CraftingPlan.root(key, 1, PlanningInventory.empty(), new PlanScore(0, 0, 0, 0, 0));
            var request = new CraftingExecutor.ExecutionRequest(plan, List.of(), 0, 0,
                    CraftingExecutor.Mode.GATHER_ONLY);
            var executor = FindMyItemsClient.executor();
            var first = executor.start(request);
            var second = executor.start(request);
            var cancelled = executor.cancel(CraftingExecutor.CancelReason.SELECTION_CHANGED);
            executor.start(request);
            executor.tick();
            var actions = executor.actionsLastTick();
            var replaced = executor.replace(request);
            return new ExecutionStatus[] {first, second, cancelled, replaced,
                    actions <= 1 ? ExecutionStatus.COMPLETE : ExecutionStatus.FAILED};
        });
        if (result[0] != ExecutionStatus.CALCULATING || result[1] != ExecutionStatus.BUSY
                || result[2] != ExecutionStatus.CANCELLED || result[3] != ExecutionStatus.CALCULATING
                || result[4] != ExecutionStatus.COMPLETE) {
            throw new AssertionError("executor must reject overlapping requests and record cancellation: "
                    + java.util.Arrays.toString(result));
        }
    }

    private static void assertTickDrivenDiamondPickaxe(ClientGameTestContext context) {
        var result = context.computeOnClient(mc -> {
            var output = new StackKey("minecraft:diamond_pickaxe", "{}");
            var diamonds = new StackKey("minecraft:diamond", "{}");
            var sticks = new StackKey("minecraft:stick", "{}");
            var node = CraftingPlan.node(output, 1, 1, 1, List.of(), Map.of(), Map.of(), null);
            var plan = CraftingPlan.of(node, PlanningInventory.empty(),
                    Map.of(diamonds, 3L, sticks, 2L), Map.of(), Map.of(),
                    new PlanScore(0, 0, 1, 0, 1));
            var positions = List.of(new BlockPosition(CHEST.getX(), CHEST.getY(), CHEST.getZ()));
            var contents = SourceKey.storage(mc.level.dimension().identifier().toString(), ContainerKind.CHEST, positions);
            var diamondSource = new CraftingExecutor.SourceSnapshot(diamonds,
                    mc.level.dimension().identifier().toString(), ContainerKind.CHEST,
                    List.of(CHEST), List.of(0), 3, contents, List.of(contents), new ItemStack(Items.DIAMOND));
            var stickSource = new CraftingExecutor.SourceSnapshot(sticks,
                    mc.level.dimension().identifier().toString(), ContainerKind.CHEST,
                    List.of(CHEST), List.of(4), 2, contents, List.of(contents), new ItemStack(Items.STICK));
            var executor = FindMyItemsClient.executor();
            executor.start(new CraftingExecutor.ExecutionRequest(plan, List.of(diamondSource, stickSource),
                    CraftingExecutor.currentPlayerGeneration(), CraftingExecutor.currentWorldGeneration(),
                    CraftingExecutor.Mode.GATHER_AND_CRAFT));
            return output;
        });
        for (int tick = 0; tick < 140; tick++) {
            context.waitTicks(1);
            var complete = context.computeOnClient(mc -> FindMyItemsClient.executor().status()
                    == ExecutionStatus.COMPLETE);
            if (complete) break;
        }
        var complete = context.computeOnClient(mc -> FindMyItemsClient.executor().status());
        if (complete != ExecutionStatus.COMPLETE) {
            var diagnostics = context.computeOnClient(mc -> FindMyItemsClient.executor().state() + " "
                    + FindMyItemsClient.executor().transferJournal() + " table="
                    + FindMyItemsClient.executor().tableRequiredMaterials() + " "
                    + FindMyItemsClient.executor().failureDiagnostics());
            throw new AssertionError("tick-driven pickaxe plan did not complete: " + complete + " " + diagnostics);
        }
        var crafted = context.computeOnClient(mc -> mc.getSingleplayerServer().getPlayerList()
                .getPlayer(mc.player.getUUID()).getInventory().countItem(Items.DIAMOND_PICKAXE));
        if (crafted != 1) throw new AssertionError("expected one crafted diamond pickaxe, found " + crafted);
    }

    /** With an empty box the crafting view lists recipe roots, not expanded plans. */
    private static void assertCraftingIndexIsPopulated(ClientGameTestContext context) {
        var rows = context.computeOnClient(mc -> CatalogScreenTestAccess.rowCount(requireCatalog(mc)));
        if (rows < 500) {
            throw new AssertionError("crafting view should list recipe roots, listed " + rows + " rows");
        }
    }

    private static void assertLocateAndAutomaticRetrievalLabels(ClientGameTestContext context) {
        var semantics = context.computeOnClient(mc -> new String[] {
                String.valueOf(CatalogScreenTestAccess.locateVisible(0, true)),
                String.valueOf(CatalogScreenTestAccess.locateVisible(5, true)),
                CatalogScreenTestAccess.automaticStatusKey(0, 5, false, true),
                CatalogScreenTestAccess.automaticStatusKey(0, 5, true, true),
        });
        if (!semantics[0].equals("false") || !semantics[1].equals("true")) {
            throw new AssertionError("locate must hide zero stock and retain positive stock: "
                    + java.util.Arrays.toString(semantics));
        }
        if (!semantics[2].equals("screen.findmyitems.craft.unavailable")
                || !semantics[3].equals("screen.findmyitems.craft.reachable_now")) {
            throw new AssertionError("positive unavailable stock must be labeled unavailable: "
                    + java.util.Arrays.toString(semantics));
        }
    }

    private static void assertCraftingBrowseIsLazyAndRootBased(ClientGameTestContext context) {
        var state = context.computeOnClient(mc -> {
            var screen = requireCatalog(mc);
            return CatalogScreenTestAccess.browseState(screen);
        });
        if (state.planRequests() != 0) {
            throw new AssertionError("empty crafting browse must not invoke the planner, requests="
                    + state.planRequests());
        }
        if (state.selected()) {
            throw new AssertionError("empty crafting browse must not select an output");
        }
        if (!state.rootRows()) {
            throw new AssertionError("crafting browse rows must contain root outputs only");
        }
    }

    private static void assertCraftingViewportAndScroll(ClientGameTestContext context) {
        var visible = context.computeOnClient(mc -> CatalogScreenTestAccess.visibleRowCount(requireCatalog(mc)));
        var rendered = context.computeOnClient(mc -> CatalogScreenTestAccess.renderedRowCount(requireCatalog(mc)));
        var total = context.computeOnClient(mc -> CatalogScreenTestAccess.rowCount(requireCatalog(mc)));
        if (visible <= 0 || visible >= total) {
            throw new AssertionError("crafting viewport should render a clipped subset of rows, visible="
                    + visible + ", total=" + total);
        }
        if (rendered <= 0 || rendered > visible + 2) {
            throw new AssertionError("crafting renderer should use visible rows plus overscan, rendered=" + rendered
                    + ", visible=" + visible);
        }

        context.runOnClient(mc -> requireCatalog(mc).mouseScrolled(200, 100, 0, -20));
        context.waitTicks(2);
        var before = context.computeOnClient(mc -> CatalogScreenTestAccess.scrollAmount(requireCatalog(mc)));
        if (before <= 0) {
            throw new AssertionError("crafting list should scroll before an index-only refresh");
        }
        context.runOnClient(mc -> FindMyItemsClient.index().replace(FindMyItemsClient.index().snapshot()));
        context.waitTicks(3);
        var after = context.computeOnClient(mc -> CatalogScreenTestAccess.scrollAmount(requireCatalog(mc)));
        if (Math.abs(before - after) > 0.01) {
            throw new AssertionError("index-only refresh must preserve scroll, before=" + before + ", after=" + after);
        }

        var hit = context.computeOnClient(mc -> CatalogScreenTestAccess.hitTestRow(requireCatalog(mc), 200.0, 100.0));
        var miss = context.computeOnClient(mc -> CatalogScreenTestAccess.hitTestRow(requireCatalog(mc), 200.0, 10000.0));
        if (hit.isEmpty() || miss.isPresent()) {
            throw new AssertionError("viewport hit testing must accept an in-viewport row and reject clipped space");
        }

        var bottom = context.computeOnClient(mc -> CatalogScreenTestAccess.lastVisibleRowBottomCenter(requireCatalog(mc)));
        var bottomHit = context.computeOnClient(mc -> CatalogScreenTestAccess.hitTestRow(requireCatalog(mc),
                bottom[0], bottom[1]));
        if (bottomHit.isEmpty()) {
            throw new AssertionError("bottom clipped row must remain hit-testable inside the viewport");
        }
    }

    private static void clickFirstCraftingRow(ClientGameTestContext context) {
        var cursor = context.computeOnClient(mc -> {
            var row = CatalogScreenTestAccess.firstVisibleRowCenter(requireCatalog(mc));
            var window = mc.getWindow();
            return new double[] {
                    row[0] * window.getScreenWidth() / window.getGuiScaledWidth(),
                    row[1] * window.getScreenHeight() / window.getGuiScaledHeight()
            };
        });
        context.getInput().setCursorPos(cursor[0], cursor[1]);
        context.waitTicks(1);
        var hovered = context.computeOnClient(mc -> CatalogScreenTestAccess.hasHoveredIdentity(requireCatalog(mc)));
        if (!hovered) {
            throw new AssertionError("hovering a browse row must expose its stable output identity");
        }
        context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
        context.waitTicks(1);
    }

    private static void assertSingleSelectedPlan(ClientGameTestContext context) {
        var state = context.computeOnClient(mc -> {
            var screen = requireCatalog(mc);
            return CatalogScreenTestAccess.selectionState(screen);
        });
        if (state.planRequests() != 1) {
            throw new AssertionError("selecting one crafting output must invoke exactly one plan request, requests="
                    + state.planRequests());
        }
        if (!state.selected()) {
            throw new AssertionError("selecting a crafting output must retain its stable identity");
        }

        context.waitTicks(20);
        state = context.computeOnClient(mc -> CatalogScreenTestAccess.selectionState(requireCatalog(mc)));
        if (state.generations().appliedPlanGeneration() != state.generations().planGeneration()) {
            throw new AssertionError("selected output should apply its current plan before invalidation");
        }
    }

    private static void assertSelectionClearsAfterFilter(ClientGameTestContext context) {
        var state = context.computeOnClient(mc -> {
            var screen = requireCatalog(mc);
            return CatalogScreenTestAccess.selectionState(screen);
        });
        if (state.selected() || state.hovered()) {
            throw new AssertionError("filter changes must clear stale crafting selection");
        }
        if (state.generations().appliedPlanGeneration() == state.generations().planGeneration()) {
            throw new AssertionError("stale plan result must not be applied after a query generation change");
        }
    }

    private static void assertGenerationInvalidation(ClientGameTestContext context) {
        var beforeAmount = context.computeOnClient(mc -> CatalogScreenTestAccess.selectionState(requireCatalog(mc))
                .generations());
        setCatalogAmount(context, "2");
        var afterAmount = context.computeOnClient(mc -> CatalogScreenTestAccess.selectionState(requireCatalog(mc))
                .generations());
        if (afterAmount.searchGeneration() <= beforeAmount.searchGeneration()
                || afterAmount.planGeneration() <= beforeAmount.planGeneration()) {
            throw new AssertionError("amount changes must advance query and plan generations");
        }

        switchViewByShortcut(context, GLFW.GLFW_KEY_1);
        var beforeView = context.computeOnClient(mc -> CatalogScreenTestAccess.selectionState(requireCatalog(mc))
                .generations());
        switchViewByShortcut(context, GLFW.GLFW_KEY_3);
        var afterView = context.computeOnClient(mc -> CatalogScreenTestAccess.selectionState(requireCatalog(mc))
                .generations());
        if (afterView.searchGeneration() <= beforeView.searchGeneration()) {
            throw new AssertionError("view changes must advance the query generation");
        }

        switchViewByShortcut(context, GLFW.GLFW_KEY_1);
        var beforeLayout = context.computeOnClient(mc -> CatalogScreenTestAccess.selectionState(requireCatalog(mc))
                .generations());
        click(context, "screen.findmyitems.layout.grid");
        var afterLayout = context.computeOnClient(mc -> CatalogScreenTestAccess.selectionState(requireCatalog(mc))
                .generations());
        click(context, "screen.findmyitems.layout.list");
        if (afterLayout.searchGeneration() <= beforeLayout.searchGeneration()) {
            throw new AssertionError("layout changes must advance the query generation");
        }

        switchViewByShortcut(context, GLFW.GLFW_KEY_3);
        var beforeRecipe = context.computeOnClient(mc -> CatalogScreenTestAccess.selectionState(requireCatalog(mc))
                .generations());
        context.runOnClient(mc -> CatalogScreen.invalidateRecipeCache());
        context.waitTicks(2);
        var afterRecipe = context.computeOnClient(mc -> CatalogScreenTestAccess.selectionState(requireCatalog(mc))
                .generations());
        if (afterRecipe.searchGeneration() <= beforeRecipe.searchGeneration()) {
            throw new AssertionError("recipe reloads must advance the query generation");
        }

        var beforeIndex = afterRecipe;
        context.runOnClient(mc -> FindMyItemsClient.index().replace(FindMyItemsClient.index().snapshot()));
        context.waitTicks(2);
        var afterIndex = context.computeOnClient(mc -> CatalogScreenTestAccess.selectionState(requireCatalog(mc))
                .generations());
        if (afterIndex.searchGeneration() <= beforeIndex.searchGeneration()) {
            throw new AssertionError("index revisions must advance the query generation");
        }
    }

    private static CatalogScreen requireCatalog(net.minecraft.client.Minecraft minecraft) {
        if (!(minecraft.gui.screen() instanceof CatalogScreen screen)) {
            throw new AssertionError("catalog screen is not open");
        }
        return screen;
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

    private static void assertGhostOpenRefusesBlockedChest(
            ClientGameTestContext context,
            net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext server) {
        server.runOnServer(s -> {
            for (int y = STAND.getY(); y <= STAND.getY() + 2; y++) {
                s.overworld().setBlockAndUpdate(
                        new BlockPos(STAND.getX(), y, STAND.getZ() + 1), Blocks.STONE.defaultBlockState());
            }
        });
        context.waitTicks(2);
        var canOpen = context.computeOnClient(mc -> GhostOpen.canOpen(CHEST));
        if (canOpen) {
            throw new AssertionError("GhostOpen must refuse a chest with no visible interaction point");
        }
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
            level.setBlockAndUpdate(FURNACE, Blocks.FURNACE.defaultBlockState());
            if (level.getBlockEntity(CHEST) instanceof ChestBlockEntity chest) {
                chest.setItem(0, new ItemStack(Items.DIAMOND, DIAMONDS));
                chest.setItem(1, new ItemStack(Items.OAK_LOG, 12));
                chest.setItem(2, shulkerHolding(new ItemStack(Items.GOLD_INGOT, BURIED_GOLD)));
                chest.setItem(3, new ItemStack(Items.EMERALD, CHEST_EMERALDS));
                chest.setItem(4, new ItemStack(Items.STICK, 12));
                chest.setChanged();
            }

            level.setBlockAndUpdate(new BlockPos(0, STAND.getY(), 3), Blocks.CRAFTING_TABLE.defaultBlockState());

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

    private static void openFurnace(ClientGameTestContext context) {
        context.getInput().lookAt(FURNACE);
        context.waitTicks(2);
        context.getInput().holdKeyFor(options -> options.keyUse, 2);
        context.waitForScreen(FurnaceScreen.class);
        context.waitTicks(2);
    }

    private static void assertFilterBarVisible(ClientGameTestContext context, boolean expected) {
        var visible = context.computeOnClient(mc -> mc.gui.screen() != null
                && mc.gui.screen().children().stream().anyMatch(child -> child instanceof net.minecraft.client.gui.components.EditBox));
        if (visible != expected) {
            throw new AssertionError("expected filter bar visible=" + expected + ", but was " + visible);
        }
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
                CHEST_EMERALDS, ENDER_EMERALDS, CHEST_EMERALDS, "items-emerald-take-chest");
        takeTheNearestEmeralds(context, server, "the ender chest",
                CHEST_EMERALDS + ENDER_EMERALDS, 0, ENDER_EMERALDS, "items-emerald-take-ender");

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
            int requested,
            String screenshot) {
        openCatalog(context);
        context.getInput().typeChars("emer");
        context.waitTicks(3);
        setCatalogAmount(context, String.valueOf(requested));

        var cursor = context.computeOnClient(mc -> {
            var row = CatalogScreenTestAccess.firstVisibleRowTakeCenter(requireCatalog(mc));
            var window = mc.getWindow();
            return new double[] {
                    row[0] * window.getScreenWidth() / window.getGuiScaledWidth(),
                    row[1] * window.getScreenHeight() / window.getGuiScaledHeight(),
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

    private static void setCatalogAmount(ClientGameTestContext context, String amount) {
        context.runOnClient(mc -> mc.gui.screen().children().stream()
                .filter(child -> child instanceof EditBox)
                .map(child -> (EditBox) child)
                .skip(1)
                .findFirst()
                .ifPresent(field -> field.setValue(amount)));
        context.waitTicks(2);
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

    private static void assertContainerFilterSearchesTooltips(ClientGameTestContext context) {
        var results = context.computeOnClient(mc -> {
            var sword = new ItemStack(Items.DIAMOND_SWORD);
            sword.set(DataComponents.CUSTOM_NAME, Component.literal("Stormblade"));
            var enchantments = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
            enchantments.set(mc.level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT)
                    .getOrThrow(Enchantments.SMITE), 4);
            sword.set(DataComponents.ENCHANTMENTS, enchantments.toImmutable());
            try {
                var matcher = InventorySearchController.class.getDeclaredMethod(
                        "matches", ItemStack.class, String.class);
                matcher.setAccessible(true);
                return new boolean[] {
                        (boolean) matcher.invoke(null, sword, "smite"),
                        (boolean) matcher.invoke(null, sword, "iv"),
                        (boolean) matcher.invoke(null, sword, "sharpness v"),
                        (boolean) matcher.invoke(null, sword, "sharpness"),
                        (boolean) matcher.invoke(null, sword, "stormblade"),
                        (boolean) matcher.invoke(null, sword, "diamond_sword")
                };
            } catch (ReflectiveOperationException e) {
                throw new AssertionError("could not invoke container filter matcher", e);
            }
        });
        if (!results[0] || !results[1] || results[2] || results[3]
                || !results[4] || !results[5]) {
            throw new AssertionError("container filter should match display name, tooltip name and level, "
                    + "item name and path, but not unrelated enchantments or levels");
        }
    }

    /** Puts the cursor on the first grid cell, so the detail pane has something to describe. */
    private static void hoverFirstGridCell(ClientGameTestContext context) {
        var cursor = context.computeOnClient(mc -> {
            var row = CatalogScreenTestAccess.firstVisibleCellCenter(requireCatalog(mc));
            var window = mc.getWindow();
            return new double[] {
                    row[0] * window.getScreenWidth() / window.getGuiScaledWidth(),
                    row[1] * window.getScreenHeight() / window.getGuiScaledHeight(),
            };
        });
        context.getInput().setCursorPos(cursor[0], cursor[1]);
        context.waitTicks(3);
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

    private static void assertDefaultCatalogAmount(ClientGameTestContext context) {
        var amount = context.computeOnClient(mc -> mc.gui.screen().children().stream()
                .filter(child -> child instanceof EditBox)
                .map(child -> (EditBox) child)
                .skip(1)
                .findFirst()
                .map(EditBox::getValue)
                .orElse(""));
        if (!amount.equals("1")) {
            throw new AssertionError("a new catalog should default to amount 1, but was " + amount);
        }
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
