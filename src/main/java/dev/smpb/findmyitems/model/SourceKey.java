package dev.smpb.findmyitems.model;

import java.util.List;
import java.util.Objects;

public record SourceKey(
        String dimension,
        ContainerKind kind,
        List<BlockPosition> positions) {
    private static final String ALL_DIMENSIONS = "findmyitems:all_dimensions";

    public SourceKey {
        Objects.requireNonNull(dimension, "dimension");
        Objects.requireNonNull(kind, "kind");
        positions = positions.stream().distinct().sorted().toList();
        if (dimension.isBlank()) {
            throw new IllegalArgumentException("dimension must not be blank");
        }
        if (positions.isEmpty()
                && (kind != ContainerKind.ENDER_CHEST || !ALL_DIMENSIONS.equals(dimension))) {
            throw new IllegalArgumentException("only the logical ender inventory has no position");
        }
        if (positions.size() > 2) {
            throw new IllegalArgumentException("a storage source has at most two positions");
        }
        if (positions.size() == 2
                && kind != ContainerKind.CHEST
                && kind != ContainerKind.TRAPPED_CHEST) {
            throw new IllegalArgumentException("only chests can have two positions");
        }
    }

    public static SourceKey storage(
            String dimension,
            ContainerKind kind,
            List<BlockPosition> positions) {
        if (positions.isEmpty()) {
            throw new IllegalArgumentException("storage positions must not be empty");
        }
        return new SourceKey(dimension, kind, positions);
    }

    public static SourceKey enderInventory() {
        return new SourceKey(ALL_DIMENSIONS, ContainerKind.ENDER_CHEST, List.of());
    }
}

