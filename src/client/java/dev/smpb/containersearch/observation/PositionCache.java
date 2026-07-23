package dev.smpb.containersearch.observation;

import net.minecraft.core.BlockPos;

import java.util.Optional;

public final class PositionCache {
    private static BlockPos pos;
    private static String dimension;

    private PositionCache() {}

    public static void record(BlockPos blockPos, String dim) {
        pos = blockPos;
        dimension = dim;
    }

    public static Optional<BlockPos> pos() {
        return Optional.ofNullable(pos);
    }

    public static Optional<String> dimension() {
        return Optional.ofNullable(dimension);
    }

    public static void clear() {
        pos = null;
        dimension = null;
    }
}
