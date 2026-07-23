package dev.smpb.containersearch.observation;

import com.mojang.serialization.JsonOps;
import dev.smpb.containersearch.index.ContainerIndex;
import dev.smpb.containersearch.model.BlockPosition;
import dev.smpb.containersearch.model.ContainerKind;
import dev.smpb.containersearch.model.ContainerObservation;
import dev.smpb.containersearch.model.SlotSnapshot;
import dev.smpb.containersearch.model.StackKey;
import dev.smpb.containersearch.model.StackSnapshot;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ShulkerBoxMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class ObservationCollector {
    private final ContainerIndex index;

    public ObservationCollector(ContainerIndex index) {
        this.index = index;
        ScreenEvents.BEFORE_INIT.register(this::onScreenInit);
    }

    private void onScreenInit(Minecraft client, Screen screen, int scaledWidth, int scaledHeight) {
        if (!(screen instanceof AbstractContainerScreen<?> containerScreen)) return;

        var menu = containerScreen.getMenu();
        var shapeInfo = resolveShapeInfo(menu);
        if (shapeInfo.isEmpty()) return;

        var player = client.player;
        if (player == null) return;

        var world = player.level();
        var dimension = world.dimension().identifier().toString();

        var hitResult = client.hitResult;
        if (hitResult == null || hitResult.getType() != HitResult.Type.BLOCK) {
            return;
        }

        var blockHit = (BlockHitResult) hitResult;
        var rawPos = blockHit.getBlockPos();

        var resolved = shapeInfo.get();
        var menuKind = resolved.menuKind();
        var storageSlots = resolved.storageSlots();

        var kind = resolveKind(world.getBlockState(rawPos).getBlock());
        if (kind.isEmpty()) return;

        var containerKind = kind.get();
        var positions = findPositions(world, rawPos, containerKind);
        if (positions.isEmpty()) return;

        var shape = ContainerShape.resolve(containerKind, menuKind, storageSlots, positions);
        if (shape.isEmpty()) return;

        var containerShape = shape.get();
        var contentsKey = ObservationBuilder.contentsKey(dimension, containerShape.kind(), containerShape.positions());
        var accessSources = ObservationBuilder.accessSources(dimension, containerShape.kind(), containerShape.positions());
        var slots = readSlots(menu, containerShape.storageSlots(), player);
        var observation = new ContainerObservation(contentsKey, accessSources, slots, Instant.now());

        index.observe(observation);
    }

    private static Optional<ShapeInfo> resolveShapeInfo(AbstractContainerMenu menu) {
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

    private static Optional<ContainerKind> resolveKind(Block block) {
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

    static List<SlotSnapshot> readSlots(AbstractContainerMenu menu, int containerSlots, Player player) {
        var snapshots = new ArrayList<SlotSnapshot>(containerSlots);
        var tooltipContext = player.level() != null
            ? Item.TooltipContext.of(player.level())
            : Item.TooltipContext.EMPTY;

        for (int i = 0; i < containerSlots; i++) {
            var slot = menu.getSlot(i);
            var stack = slot.getItem();
            if (stack.isEmpty()) continue;

            snapshots.add(snapshotStack(stack, i, tooltipContext, player));
        }
        return List.copyOf(snapshots);
    }

    static SlotSnapshot snapshotStack(ItemStack stack, int slotIndex,
                                       Item.TooltipContext tooltipContext, Player player) {
        var itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        var componentsJson = serializeComponents(stack.getComponentsPatch());
        var key = new StackKey(itemId, componentsJson);
        var count = stack.getCount();
        var displayName = stack.getHoverName().getString();
        var tooltip = getTooltipLines(stack, tooltipContext, player);
        return new SlotSnapshot(slotIndex, new StackSnapshot(key, count, displayName, tooltip));
    }

    static String serializeComponents(DataComponentPatch patch) {
        if (patch.isEmpty()) return "{}";
        try {
            var json = DataComponentPatch.CODEC
                .encodeStart(JsonOps.INSTANCE, patch)
                .getOrThrow();
            return dev.smpb.containersearch.model.CanonicalJson.stringify(json);
        } catch (Exception e) {
            return "{}";
        }
    }

    static List<String> getTooltipLines(ItemStack stack,
                                        Item.TooltipContext tooltipContext, Player player) {
        try {
            return stack.getTooltipLines(tooltipContext, player, TooltipFlag.NORMAL)
                .stream()
                .map(Component::getString)
                .toList();
        } catch (Exception e) {
            return List.of();
        }
    }

    private record ShapeInfo(MenuKind menuKind, int storageSlots) {}
}
