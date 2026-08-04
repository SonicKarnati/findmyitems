package dev.smpb.findmyitems.retrieval;

import dev.smpb.findmyitems.FindMyItemsClient;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;

import java.util.function.IntSupplier;

/** Client prediction of the same target facts the server validates before an action. */
public final class ReachabilityService {
    private static ReachabilityService shared;
    private final IntSupplier configuredUpperBound;

    public ReachabilityService(IntSupplier configuredUpperBound) {
        this.configuredUpperBound = configuredUpperBound;
    }

    public static ReachabilityService shared() {
        if (shared == null) {
            shared = new ReachabilityService(() -> FindMyItemsClient.config().retrieveDistanceBlocks);
        }
        return shared;
    }

    public Reachability.Result check(BlockPos pos, TargetKind kind) {
        var mc = Minecraft.getInstance();
        var player = mc.player;
        var level = mc.level;
        var dimension = level == null ? "" : level.dimension().identifier().toString();
        if (player == null || level == null) {
            return new Reachability.Result(false, Reachability.Reason.DIFFERENT_DIMENSION,
                    pos, dimension, Reachability.expectation(kind));
        }

        var result = Reachability.check(level, player, pos, dimension, kind, configuredUpperBound.getAsInt());
        if (!result.actionable()) return result;
        if (!Reachability.hasVisibleInteractionPoint(level, player, pos)) {
            return new Reachability.Result(false, Reachability.Reason.OBSTRUCTED,
                    pos, dimension, result.handlerExpectation());
        }
        var provider = level.getBlockState(pos).getMenuProvider(level, pos);
        var enderChest = kind == TargetKind.CONTAINER && level.getBlockState(pos).is(Blocks.ENDER_CHEST);
        if (provider == null && !enderChest) {
            return new Reachability.Result(false, Reachability.Reason.HANDLER_MISMATCH,
                    pos, dimension, result.handlerExpectation());
        }
        return result;
    }
}
