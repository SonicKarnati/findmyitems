package dev.smpb.containersearch.observation;

import static org.junit.jupiter.api.Assertions.*;

import dev.smpb.containersearch.model.BlockPosition;
import dev.smpb.containersearch.model.ContainerKind;
import dev.smpb.containersearch.model.SourceKey;
import java.util.List;
import org.junit.jupiter.api.Test;

final class ObservationBuilderTest {
    private static final String DIM = "minecraft:overworld";
    private static final BlockPosition POS = new BlockPosition(10, 64, 200);
    private static final List<BlockPosition> SINGLE = List.of(POS);
    private static final List<BlockPosition> DOUBLE = List.of(
            new BlockPosition(10, 64, 200), new BlockPosition(11, 64, 200));

    @Test
    void contentsKeyForStorageUsesExactPositions() {
        var key = ObservationBuilder.contentsKey(DIM, ContainerKind.CHEST, SINGLE);
        assertEquals(DIM, key.dimension());
        assertEquals(ContainerKind.CHEST, key.kind());
        assertEquals(SINGLE, key.positions());
    }

    @Test
    void contentsKeyForDoubleChestIncludesBothPositions() {
        var key = ObservationBuilder.contentsKey(DIM, ContainerKind.CHEST, DOUBLE);
        assertEquals(2, key.positions().size());
    }

    @Test
    void contentsKeyForEnderChestReturnsSharedInventory() {
        var key = ObservationBuilder.contentsKey(DIM, ContainerKind.ENDER_CHEST, SINGLE);
        assertEquals(SourceKey.enderInventory(), key);
    }

    @Test
    void accessSourcesSplitEachPositionIntoOwnSource() {
        var sources = ObservationBuilder.accessSources(DIM, ContainerKind.CHEST, DOUBLE);
        assertEquals(2, sources.size());
        assertNotEquals(sources.get(0), sources.get(1));
        assertEquals(1, sources.get(0).positions().size());
        assertEquals(1, sources.get(1).positions().size());
    }

    @Test
    void accessSourcesForSinglePositionHasOneEntry() {
        var sources = ObservationBuilder.accessSources(DIM, ContainerKind.BARREL, SINGLE);
        assertEquals(1, sources.size());
        assertEquals(SINGLE, sources.getFirst().positions());
    }
}
