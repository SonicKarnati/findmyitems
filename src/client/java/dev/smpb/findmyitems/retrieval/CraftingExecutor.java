package dev.smpb.findmyitems.retrieval;

import dev.smpb.findmyitems.craft.CraftingPlan;
import dev.smpb.findmyitems.craft.InventorySimulation;
import dev.smpb.findmyitems.craft.RecipeCatalog;
import dev.smpb.findmyitems.config.ModConfig;
import dev.smpb.findmyitems.index.ContainerIndex;
import dev.smpb.findmyitems.model.ContainerKind;
import dev.smpb.findmyitems.model.SourceKey;
import dev.smpb.findmyitems.model.StackKey;
import dev.smpb.findmyitems.observation.SlotReader;
import dev.smpb.findmyitems.gui.CatalogScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.LongSupplier;

/**
 * Tick-sized coordinator for gathering a plan and letting vanilla menus craft it.
 *
 * <p>The executor never edits a remote container. Every source transfer is delegated to the server
 * handler, and every recipe transfer is a normal menu click. A transition is deliberately kept to
 * one action so a closed screen, changed source, or changed player cannot be hidden by a long loop.
 */
public final class CraftingExecutor {
    private static final int ACTION_TIMEOUT = 40;

    public enum Mode { GATHER_ONLY, GATHER_AND_CRAFT }

    public enum CancelReason {
        SCREEN_CLOSED,
        SELECTION_CHANGED,
        QUERY_CHANGED,
        OUT_OF_REACH,
        DIMENSION_CHANGED,
        PLAYER_DIED,
        SOURCE_CHANGED,
        TARGET_CHANGED,
        INVENTORY_FULL,
        SUPERSEDED
    }

    public record SourceSnapshot(StackKey key, String dimension, ContainerKind kind,
                                 List<BlockPos> positions, List<Integer> path, int count,
                                 SourceKey contentsKey, List<SourceKey> accessSources,
                                 ItemStack template) {
        public SourceSnapshot(StackKey key, String dimension, ContainerKind kind,
                              List<BlockPos> positions, int slot, int count) {
            this(key, dimension, kind, positions, List.of(slot), count,
                    storageKey(dimension, kind, positions),
                    List.of(storageKey(dimension, kind, positions)), plainTemplate(key));
        }

        public SourceSnapshot {
            Objects.requireNonNull(key, "key");
            Objects.requireNonNull(dimension, "dimension");
            Objects.requireNonNull(kind, "kind");
            positions = List.copyOf(positions);
            path = List.copyOf(path);
            Objects.requireNonNull(contentsKey, "contentsKey");
            accessSources = List.copyOf(accessSources);
            template = template.copyWithCount(1);
            if (positions.isEmpty() || path.isEmpty() || path.stream().anyMatch(slot -> slot < 0)
                    || count <= 0) throw new IllegalArgumentException("invalid source snapshot");
        }

        public BlockPos position() {
            return positions.getFirst();
        }

        public SourceSnapshot withCount(int nextCount) {
            return new SourceSnapshot(key, dimension, kind, positions, path, nextCount,
                    contentsKey, accessSources, template);
        }
    }

    public record ExecutionRequest(CraftingPlan plan, List<SourceSnapshot> sources,
                                   StackKey target, long targetGeneration,
                                   long playerGeneration, long worldGeneration, Mode mode) {
        public ExecutionRequest {
            Objects.requireNonNull(plan, "plan");
            sources = List.copyOf(sources);
            Objects.requireNonNull(target, "target");
            Objects.requireNonNull(mode, "mode");
        }

        public ExecutionRequest(CraftingPlan plan, List<SourceSnapshot> sources,
                                long playerGeneration, long worldGeneration, Mode mode) {
            this(plan, sources, plan.root().item(), 0, playerGeneration, worldGeneration, mode);
        }
    }

    public record TransferJournalEntry(StackKey key, SourceSnapshot source, int requested,
                                       int moved, String note) {}

