package dev.smpb.findmyitems.observation;

import dev.smpb.findmyitems.model.BlockPosition;
import dev.smpb.findmyitems.model.ContainerKind;
import dev.smpb.findmyitems.model.SourceKey;

import java.util.List;

public final class ObservationBuilder {
    private ObservationBuilder() {}

    public static SourceKey contentsKey(String dimension, ContainerKind kind, List<BlockPosition> positions) {
        if (kind == ContainerKind.ENDER_CHEST) {
            return SourceKey.enderInventory();
        }
        return SourceKey.storage(dimension, kind, positions);
    }

    public static List<SourceKey> accessSources(String dimension, ContainerKind kind, List<BlockPosition> positions) {
        return positions.stream()
            .map(pos -> SourceKey.storage(dimension, kind, List.of(pos)))
            .toList();
    }
}
