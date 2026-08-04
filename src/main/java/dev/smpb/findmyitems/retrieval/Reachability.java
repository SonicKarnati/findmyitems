package dev.smpb.findmyitems.retrieval;

import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.EnderChestBlock;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import dev.smpb.findmyitems.model.ContainerKind;

import java.util.Objects;

/** Shared, environment-independent facts used by the client prediction and server authority. */
public final class Reachability {
    private static final double VANILLA_PADDING = 1.0;

    public enum Reason {
        ACTIONABLE,
        DIFFERENT_DIMENSION,
        CHUNK_UNLOADED,
        WRONG_BLOCK,
        NOT_INTERACTABLE,
        OUT_OF_RANGE,
        OBSTRUCTED,
        HANDLER_MISMATCH
    }

    public enum HandlerExpectation {
        CONTAINER,
        CRAFTING_TABLE
    }

    public record Result(boolean actionable, Reason reason, BlockPos target, String dimension,
                         HandlerExpectation handlerExpectation) {
        public Result {
            Objects.requireNonNull(reason, "reason");
            Objects.requireNonNull(target, "target");
            Objects.requireNonNull(dimension, "dimension");
            Objects.requireNonNull(handlerExpectation, "handlerExpectation");
        }
    }

    private Reachability() {}

    public static Result check(Level level, Player player, BlockPos pos, String dimension,
                               TargetKind kind, int configuredUpperBound) {
        return check(level, player, pos, dimension, kind, null, configuredUpperBound);
    }

    public static Result check(Level level, Player player, BlockPos pos, String dimension,
                               TargetKind kind, ContainerKind expectedContainer, int configuredUpperBound) {
        var expectation = expectation(kind);
        if (!level.dimension().identifier().toString().equals(dimension)) {
            return result(false, Reason.DIFFERENT_DIMENSION, pos, dimension, expectation);
        }
        if (!level.isLoaded(pos)) {
            return result(false, Reason.CHUNK_UNLOADED, pos, dimension, expectation);
        }
        if (!expectedBlock(level, pos, kind, expectedContainer)) {
            return result(false, Reason.WRONG_BLOCK, pos, dimension, expectation);
        }
        if (!interactable(level, pos, kind)) {
            return result(false, Reason.NOT_INTERACTABLE, pos, dimension, expectation);
        }
        if (!inRange(player, pos, configuredUpperBound)) {
            return result(false, Reason.OUT_OF_RANGE, pos, dimension, expectation);
        }
        if (!hasVisibleInteractionPoint(level, player, pos)) {
            return result(false, Reason.OBSTRUCTED, pos, dimension, expectation);
        }
        return result(true, Reason.ACTIONABLE, pos, dimension, expectation);
    }

    public static HandlerExpectation expectation(TargetKind kind) {
        return kind == TargetKind.CRAFTING_TABLE
                ? HandlerExpectation.CRAFTING_TABLE : HandlerExpectation.CONTAINER;
    }

    public static boolean expectedBlock(Level level, BlockPos pos, TargetKind kind) {
        return expectedBlock(level, pos, kind, null);
    }

    public static boolean expectedBlock(Level level, BlockPos pos, TargetKind kind,
                                        ContainerKind expectedContainer) {
        if (kind == TargetKind.CONTAINER && expectedContainer != null) {
            var block = level.getBlockState(pos).getBlock();
            return switch (expectedContainer) {
                case CHEST -> block == Blocks.CHEST;
                case TRAPPED_CHEST -> block == Blocks.TRAPPED_CHEST;
                case BARREL -> block == Blocks.BARREL;
                case SHULKER_BOX -> block instanceof ShulkerBoxBlock;
                case ENDER_CHEST -> block == Blocks.ENDER_CHEST;
            };
        }
        return kind == TargetKind.CRAFTING_TABLE
                ? level.getBlockState(pos).is(Blocks.CRAFTING_TABLE)
                : isContainerBlock(level, pos);
    }

    public static boolean interactable(Level level, BlockPos pos, TargetKind kind) {
        if (kind == TargetKind.CRAFTING_TABLE) return true;
        var state = level.getBlockState(pos);
        if (state.getBlock() instanceof EnderChestBlock) return true;
        BlockEntity entity = level.getBlockEntity(pos);
        return entity instanceof Container && !entity.isRemoved();
    }

    public static boolean isContainerBlock(Level level, BlockPos pos) {
        var block = level.getBlockState(pos).getBlock();
        return block instanceof ChestBlock || block instanceof EnderChestBlock
                || block == Blocks.BARREL || block instanceof ShulkerBoxBlock;
    }

    public static boolean inRange(Player player, BlockPos pos, int configuredUpperBound) {
        if (player.isWithinBlockInteractionRange(pos, VANILLA_PADDING)) return true;
        return configuredUpperBound > 0
                && player.getEyePosition().distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(pos))
                <= (double) configuredUpperBound * configuredUpperBound;
    }

    public static boolean hasVisibleInteractionPoint(Level level, Player player, BlockPos pos) {
        var shape = level.getBlockState(pos).getShape(level, pos);
        if (shape.isEmpty()) return false;
        var box = shape.bounds();
        var x = pos.getX();
        var y = pos.getY();
        var z = pos.getZ();
        var eye = player.getEyePosition();
        var samples = 5;
        var epsilon = 0.01;
        for (int u = 1; u < samples; u++) {
            for (int v = 1; v < samples; v++) {
                var fu = (double) u / samples;
                var fv = (double) v / samples;
                var points = new net.minecraft.world.phys.Vec3[] {
                        new net.minecraft.world.phys.Vec3(x + box.maxX + epsilon,
                                y + box.minY + (box.maxY - box.minY) * fu,
                                z + box.minZ + (box.maxZ - box.minZ) * fv),
                        new net.minecraft.world.phys.Vec3(x + box.minX - epsilon,
                                y + box.minY + (box.maxY - box.minY) * fu,
                                z + box.minZ + (box.maxZ - box.minZ) * fv),
                        new net.minecraft.world.phys.Vec3(x + box.minX + (box.maxX - box.minX) * fu,
                                y + box.maxY + epsilon,
                                z + box.minZ + (box.maxZ - box.minZ) * fv),
                        new net.minecraft.world.phys.Vec3(x + box.minX + (box.maxX - box.minX) * fu,
                                y + box.minY - epsilon,
                                z + box.minZ + (box.maxZ - box.minZ) * fv),
                        new net.minecraft.world.phys.Vec3(x + box.minX + (box.maxX - box.minX) * fu,
                                y + box.minY + (box.maxY - box.minY) * fv,
                                z + box.maxZ + epsilon),
                        new net.minecraft.world.phys.Vec3(x + box.minX + (box.maxX - box.minX) * fu,
                                y + box.minY + (box.maxY - box.minY) * fv,
                                z + box.minZ - epsilon),
                };
                for (var point : points) {
                    var hit = level.clip(new net.minecraft.world.level.ClipContext(eye, point,
                            net.minecraft.world.level.ClipContext.Block.OUTLINE,
                            net.minecraft.world.level.ClipContext.Fluid.NONE, player));
                    if (hit.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK
                            && hit.getBlockPos().equals(pos)) return true;
                }
            }
        }
        return false;
    }

    private static Result result(boolean actionable, Reason reason, BlockPos pos, String dimension,
                                 HandlerExpectation expectation) {
        return new Result(actionable, reason, pos, dimension, expectation);
    }
}