    public enum State {
        IDLE, PREFLIGHT, GATHER, OPEN_SOURCE, WAIT_FOR_SOURCE, VALIDATE_SOURCE,
        TRANSFER, CLOSE_SOURCE, LOCATE_TABLE, OPEN_TABLE, WAIT_FOR_TABLE, VALIDATE_TABLE,
            PLACE_RECIPE, TAKE_OUTPUT, WAIT_OUTPUT_SYNC, CLOSE_TABLE, COMPLETE, CANCELLED, FAILED
    }

    private static SourceKey storageKey(String dimension, ContainerKind kind, List<BlockPos> positions) {
        var sourcePositions = positions.stream()
                .map(pos -> new dev.smpb.findmyitems.model.BlockPosition(pos.getX(), pos.getY(), pos.getZ()))
                .toList();
        return SourceKey.storage(dimension, kind, sourcePositions);
    }

    private static ItemStack plainTemplate(StackKey key) {
        return net.minecraft.core.registries.BuiltInRegistries.ITEM.get(
                net.minecraft.resources.Identifier.parse(key.itemId())).map(ItemStack::new)
                .orElse(ItemStack.EMPTY);
    }

    private final ContainerIndex index;
    private final ModConfig config;
    private final LongSupplier playerGeneration;
    private final LongSupplier worldGeneration;
    private final List<TransferJournalEntry> journal = new ArrayList<>();
    private ExecutionRequest request;
    private ExecutionStatus status = ExecutionStatus.COMPLETE;
    private State state = State.IDLE;
    private int sourceIndex;
    private int timeout;
    private boolean callbackPending;
    private volatile long runToken;
    private List<CraftingPlan.Node> craftNodes = List.of();
    private int craftNodeIndex;
    private int craftsRemaining;
    private int ingredientIndex;
    private boolean carryingIngredient;
    private boolean ingredientPlaced;
    private int sourceInventorySlot;
    private RecipeCatalog.RecipeDefinition activeRecipe;
    private boolean tableOpen;
    private List<StackKey> tableRequiredMaterials = List.of();
    private int actionsThisTick;
    private int actionsLastTick;
    private boolean menuActionPending;
    private int outputWait;
    private String failureDiagnostics = "";
    private int expectedMenuId = -1;
    private LongSupplier targetGenerationSupplier = () -> request == null ? -1 : request.targetGeneration();

    public CraftingExecutor(ContainerIndex index, ModConfig config) {
        this(index, config, CraftingExecutor::currentPlayerGeneration,
                CraftingExecutor::currentWorldGeneration);
    }

    public CraftingExecutor(ContainerIndex index, ModConfig config,
                            LongSupplier playerGeneration, LongSupplier worldGeneration) {
        this.index = Objects.requireNonNull(index, "index");
        this.config = Objects.requireNonNull(config, "config");
        this.playerGeneration = Objects.requireNonNull(playerGeneration, "playerGeneration");
        this.worldGeneration = Objects.requireNonNull(worldGeneration, "worldGeneration");
    }

    public ExecutionStatus start(ExecutionRequest next) {
        if (state != State.IDLE && state != State.COMPLETE && state != State.CANCELLED && state != State.FAILED) {
            status = ExecutionStatus.BUSY;
            return status;
        }
        runToken++;
        request = Objects.requireNonNull(next, "request");
        journal.clear();
        sourceIndex = 0;
        callbackPending = false;
        menuActionPending = false;
        craftNodes = List.of();
        craftNodeIndex = 0;
        tableOpen = false;
        state = State.PREFLIGHT;
        status = ExecutionStatus.CALCULATING;
        return status;
    }

    /** Replaces an active request and records the distinct supersession reason. */
    public ExecutionStatus replace(ExecutionRequest next) {
        if (busy()) cancel(CancelReason.SUPERSEDED);
        return start(next);
    }

