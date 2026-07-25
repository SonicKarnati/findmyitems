package dev.smpb.findmyitems.observation;

import dev.smpb.findmyitems.FindMyItemsClient;
import dev.smpb.findmyitems.config.ModConfig;
import dev.smpb.findmyitems.index.ContainerIndex;
import dev.smpb.findmyitems.index.IndexedContainer;
import dev.smpb.findmyitems.model.BlockPosition;
import dev.smpb.findmyitems.model.ContainerKind;
import dev.smpb.findmyitems.model.ContainerObservation;
import dev.smpb.findmyitems.model.SourceKey;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ShulkerBoxMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.ShulkerBoxBlock;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class ObservationCollector {
    /** Containers re-read per tick. Small enough that a big base costs a slice, not a stutter. */
    private static final int RESCAN_BATCH = 8;

    private final ContainerIndex index;
    private final ModConfig config;
    private final Deque<SourceKey> rescanQueue = new ArrayDeque<>();
    private final Map<SourceKey, IndexedContainer> rescanTargets = new HashMap<>();
    private AbstractContainerScreen<?> pendingScreen;
    private int scanCounter;

    public ObservationCollector(ContainerIndex index, ModConfig config) {
        this.index = index;
        this.config = config;
        ScreenEvents.AFTER_INIT.register(this::onAfterScreenInit);
        ClientTickEvents.END_CLIENT_TICK.register(this::onTick);
    }

    private void onAfterScreenInit(Minecraft client, Screen screen, int scaledWidth, int scaledHeight) {
        if (!FindMyItemsClient.activeWorld()) return;
        if (screen instanceof AbstractContainerScreen<?> containerScreen) {
            pendingScreen = containerScreen;
        }
    }

    private void onTick(Minecraft client) {
        if (!FindMyItemsClient.activeWorld()) {
            pendingScreen = null;
            rescanQueue.clear();
            return;
        }

        processPendingObservation(client);

        var interval = config.rescanIntervalSeconds * 20;
        if (interval > 0 && ++scanCounter >= interval) {
            scanCounter = 0;
            queueContainerRescan();
        }
        drainRescanQueue(client);
    }

    private void processPendingObservation(Minecraft client) {
        var screen = pendingScreen;
        if (screen == null) return;
        pendingScreen = null;

        var menu = screen.getMenu();

        var player = client.player;
        if (player == null) return;

        var world = player.level();
        var dimension = world.dimension().identifier().toString();

        var cachedPos = PositionCache.pos();
        if (cachedPos.isEmpty()) return;

        var rawPos = cachedPos.get();

        var shapeInfo = resolveShapeInfo(menu);
        if (shapeInfo.isEmpty()) return;

        var resolved = shapeInfo.get();
        var storageSlots = resolved.storageSlots;

        var block = world.getBlockState(rawPos).getBlock();
        var kind = resolveKind(block);
        if (kind.isEmpty()) return;

        var containerKind = kind.get();
        var positions = findPositions(world, rawPos, containerKind);
        if (positions.isEmpty()) return;

        var shape = ContainerShape.resolve(containerKind, resolved.menuKind, storageSlots, positions);
        if (shape.isEmpty()) return;

        var containerShape = shape.get();
        var contentsKey = ObservationBuilder.contentsKey(dimension, containerShape.kind(), containerShape.positions());
        var accessSources = ObservationBuilder.accessSources(dimension, containerShape.kind(), containerShape.positions());
        var slots = SlotReader.readMenuSlots(menu, storageSlots, player);
        var observation = new ContainerObservation(contentsKey, accessSources, slots, Instant.now());

        index.observe(observation);
    }

    /**
     * Lines up every remembered container to be re-read.
     *
     * <p>Queued rather than done here: re-reading a large base in one go means walking every slot
     * of every container in range, plus their nested shulkers, inside a single server tick. That is
     * a visible hitch every interval. {@link #RESCAN_BATCH} containers a tick spreads the same work
     * flat, and the queue is rebuilt from scratch each interval so it cannot drift out of date.
     */
    private void queueContainerRescan() {
        rescanQueue.clear();
        rescanTargets.clear();
        for (var container : index.snapshot().containers()) {
            for (var source : container.accessSources()) {
                if (rescanTargets.put(source, container) == null) rescanQueue.add(source);
            }
        }
    }

    private void drainRescanQueue(Minecraft client) {
        if (rescanQueue.isEmpty()) return;

        var server = client.getSingleplayerServer();
        var player = client.player;
        if (server == null || player == null) {
            rescanQueue.clear();
            return;
        }

        var batch = new ArrayList<SourceKey>(RESCAN_BATCH);
        for (int i = 0; i < RESCAN_BATCH && !rescanQueue.isEmpty(); i++) {
            batch.add(rescanQueue.poll());
        }

        var searchBlocks = config.searchDistanceBlocks;
        var maxDistSq = (double) searchBlocks * searchBlocks;
        var useDistanceLimit = searchBlocks > 0;

        var targets = Map.copyOf(rescanTargets);

        server.execute(() -> {
            var observations = new ArrayList<ContainerObservation>();
            var missingSources = new ArrayList<SourceKey>();

            for (var source : batch) {
                var container = targets.get(source);
                if (container != null) {
                    if (source.positions().isEmpty()) continue;
                    var pos = source.positions().getFirst();
                    var mcPos = new BlockPos(pos.x(), pos.y(), pos.z());
                    var dim = source.dimension();
                    var worldKey = ResourceKey.create(Registries.DIMENSION, Identifier.parse(dim));
                    var world = server.getLevel(worldKey);
                    if (world == null || !world.isLoaded(mcPos)) continue;

                    if (useDistanceLimit) {
                        var dx = pos.x() + 0.5 - player.getX();
                        var dy = pos.y() + 0.5 - player.getY();
                        var dz = pos.z() + 0.5 - player.getZ();
                        if (dx * dx + dy * dy + dz * dz > maxDistSq) continue;
                    }

                    var block = world.getBlockState(mcPos).getBlock();
                    var kind = resolveKind(block);
                    if (kind.isEmpty() || kind.get() != source.kind()) {
                        missingSources.add(source);
                        continue;
                    }

                    var be = world.getBlockEntity(mcPos);
                    if (!(be instanceof Container containerBE) || be.isRemoved()) {
                        missingSources.add(source);
                        continue;
                    }

                    var serverPlayer = server.getPlayerList().getPlayer(player.getUUID());
                    if (serverPlayer == null) continue;

                    var slots = SlotReader.readContainerSlots(containerBE, serverPlayer);

                    var contentsKey = source.positions().size() == container.contentsKey().positions().size()
                            ? container.contentsKey()
                            : SourceKey.storage(dim, source.kind(), source.positions());
                    var obs = new ContainerObservation(contentsKey, List.of(source), slots, Instant.now());
                    observations.add(obs);
                }
            }

            client.execute(() -> {
                for (var obs : observations) {
                    index.observe(obs);
                }
                for (var source : missingSources) {
                    index.markMissing(source);
                }
            });
        });
    }

    private static Optional<ShapeInfo> resolveShapeInfo(net.minecraft.world.inventory.AbstractContainerMenu menu) {
        if (menu instanceof ChestMenu chestMenu) {
            var rows = chestMenu.getRowCount();
            var containerSlots = rows * 9;
            if (containerSlots == 27 || containerSlots == 54) {
                return Optional.of(new ShapeInfo(MenuKind.GENERIC_STORAGE, containerSlots));
            }
            return Optional.empty();
        }
        if (menu instanceof ShulkerBoxMenu) {
            return Optional.of(new ShapeInfo(MenuKind.SHULKER, 27));
        }
        return Optional.empty();
    }

    static Optional<ContainerKind> resolveKind(Block block) {
        if (block == Blocks.CHEST) return Optional.of(ContainerKind.CHEST);
        if (block == Blocks.TRAPPED_CHEST) return Optional.of(ContainerKind.TRAPPED_CHEST);
        if (block == Blocks.BARREL) return Optional.of(ContainerKind.BARREL);
        if (block == Blocks.ENDER_CHEST) return Optional.of(ContainerKind.ENDER_CHEST);
        if (block instanceof ShulkerBoxBlock) return Optional.of(ContainerKind.SHULKER_BOX);
        return Optional.empty();
    }

    static List<BlockPosition> findPositions(Level world, net.minecraft.core.BlockPos pos, ContainerKind kind) {
        var bp = new BlockPosition(pos.getX(), pos.getY(), pos.getZ());

        if (kind == ContainerKind.CHEST || kind == ContainerKind.TRAPPED_CHEST) {
            var state = world.getBlockState(pos);
            var blockType = ChestBlock.getBlockType(state);
            return switch (blockType) {
                case SINGLE -> List.of(bp);
                case FIRST, SECOND -> {
                    var connected = ChestBlock.getConnectedBlockPos(pos, state);
                    var other = new BlockPosition(connected.getX(), connected.getY(), connected.getZ());
                    yield List.of(bp, other);
                }
            };
        }

        return List.of(bp);
    }

    private record ShapeInfo(MenuKind menuKind, int storageSlots) {}
}
