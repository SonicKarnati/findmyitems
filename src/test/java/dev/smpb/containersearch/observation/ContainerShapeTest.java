package dev.smpb.containersearch.observation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.smpb.containersearch.model.BlockPosition;
import dev.smpb.containersearch.model.ContainerKind;
import java.util.List;
import org.junit.jupiter.api.Test;

final class ContainerShapeTest {
    private static final List<BlockPosition> SINGLE = List.of(new BlockPosition(1, 64, 1));
    private static final List<BlockPosition> DOUBLE = List.of(
            new BlockPosition(1, 64, 1), new BlockPosition(2, 64, 1));

    @Test
    void genericStorageAcceptsSupportedVanillaSlotCounts() {
        assertEquals(27, shape(ContainerKind.CHEST, MenuKind.GENERIC_STORAGE, 27, SINGLE).storageSlots());
        assertEquals(54, shape(ContainerKind.CHEST, MenuKind.GENERIC_STORAGE, 54, DOUBLE).storageSlots());
        assertEquals(27, shape(ContainerKind.TRAPPED_CHEST, MenuKind.GENERIC_STORAGE, 27, SINGLE).storageSlots());
        assertEquals(27, shape(ContainerKind.BARREL, MenuKind.GENERIC_STORAGE, 27, SINGLE).storageSlots());
        assertEquals(27, shape(ContainerKind.ENDER_CHEST, MenuKind.GENERIC_STORAGE, 27, SINGLE).storageSlots());
    }

    @Test
    void shulkerRequiresItsDedicatedMenu() {
        assertEquals(27, shape(ContainerKind.SHULKER_BOX, MenuKind.SHULKER, 27, SINGLE).storageSlots());
        assertTrue(ContainerShape.resolve(
                        ContainerKind.SHULKER_BOX, MenuKind.GENERIC_STORAGE, 27, SINGLE)
                .isEmpty());
    }

    @Test
    void utilityAndSmallMenusAreRejected() {
        assertTrue(ContainerShape.resolve(ContainerKind.CHEST, MenuKind.UTILITY, 3, SINGLE).isEmpty());
        assertTrue(ContainerShape.resolve(ContainerKind.CHEST, MenuKind.GENERIC_STORAGE, 18, SINGLE).isEmpty());
        assertTrue(ContainerShape.resolve(ContainerKind.BARREL, MenuKind.GENERIC_STORAGE, 54, SINGLE).isEmpty());
    }

    private static ContainerShape shape(
            ContainerKind kind,
            MenuKind menuKind,
            int storageSlots,
            List<BlockPosition> positions) {
        return ContainerShape.resolve(kind, menuKind, storageSlots, positions).orElseThrow();
    }
}