    /** Advances at most one state action. */
    public void tick() {
        if (request == null || state == State.IDLE || state == State.COMPLETE
                || state == State.CANCELLED || state == State.FAILED) return;
        actionsThisTick = 0;
        if (menuActionPending) return;
        if (!request.target().equals(request.plan().root().item()) || request.targetGeneration() < 0) {
            cancel(CancelReason.TARGET_CHANGED);
            return;
        }
        if (request.targetGeneration() > 0 && request.targetGeneration() != targetGenerationSupplier.getAsLong()) {
            cancel(CancelReason.TARGET_CHANGED);
            return;
        }
        if (generationChanged()) {
            cancel(generationCancelReason());
            return;
        }
        if (timeout > 0 && --timeout == 0) {
            fail("operation timed out");
            return;
        }

        try {
            switch (state) {
                case PREFLIGHT -> preflight();
                case GATHER -> gather();
                case OPEN_SOURCE -> openSource();
                case WAIT_FOR_SOURCE -> waitForSource();
                case VALIDATE_SOURCE -> state = State.TRANSFER;
                case TRANSFER -> transfer();
                case CLOSE_SOURCE -> closeSource();
                case LOCATE_TABLE -> locateTable();
                case OPEN_TABLE -> openTable();
                case WAIT_FOR_TABLE -> waitForTable();
                case VALIDATE_TABLE -> validateTable();
                case PLACE_RECIPE -> placeRecipe();
                case TAKE_OUTPUT -> takeOutput();
                case WAIT_OUTPUT_SYNC -> waitOutputSync();
                case CLOSE_TABLE -> closeTable();
                default -> fail("invalid executor state");
            }
        } catch (RuntimeException exception) {
            fail(exception.getMessage() == null ? "operation failed" : exception.getMessage());
        } finally {
            actionsLastTick = actionsThisTick;
        }
    }

    public ExecutionStatus cancel(CancelReason reason) {
        Objects.requireNonNull(reason, "reason");
        if (request != null) {
            journal.add(new TransferJournalEntry(request.plan().root().item(),
                    sourceIndex < request.sources().size() ? request.sources().get(sourceIndex) : null,
                    0, 0, "cancelled:" + reason.name().toLowerCase()));
        }
        runToken++;
        GhostOpen.cancel();
        closeMenu();
        state = State.CANCELLED;
        status = ExecutionStatus.CANCELLED;
        callbackPending = false;
        menuActionPending = false;
        return status;
    }

    public ExecutionStatus status() { return status; }

    public boolean busy() {
        return state != State.IDLE && state != State.COMPLETE && state != State.CANCELLED && state != State.FAILED;
    }

    public List<TransferJournalEntry> transferJournal() { return List.copyOf(journal); }

    public List<StackKey> tableRequiredMaterials() { return tableRequiredMaterials; }

    public int actionsLastTick() { return actionsLastTick; }

    public void setTargetGenerationSupplier(LongSupplier supplier) {
        targetGenerationSupplier = Objects.requireNonNull(supplier, "supplier");
    }

    public String menuDiagnostics() {
        var player = Minecraft.getInstance().player;
        if (player == null) return "no-player";
        var menu = player.containerMenu;
        var slots = new ArrayList<Integer>();
        for (int slot = 0; slot < Math.min(10, menu.slots.size()); slot++) {
            slots.add(menu.getSlot(slot).getItem().getCount());
        }
        return menu.getClass().getSimpleName() + " slots=" + slots + " carried="
                + menu.getCarried().getCount() + " recipe="
                + (activeRecipe == null ? "none" : activeRecipe.gridSlots());
    }

    public String failureDiagnostics() { return failureDiagnostics; }

    public State state() { return state; }

    public static long currentPlayerGeneration() {
        var player = Minecraft.getInstance().player;
        if (player == null) return -1L;
        return java.util.Objects.hash(player.blockPosition(), player.level().dimension());
    }

    public static long currentWorldGeneration() {
        var level = Minecraft.getInstance().level;
        return level == null ? -1L : level.dimension().hashCode();
    }

    private boolean generationChanged() {
        return request.playerGeneration() != playerGeneration.getAsLong()
                || request.worldGeneration() != worldGeneration.getAsLong();
    }

    private CancelReason generationCancelReason() {
        var mc = Minecraft.getInstance();
        if (mc.player == null) return CancelReason.PLAYER_DIED;
        if (request.worldGeneration() != worldGeneration.getAsLong()) return CancelReason.DIMENSION_CHANGED;
        return CancelReason.OUT_OF_REACH;
    }

