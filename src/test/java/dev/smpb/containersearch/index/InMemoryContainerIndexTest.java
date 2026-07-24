package dev.smpb.containersearch.index;

import static org.junit.jupiter.api.Assertions.*;

import dev.smpb.containersearch.model.*;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

final class InMemoryContainerIndexTest {
    private static final String DIM = "minecraft:overworld";
    private static final BlockPosition POS = new BlockPosition(10, 64, 200);
    private static final SourceKey SOURCE = SourceKey.storage(DIM, ContainerKind.CHEST, List.of(POS));

    private static StackSnapshot stack(String itemId, int count) {
        return new StackSnapshot(new StackKey(itemId, "{}"), count, itemId, List.of());
    }

    private static SlotSnapshot slot(int index, String itemId, int count) {
        return new SlotSnapshot(index, stack(itemId, count));
    }

    private static ContainerObservation observation(SourceKey contentsKey, SourceKey accessSource, SlotSnapshot... slots) {
        return new ContainerObservation(contentsKey, List.of(accessSource), List.of(slots), Instant.now());
    }

    @Test
    void observeAndSearchReturnsItem() {
        var index = new InMemoryContainerIndex();
        index.observe(observation(SOURCE, SOURCE, slot(0, "minecraft:oak_log", 64)));

        var results = index.search("oak");
        assertEquals(1, results.size());
        assertEquals("minecraft:oak_log", results.getFirst().key().itemId());
        assertEquals(64, results.getFirst().totalCount());
    }

    @Test
    void searchWithEmptyQueryReturnsAll() {
        var index = new InMemoryContainerIndex();
        index.observe(observation(SOURCE, SOURCE, slot(0, "minecraft:oak_log", 64)));
        index.observe(observation(
                SourceKey.storage(DIM, ContainerKind.CHEST, List.of(new BlockPosition(20, 64, 200))),
                SourceKey.storage(DIM, ContainerKind.CHEST, List.of(new BlockPosition(20, 64, 200))),
                slot(0, "minecraft:diamond", 10)));

        var results = index.search("");
        assertEquals(2, results.size());
    }

    @Test
    void markMissingRemovesSource() {
        var index = new InMemoryContainerIndex();
        var source2 = SourceKey.storage(DIM, ContainerKind.CHEST, List.of(new BlockPosition(20, 64, 200)));
        index.observe(observation(SOURCE, SOURCE, slot(0, "minecraft:oak_log", 64)));
        index.observe(observation(source2, source2, slot(0, "minecraft:oak_log", 32)));

        var before = index.search("");
        assertEquals(96, before.getFirst().totalCount());

        index.markMissing(SOURCE);

        var after = index.search("");
        assertEquals(32, after.getFirst().totalCount());
    }

    @Test
    void reObservingUpdatesCounts() {
        var index = new InMemoryContainerIndex();
        index.observe(observation(SOURCE, SOURCE, slot(0, "minecraft:oak_log", 64)));

        index.observe(observation(SOURCE, SOURCE, slot(0, "minecraft:oak_log", 32)));

        var results = index.search("");
        assertEquals(32, results.getFirst().totalCount());
    }

    @Test
    void breakingDoubleChestHalfEvictsStaleContents() {
        var index = new InMemoryContainerIndex();
        var posA = POS;
        var posB = new BlockPosition(11, 64, 200);
        var doubleKey = SourceKey.storage(DIM, ContainerKind.CHEST, List.of(posA, posB));
        var single = SourceKey.storage(DIM, ContainerKind.CHEST, List.of(posA));

        // Double chest full of 64 stone.
        index.observe(observation(doubleKey, single, slot(0, "minecraft:stone", 64)));
        assertEquals(64, index.search("").getFirst().totalCount());

        // Break the B half; surviving single chest re-observed with its 32.
        index.observe(observation(single, single, slot(0, "minecraft:stone", 32)));

        var after = index.search("");
        assertEquals(1, after.size());
        assertEquals(32, after.getFirst().totalCount());
    }

    @Test
    void aggregationAcrossMultipleContainers() {
        var index = new InMemoryContainerIndex();
        var source2 = SourceKey.storage(DIM, ContainerKind.CHEST, List.of(new BlockPosition(20, 64, 200)));

        index.observe(observation(SOURCE, SOURCE, slot(0, "minecraft:oak_log", 64)));
        index.observe(observation(source2, source2, slot(0, "minecraft:oak_log", 32)));

        var results = index.search("");
        assertEquals(1, results.size());
        assertEquals(96, results.getFirst().totalCount());
        assertEquals(2, results.getFirst().sources().size());
    }
}
