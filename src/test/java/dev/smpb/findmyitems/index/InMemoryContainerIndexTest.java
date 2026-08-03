package dev.smpb.findmyitems.index;

import static org.junit.jupiter.api.Assertions.*;

import dev.smpb.findmyitems.model.*;
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

    private static SlotSnapshot slot(int index, String itemId, int count, String displayName) {
        return new SlotSnapshot(index, new StackSnapshot(new StackKey(itemId, "{}"), count,
                displayName, List.of()));
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

    /**
     * The ender inventory survives markMissing with no way to reach it. Its stock must still be
     * accounted for by the row, or the total, the container tally and the Take clamp disagree.
     */
    @Test
    void containerWithNoAccessSourceIsStillListedAsASource() {
        var index = new InMemoryContainerIndex();
        var enderPos = SourceKey.storage(DIM, ContainerKind.ENDER_CHEST, List.of(new BlockPosition(20, 64, 200)));

        index.observe(observation(SOURCE, SOURCE, slot(0, "minecraft:emerald", 5)));
        index.observe(new ContainerObservation(
                SourceKey.enderInventory(), List.of(enderPos), List.of(slot(10, "minecraft:emerald", 10)),
                Instant.now()));

        // The block is gone; the remembered ender contents deliberately outlive it.
        index.markMissing(enderPos);

        var result = index.search("emerald").getFirst();
        assertEquals(15, result.totalCount());
        assertEquals(15, result.sources().stream().mapToInt(SourceResult::count).sum());
        assertEquals(2, result.sources().size());
        assertEquals(10, result.sources().stream()
                .filter(source -> source.source().positions().isEmpty())
                .mapToInt(SourceResult::count)
                .sum());
    }

    /**
     * The ender inventory can be read from player data with no chest placed, so it is the one
     * container that may be observed with no access source at all.
     */
    @Test
    void enderInventoryCanBeObservedWithNoAccessSource() {
        var index = new InMemoryContainerIndex();
        var enderPos = SourceKey.storage(DIM, ContainerKind.ENDER_CHEST, List.of(new BlockPosition(20, 64, 200)));

        index.observe(new ContainerObservation(SourceKey.enderInventory(), List.of(enderPos),
                List.of(slot(0, "minecraft:emerald", 10)), Instant.now()));
        index.observe(new ContainerObservation(SourceKey.enderInventory(), List.of(),
                List.of(slot(0, "minecraft:emerald", 4)), Instant.now()));

        var result = index.search("emerald").getFirst();
        assertEquals(4, result.totalCount());
        // Reading it from player data must not cost the way in that a placed chest gave us.
        assertEquals(List.of(enderPos), result.sources().stream().map(SourceResult::source).toList());

        assertThrows(IllegalArgumentException.class, () -> new ContainerObservation(
                SOURCE, List.of(), List.of(slot(0, "minecraft:emerald", 1)), Instant.now()));
    }

    /** A rescan that found nothing new must not file the same ender contents under a second key. */
    @Test
    void rescanningAnEnderChestDoesNotDuplicateIt() {
        var index = new InMemoryContainerIndex();
        var enderPos = SourceKey.storage(DIM, ContainerKind.ENDER_CHEST, List.of(new BlockPosition(20, 64, 200)));
        var opened = new ContainerObservation(
                SourceKey.enderInventory(), List.of(enderPos), List.of(slot(10, "minecraft:emerald", 10)),
                Instant.now());

        index.observe(opened);
        index.observe(opened);

        assertEquals(1, index.snapshot().containers().size());
        var result = index.search("emerald").getFirst();
        assertEquals(10, result.totalCount());
        assertEquals(1, result.sources().size());
    }

    /**
     * A double chest is two ways in to one container. Both must be offered so the nearer half can
     * be picked, and both must name the same container so nothing counts it twice.
     */
    @Test
    void bothHalvesOfADoubleChestPointAtOneContainer() {
        var index = new InMemoryContainerIndex();
        var posA = POS;
        var posB = new BlockPosition(11, 64, 200);
        var doubleKey = SourceKey.storage(DIM, ContainerKind.CHEST, List.of(posA, posB));

        index.observe(new ContainerObservation(doubleKey,
                List.of(SourceKey.storage(DIM, ContainerKind.CHEST, List.of(posA)),
                        SourceKey.storage(DIM, ContainerKind.CHEST, List.of(posB))),
                List.of(slot(0, "minecraft:stone", 64)), Instant.now()));

        var result = index.search("").getFirst();
        assertEquals(64, result.totalCount());
        assertEquals(2, result.sources().size());
        assertEquals(1, result.sources().stream().map(SourceResult::contentsKey).distinct().count());
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

    @Test
    void ranksCompleteNameMatchesBeforeSubstringMatches() {
        var index = new InMemoryContainerIndex();
        var white = SourceKey.storage(DIM, ContainerKind.CHEST,
                List.of(new BlockPosition(20, 64, 200)));
        var orange = SourceKey.storage(DIM, ContainerKind.CHEST,
                List.of(new BlockPosition(21, 64, 200)));
        var bedrock = SourceKey.storage(DIM, ContainerKind.CHEST,
                List.of(new BlockPosition(22, 64, 200)));
        index.observe(observation(white, white, slot(0, "minecraft:white_bed", 1, "White Bed")));
        index.observe(observation(orange, orange, slot(0, "minecraft:orange_bed", 1, "Orange Bed")));
        index.observe(observation(bedrock, bedrock, slot(0, "minecraft:bedrock", 1, "Bedrock")));

        var ids = index.search("bed").stream().map(result -> result.key().itemId()).toList();
        assertEquals(3, ids.size());
        assertTrue(ids.subList(0, 2).containsAll(List.of("minecraft:orange_bed", "minecraft:white_bed")));
        assertEquals("minecraft:bedrock", ids.get(2));
    }

    @Test
    void searchOnlyConsidersIndexedRootStacks() {
        var index = new InMemoryContainerIndex();
        index.observe(observation(SOURCE, SOURCE, slot(0, "minecraft:oak_log", 1, "Oak Log")));

        assertTrue(index.search("plank").isEmpty());
    }
}
