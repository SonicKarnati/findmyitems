package dev.smpb.findmyitems.retrieval;

import dev.smpb.findmyitems.craft.CraftingPlan;
import dev.smpb.findmyitems.craft.InventorySimulation;
import dev.smpb.findmyitems.craft.RecipeCatalog;
import dev.smpb.findmyitems.config.ModConfig;
import dev.smpb.findmyitems.index.ContainerIndex;
import dev.smpb.findmyitems.model.ContainerKind;
import dev.smpb.findmyitems.model.StackKey;
import dev.smpb.findmyitems.observation.SlotReader;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
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
                                 List<BlockPos> positions, int slot, int count) {
        public SourceSnapshot {
            Objects.requireNonNull(key, "key");
            Objects.requireNonNull(dimension, "dimension");
            Objects.requireNonNull(kind, "kind");
            positions = List.copyOf(positions);
            if (positions.isEmpty()) throw new IllegalArgumentException("source needs an access position");
            if (slot < -1 || count <= 0) throw new IllegalArgumentException("invalid source snapshot");
        }

        public BlockPos position() {
            return positions.getFirst();
        }
    }

    public record ExecutionRequest(CraftingPlan plan, List<SourceSnapshot> sources,
                                   long playerGeneration, long worldGeneration, Mode mode) {
        public ExecutionRequest {
            Objects.requireNonNull(plan, "plan");
            sources = List.copyOf(sources);
            Objects.requireNonNull(mode, "mode");
        }
    }

    public record TransferJournalEntry(StackKey key, SourceSnapshot source, int requested,
                                       int moved, String note) {}

    public enum State {
        IDLE, PREFLIGHT, GATHER, OPEN_SOURCE, WAIT_FOR_SOURCE, VALIDATE_SOURCE,
        TRANSFER, CLOSE_SOURCE, LOCATE_TABLE, OPEN_TABLE, WAIT_FOR_TABLE, VALIDATE_TABLE,
        PLACE_RECIPE, TAKE_OUTPUT, COMPLETE, CANCELLED, FAILED
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
        request = Objects.requireNonNull(next, "request");
        journal.clear();
        sourceIndex = 0;
        callbackPending = false;
        state = State.PREFLIGHT;
        status = ExecutionStatus.CALCULATING;
        return status;
    }

    /** Advances at most one state action. */
    public void tick() {
        if (request == null || state == State.IDLE || state == State.COMPLETE
                || state == State.CANCELLED || state == State.FAILED) return;
        if (generationChanged()) {
            cancel(CancelReason.TARGET_CHANGED);
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
                default -> fail("invalid executor state");
            }
        } catch (RuntimeException exception) {
            fail(exception.getMessage() == null ? "operation failed" : exception.getMessage());
        }
    }

    public ExecutionStatus cancel(CancelReason reason) {
        Objects.requireNonNull(reason, "reason");
        if (request != null) {
            journal.add(new TransferJournalEntry(request.plan().root().item(),
                    sourceIndex < request.sources().size() ? request.sources().get(sourceIndex) : null,
                    0, 0, "cancelled:" + reason.name().toLowerCase()));
        }
        state = State.CANCELLED;
        status = ExecutionStatus.CANCELLED;
        callbackPending = false;
        return status;
    }

    public ExecutionStatus status() { return status; }

    public boolean busy() {
        return state != State.IDLE && state != State.COMPLETE && state != State.CANCELLED && state != State.FAILED;
    }

    public List<TransferJournalEntry> transferJournal() { return List.copyOf(journal); }

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

    private void preflight() {
        var plan = request.plan();
        if (!plan.missing().isEmpty()) {
            status = ExecutionStatus.MISSING;
            state = State.FAILED;
            return;
        }
        var player = Minecraft.getInstance().player;
        if (player == null) {
            status = ExecutionStatus.FULL;
            state = State.FAILED;
            return;
        }
        var slots = new ArrayList<ItemStack>();
        for (int slot = 0; slot < 36; slot++) slots.add(player.getInventory().getItem(slot).copy());
        var capacity = InventorySimulation.simulate(
                InventorySimulation.PlayerInventorySnapshot.of(slots, player.registryAccess()), plan);
        if (!capacity.safe()) {
            status = ExecutionStatus.FULL;
            state = State.FAILED;
            return;
        }
        status = ExecutionStatus.GATHER;
        state = State.GATHER;
    }

    private void gather() {
        if (sourceIndex >= request.sources().size()) {
            if (request.mode() == Mode.GATHER_ONLY) {
                state = State.COMPLETE;
                status = ExecutionStatus.COMPLETE;
            } else {
                state = State.LOCATE_TABLE;
            }
            return;
        }
        state = State.OPEN_SOURCE;
    }

    private void openSource() {
        var source = request.sources().get(sourceIndex);
        var mc = Minecraft.getInstance();
        if (mc.player == null || !ReachabilityService.shared().check(source.position(), TargetKind.CONTAINER).actionable()) {
            cancel(CancelReason.OUT_OF_REACH);
            return;
        }
        callbackPending = true;
        timeout = ACTION_TIMEOUT;
        GhostOpen.openThen(source.position(), () -> {
            var server = mc.getSingleplayerServer();
            if (server == null || mc.player == null) {
                callbackPending = false;
                return;
            }
            var uuid = mc.player.getUUID();
            server.execute(() -> {
                var serverPlayer = server.getPlayerList().getPlayer(uuid);
                var moved = serverPlayer == null ? 0 : source.slot() >= 0
                        ? RetrieveHandler.retrieveSlot(serverPlayer, source.position(), source.dimension(), source.kind(),
                        source.slot(), source.key().itemId(), source.key().componentsJson(), source.count(),
                        config.retrieveDistanceBlocks)
                        : (RetrieveHandler.retrieve(serverPlayer, source.position(), source.dimension(),
                        source.key().itemId(), source.key().componentsJson(), source.count(),
                        config.retrieveDistanceBlocks, source.kind()) ? source.count() : 0);
                mc.execute(() -> {
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
        if (last == null || last.moved() <= 0) {
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
            status = ExecutionStatus.NO_TABLE;
            state = State.FAILED;
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
        state = State.PLACE_RECIPE;
    }

    private void placeRecipe() {
        var mc = Minecraft.getInstance();
        var menu = mc.player == null ? null : mc.player.containerMenu;
        if (!(menu instanceof CraftingMenu crafting)) {
            fail("crafting table closed");
            return;
        }
        var recipe = recipeFor(planRoot());
        if (recipe == null) {
            fail("recipe unavailable");
            return;
        }
        var options = recipe.ingredientOptions();
        for (int grid = 0; grid < options.size() && grid < 9; grid++) {
            var inventorySlot = findInventorySlot(options.get(grid));
            if (inventorySlot < 0) {
                fail("ingredient changed");
                return;
            }
            click(crafting.containerId, inventorySlot, 0);
            click(crafting.containerId, 1 + grid, 0);
        }
        state = State.TAKE_OUTPUT;
    }

    private void takeOutput() {
        var player = Minecraft.getInstance().player;
        if (player == null || !(player.containerMenu instanceof CraftingMenu menu)) {
            fail("crafting table closed");
            return;
        }
        if (!menu.getSlot(0).hasItem()) {
            fail("recipe did not produce output");
            return;
        }
        click(menu.containerId, 0, 0);
        state = State.COMPLETE;
        status = ExecutionStatus.COMPLETE;
    }

    private void click(int menuId, int slot, int button) {
        var mc = Minecraft.getInstance();
        mc.player.containerMenu.clicked(slot, button, ContainerInput.PICKUP, mc.player);
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
        if (player == null) return -1;
        for (int slot = 0; slot < 36; slot++) {
            var stack = player.getInventory().getItem(slot);
            if (stack.isEmpty()) continue;
            var key = new StackKey(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(),
                    SlotReader.serializeComponents(stack.getComponentsPatch(), player.registryAccess()));
            if (choices.contains(key)) return slot < 9 ? 36 + slot : slot;
        }
        return -1;
    }

    private void fail(String note) {
        state = State.FAILED;
        status = ExecutionStatus.FAILED;
        if (request != null) journal.add(new TransferJournalEntry(request.plan().root().item(), null, 0, 0, note));
    }
}
