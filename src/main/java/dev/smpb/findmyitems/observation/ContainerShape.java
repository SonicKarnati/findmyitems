package dev.smpb.findmyitems.observation;

import dev.smpb.findmyitems.model.BlockPosition;
import dev.smpb.findmyitems.model.ContainerKind;
import java.util.List;
import java.util.Optional;

public record ContainerShape(
        ContainerKind kind,
        int storageSlots,
        List<BlockPosition> positions) {
    public ContainerShape {
        positions = positions.stream().distinct().sorted().toList();
    }

    public static Optional<ContainerShape> resolve(
            ContainerKind kind,
            MenuKind menuKind,
            int storageSlots,
            List<BlockPosition> positions) {
        if (menuKind == MenuKind.UTILITY || positions.isEmpty()) {
            return Optional.empty();
        }
        boolean supported = switch (kind) {
            case CHEST, TRAPPED_CHEST ->
                menuKind == MenuKind.GENERIC_STORAGE && (storageSlots == 27 || storageSlots == 54);
            case BARREL, ENDER_CHEST ->
                menuKind == MenuKind.GENERIC_STORAGE && storageSlots == 27;
            case SHULKER_BOX -> menuKind == MenuKind.SHULKER && storageSlots == 27;
        };
        if (!supported) {
            return Optional.empty();
        }
        if (storageSlots == 54 && positions.size() != 2) {
            return Optional.empty();
        }
        if (storageSlots == 27 && positions.size() != 1) {
            return Optional.empty();
        }
        return Optional.of(new ContainerShape(kind, storageSlots, positions));
    }
}