    private void preflight() {
        var plan = request.plan();
        if (!plan.missing().isEmpty()) {
            fail(ExecutionStatus.MISSING, "missing materials");
            return;
        }
        var player = Minecraft.getInstance().player;
        if (player == null) {
            fail(ExecutionStatus.FULL, "player unavailable");
            return;
        }
        var slots = new ArrayList<ItemStack>();
        for (int slot = 0; slot < 36; slot++) slots.add(player.getInventory().getItem(slot).copy());
        var snapshot = InventorySimulation.PlayerInventorySnapshot.of(slots, player.registryAccess());
        var current = new LinkedHashMap<StackKey, Long>();
        for (var stack : slots) {
            if (stack.isEmpty()) continue;
            var key = new StackKey(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(),
                    SlotReader.serializeComponents(stack.getComponentsPatch(), player.registryAccess()));
            current.merge(key, (long) stack.getCount(), Math::addExact);
        }
        var needed = new LinkedHashMap<StackKey, Long>();
        for (var entry : plan.consumedDelta().entrySet()) {
            needed.put(entry.getKey(), Math.max(0, entry.getValue() - current.getOrDefault(entry.getKey(), 0L)));
        }
        var gathered = new LinkedHashMap<StackKey, Long>();
        var activeSources = new ArrayList<SourceSnapshot>();
        for (var source : request.sources()) {
            var left = needed.getOrDefault(source.key(), 0L) - gathered.getOrDefault(source.key(), 0L);
            if (left <= 0) continue;
            var take = (int) Math.min(left, source.count());
            activeSources.add(source.withCount(take));
            gathered.merge(source.key(), (long) take, Math::addExact);
        }
        if (needed.entrySet().stream().anyMatch(entry -> entry.getValue() > gathered.getOrDefault(entry.getKey(), 0L))) {
            fail(ExecutionStatus.MISSING, "source changed");
            return;
        }
        var templates = new LinkedHashMap<StackKey, ItemStack>();
        for (var source : activeSources) templates.putIfAbsent(source.key(), source.template());
        var capacity = InventorySimulation.simulateAfterGather(snapshot, plan, gathered, templates);
        if (!capacity.safe()) {
            fail(ExecutionStatus.FULL, capacity.failureReason());
            return;
        }
        request = new ExecutionRequest(plan, activeSources, request.target(), request.targetGeneration(),
                request.playerGeneration(), request.worldGeneration(), request.mode());
        craftNodes = postOrder(plan.root()).stream().filter(node -> node.craftCount() > 0).toList();
        craftNodeIndex = 0;
        tableRequiredMaterials = craftNodes.stream()
                .filter(node -> {
                    var recipe = recipeFor(node.item());
                    return recipe != null && recipe.station() == RecipeCatalog.Station.CRAFTING_TABLE;
                })
                .map(CraftingPlan.Node::item).distinct().toList();
        status = ExecutionStatus.GATHER;
        state = State.GATHER;
    }

    private void gather() {
        if (sourceIndex >= request.sources().size()) {
            if (request.mode() == Mode.GATHER_ONLY) {
                advanceCraft();
            } else {
                advanceCraft();
            }
            return;
        }
        state = State.OPEN_SOURCE;
    }

    private void openSource() {
        var source = request.sources().get(sourceIndex);
        var token = runToken;
        var mc = Minecraft.getInstance();
        if (mc.player == null || !ReachabilityService.shared().check(source.position(), TargetKind.CONTAINER).actionable()) {
            cancel(CancelReason.OUT_OF_REACH);
            return;
        }
        callbackPending = true;
        timeout = ACTION_TIMEOUT;
        GhostOpen.openThen(source.position(), () -> isCurrent(token), () -> {
            if (!isCurrent(token)) return;
            var server = mc.getSingleplayerServer();
            if (server == null || mc.player == null) {
                callbackPending = false;
                return;
            }
            var uuid = mc.player.getUUID();
            server.execute(() -> {
                if (!isCurrent(token)) return;
                var serverPlayer = server.getPlayerList().getPlayer(uuid);
                var moved = serverPlayer == null ? 0 : RetrieveHandler.retrievePath(serverPlayer,
                        source.position(), source.dimension(), source.kind(), source.path(),
                        source.key().itemId(), source.key().componentsJson(), source.count(),
                        config.retrieveDistanceBlocks);
                var observation = moved > 0 && serverPlayer != null
                        ? RetrieveHandler.observe(serverPlayer, source.position(), source.kind(),
                        source.contentsKey(), source.accessSources()) : null;
                mc.execute(() -> {
                    if (!isCurrent(token)) return;
                    if (observation != null) index.observe(observation);
                    journal.add(new TransferJournalEntry(source.key(), source, source.count(), moved,
                            moved == source.count() ? "transferred" : "source-changed"));
                    callbackPending = false;
                });
            });
        });
        state = State.WAIT_FOR_SOURCE;
    }

    private void waitForSource() {
        if (!callbackPending) state = State.VALIDATE_SOURCE;
    }

    private void transfer() {
        var last = journal.isEmpty() ? null : journal.getLast();
        if (last == null || last.moved() != last.requested()) {
            cancel(CancelReason.SOURCE_CHANGED);
            return;
        }
        state = State.CLOSE_SOURCE;
    }

    private void closeSource() {
        sourceIndex++;
        state = State.GATHER;
        timeout = 0;
    }

    private void locateTable() {
        var player = Minecraft.getInstance().player;
        if (player == null) {
            cancel(CancelReason.PLAYER_DIED);
            return;
        }
        var radius = Math.max(4, config.retrieveDistanceBlocks);
        var center = player.blockPosition();
        var table = BlockPos.betweenClosedStream(center.offset(-radius, -radius, -radius),
                        center.offset(radius, radius, radius))
                .filter(pos -> player.level().getBlockState(pos).is(net.minecraft.world.level.block.Blocks.CRAFTING_TABLE))
                .filter(pos -> ReachabilityService.shared().check(pos, TargetKind.CRAFTING_TABLE).actionable())
                .findFirst();
        if (table.isEmpty()) {
            fail(ExecutionStatus.NO_TABLE, "no reachable crafting table");
            return;
        }
        tablePosition = table.get().immutable();
        state = State.OPEN_TABLE;
    }

    private BlockPos tablePosition;

    private void openTable() {
        var mc = Minecraft.getInstance();
        if (mc.gameMode == null || mc.player == null) {
            fail("no game mode");
            return;
        }
        var hit = new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(tablePosition),
                net.minecraft.core.Direction.UP, tablePosition, false);
        actionsThisTick++;
        mc.gameMode.useItemOn(mc.player, net.minecraft.world.InteractionHand.MAIN_HAND, hit);
        timeout = ACTION_TIMEOUT;
        state = State.WAIT_FOR_TABLE;
    }

    private void waitForTable() {
        var menu = Minecraft.getInstance().player == null ? null : Minecraft.getInstance().player.containerMenu;
        if (menu instanceof CraftingMenu) state = State.VALIDATE_TABLE;
    }

    private void validateTable() {
        var player = Minecraft.getInstance().player;
        if (!(player.containerMenu instanceof CraftingMenu)) {
            fail("crafting table menu did not open");
            return;
        }
        status = ExecutionStatus.CRAFT;
        tableOpen = true;
        expectedMenuId = player.containerMenu.containerId;
        ingredientIndex = 0;
        carryingIngredient = false;
        ingredientPlaced = false;
        state = State.PLACE_RECIPE;
    }

    private void placeRecipe() {
        var mc = Minecraft.getInstance();
        var menu = mc.player == null ? null : mc.player.containerMenu;
        if (!(menu instanceof CraftingMenu) && !(menu instanceof InventoryMenu)) {
            fail("crafting menu closed");
            return;
        }
        if (menu.containerId != expectedMenuId) {
            cancel(CancelReason.SCREEN_CLOSED);
            return;
        }
        var options = activeRecipe.ingredientOptions();
        if (ingredientIndex >= options.size()) {
            outputWait = 10;
            state = State.TAKE_OUTPUT;
            return;
        }
        if (!carryingIngredient) {
            var inventorySlot = findInventorySlot(options.get(ingredientIndex));
            if (inventorySlot < 0) {
                fail("ingredient changed");
                return;
            }
            sourceInventorySlot = inventorySlot;
            click(inventorySlot, 0);
            carryingIngredient = true;
        } else if (!ingredientPlaced) {
            click(1 + activeRecipe.gridSlots().get(ingredientIndex), 1);
            ingredientPlaced = true;
        } else {
            click(sourceInventorySlot, 0);
            carryingIngredient = false;
            ingredientPlaced = false;
            ingredientIndex++;
        }
    }

    private void takeOutput() {
        var player = Minecraft.getInstance().player;
        if (player == null || (!(player.containerMenu instanceof CraftingMenu)
                && !(player.containerMenu instanceof InventoryMenu))) {
            fail("crafting table closed");
            return;
        }
        var menu = player.containerMenu;
        if (menu.containerId != expectedMenuId) {
            cancel(CancelReason.SCREEN_CLOSED);
            return;
        }
        if (!menu.getSlot(0).hasItem()) {
            if (outputWait-- > 0) return;
            fail("recipe did not produce output");
            return;
        }
        click(0, 0);
        craftsRemaining--;
        if (craftsRemaining > 0) {
            ingredientIndex = 0;
            carryingIngredient = false;
            ingredientPlaced = false;
            state = State.PLACE_RECIPE;
            return;
        }
        outputWait = 5;
        state = State.WAIT_OUTPUT_SYNC;
    }

    private void waitOutputSync() {
        if (outputWait-- > 0) return;
        craftNodeIndex++;
        if (tableOpen && nextRecipeIsInventoryOrDone()) {
            state = State.CLOSE_TABLE;
        } else {
            advanceCraft();
        }
    }

    private void closeTable() {
        closeMenu();
        tableOpen = false;
        advanceCraft();
    }

    private void advanceCraft() {
        while (craftNodeIndex < craftNodes.size() && craftNodes.get(craftNodeIndex).craftCount() <= 0) {
            craftNodeIndex++;
        }
        if (craftNodeIndex >= craftNodes.size()) {
            if (tableOpen) state = State.CLOSE_TABLE;
            else {
                state = State.COMPLETE;
                status = ExecutionStatus.COMPLETE;
                if (!(Minecraft.getInstance().gui.screen() instanceof CatalogScreen)) {
                    Minecraft.getInstance().gui.setScreen(new CatalogScreen(index, config));
                }
            }
            return;
        }
        var node = craftNodes.get(craftNodeIndex);
        activeRecipe = recipeFor(node.item());
        if (activeRecipe == null) {
            fail("recipe unavailable");
            return;
        }
        craftsRemaining = Math.toIntExact(node.craftCount());
        ingredientIndex = 0;
        carryingIngredient = false;
        ingredientPlaced = false;
        if (request.mode() == Mode.GATHER_ONLY && activeRecipe.station() == RecipeCatalog.Station.CRAFTING_TABLE) {
            craftNodeIndex++;
            advanceCraft();
        } else if (activeRecipe.station() == RecipeCatalog.Station.CRAFTING_TABLE) {
            state = tableOpen ? State.PLACE_RECIPE : State.LOCATE_TABLE;
        } else if (tableOpen) {
            state = State.CLOSE_TABLE;
        } else {
            status = ExecutionStatus.CRAFT;
            var player = Minecraft.getInstance().player;
            expectedMenuId = player == null ? -1 : player.containerMenu.containerId;
            state = State.PLACE_RECIPE;
        }
    }

    private boolean nextRecipeIsInventoryOrDone() {
        if (craftNodeIndex >= craftNodes.size()) return true;
        var recipe = recipeFor(craftNodes.get(craftNodeIndex).item());
        return recipe == null || recipe.station() == RecipeCatalog.Station.INVENTORY;
    }

    private static List<CraftingPlan.Node> postOrder(CraftingPlan.Node node) {
        var nodes = new ArrayList<CraftingPlan.Node>();
        for (var child : node.children()) nodes.addAll(postOrder(child));
        nodes.add(node);
        return nodes;
    }

    private void click(int slot, int button) {
        var mc = Minecraft.getInstance();
        if (mc.player == null || mc.getSingleplayerServer() == null) {
            fail("menu action unavailable");
            return;
        }
        actionsThisTick++;
        menuActionPending = true;
        var token = runToken;
        var uuid = mc.player.getUUID();
        var menuId = mc.player.containerMenu.containerId;
        mc.getSingleplayerServer().execute(() -> {
            var serverPlayer = mc.getSingleplayerServer().getPlayerList().getPlayer(uuid);
            var outputOverflow = false;
            var actionFailure = serverPlayer == null || !isCurrent(token)
                    || serverPlayer.containerMenu.containerId != menuId;
            if (serverPlayer != null && isCurrent(token) && serverPlayer.containerMenu.containerId == menuId) {
                serverPlayer.containerMenu.clicked(slot, button, ContainerInput.PICKUP, serverPlayer);
                if (slot == 0 && !serverPlayer.containerMenu.getCarried().isEmpty()) {
                    var carried = serverPlayer.containerMenu.getCarried().copy();
                    serverPlayer.getInventory().add(carried);
                    serverPlayer.containerMenu.setCarried(carried);
                    outputOverflow = !carried.isEmpty();
                }
                serverPlayer.containerMenu.broadcastChanges();
                serverPlayer.containerMenu.broadcastFullState();
            }
            var failedToInsertOutput = outputOverflow;
            mc.execute(() -> {
                if (runToken == token) {
                    menuActionPending = false;
                    if (actionFailure) fail("menu action rejected");
                    else if (failedToInsertOutput) fail(ExecutionStatus.FULL, "crafted output did not fit");
                }
            });
        });
    }

    private RecipeCatalog.RecipeDefinition recipeFor(StackKey output) {
        var mc = Minecraft.getInstance();
        if (mc.getSingleplayerServer() == null || mc.level == null) return null;
        return RecipeCatalog.from(mc.getSingleplayerServer().getRecipeManager(), mc.level).recipesFor(output)
                .stream().findFirst().orElse(null);
    }

    private StackKey planRoot() { return request.plan().root().item(); }

    private int findInventorySlot(List<StackKey> choices) {
        var player = Minecraft.getInstance().player;
        var inventory = player == null ? null : player.getInventory();
        var server = Minecraft.getInstance().getSingleplayerServer();
        if (server != null && player != null) {
            var serverPlayer = server.getPlayerList().getPlayer(player.getUUID());
            if (serverPlayer != null) inventory = serverPlayer.getInventory();
        }
        if (inventory == null) return -1;
        for (int slot = 0; slot < 36; slot++) {
            var stack = inventory.getItem(slot);
            if (stack.isEmpty()) continue;
            var key = new StackKey(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(),
                    SlotReader.serializeComponents(stack.getComponentsPatch(), player.registryAccess()));
            if (choices.contains(key)) {
                var menu = Minecraft.getInstance().player == null
                        ? null : Minecraft.getInstance().player.containerMenu;
                var playerInventoryOffset = menu instanceof net.minecraft.world.inventory.CraftingMenu ? 10 : 9;
                return slot < 9 ? playerInventoryOffset + 27 + slot : playerInventoryOffset + slot - 9;
            }
        }
        return -1;
    }

    private void fail(String note) {
        fail(ExecutionStatus.FAILED, note);
    }

    private void fail(ExecutionStatus failureStatus, String note) {
        failureDiagnostics = menuDiagnostics();
        runToken++;
        GhostOpen.cancel();
        closeMenu();
        state = State.FAILED;
        status = failureStatus;
        menuActionPending = false;
        if (request != null) journal.add(new TransferJournalEntry(request.plan().root().item(), null, 0, 0, note));
    }

    private boolean isCurrent(long token) {
        return runToken == token && busy();
    }

    private void closeMenu() {
        var mc = Minecraft.getInstance();
        var player = mc.player;
        if (player == null || player.containerMenu == player.inventoryMenu) return;
        if (!player.containerMenu.getCarried().isEmpty()) return;
        var connection = mc.getConnection();
        if (connection != null) {
            actionsThisTick++;
            connection.send(new net.minecraft.network.protocol.game.ServerboundContainerClosePacket(
                    player.containerMenu.containerId));
        }
        player.containerMenu = player.inventoryMenu;
        if (!(mc.gui.screen() instanceof dev.smpb.findmyitems.gui.CatalogScreen)) mc.gui.setScreen(null);
    }
}
