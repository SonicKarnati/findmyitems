package dev.smpb.containersearch.index;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.smpb.containersearch.model.BlockPosition;
import dev.smpb.containersearch.model.ContainerKind;
import dev.smpb.containersearch.model.ContainerObservation;
import dev.smpb.containersearch.model.SlotSnapshot;
import dev.smpb.containersearch.model.SourceKey;
import dev.smpb.containersearch.model.StackKey;
import dev.smpb.containersearch.model.StackSnapshot;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

final class InMemoryContainerIndexTest {
    private InMemoryContainerIndex index;
    private SourceKey firstChest;

    @BeforeEach
    void setUp() {
        index = new InMemoryContainerIndex();
        firstChest = source(1);
    }

    @Test
    void newerObservationReplacesRatherThanMerges() {
        index.observe(observation(firstChest, stack(0, "minecraft:stone", "{}", 64, "Stone")));
        long previousRevision = index.revision();

        index.observe(observation(firstChest, stack(0, "minecraft:dirt", "{}", 3, "Dirt")));

        assertTrue(index.search("stone").isEmpty());
        assertEquals(3, index.search("dirt").getFirst().totalCount());
        assertTrue(index.revision() > previousRevision);
    }

    @Test
    void componentDistinctStacksRemainSeparate() {
        index.observe(observation(
                firstChest,
                stack(0, "minecraft:diamond_pickaxe", "{damage:1}", 1, "Diamond Pickaxe"),
                stack(1, "minecraft:diamond_pickaxe", "{damage:2}", 1, "Diamond Pickaxe")));

        var results = index.search("diamond pickaxe");

        assertEquals(2, results.size());
        assertNotEquals(results.get(0).key(), results.get(1).key());
    }

    @Test
    void everyQueryTermMustMatchNameIdentifierOrTooltip() {
        index.observe(observation(
                firstChest,
                new SlotSnapshot(0, new StackSnapshot(
                        new StackKey("minecraft:diamond_pickaxe", "{enchantments:efficiency_5}"),
                        1,
                        "Diamond Pickaxe",
                        List.of("Efficiency V")))));

        assertEquals(1, index.search("DIAMOND efficiency").size());
        assertTrue(index.search("diamond silk").isEmpty());
    }

    @Test
    void equalStacksAggregateAcrossIndependentInventories() {
        index.observe(observation(firstChest, stack(0, "minecraft:cobblestone", "{}", 64, "Cobblestone")));
        index.observe(observation(source(2), stack(0, "minecraft:cobblestone", "{}", 38, "Cobblestone")));

        var result = index.search("cobble").getFirst();

        assertEquals(102, result.totalCount());
        assertEquals(2, result.sources().size());
    }

    @Test
    void enderAccessPointsMergeWithoutMultiplyingSharedContents() {
        var overworldAccess = SourceKey.storage(
                "minecraft:overworld",
                ContainerKind.ENDER_CHEST,
                List.of(new BlockPosition(3, 64, 3)));
        var netherAccess = SourceKey.storage(
                "minecraft:the_nether",
                ContainerKind.ENDER_CHEST,
                List.of(new BlockPosition(7, 70, 7)));
        var stack = stack(0, "minecraft:ender_pearl", "{}", 12, "Ender Pearl");
        index.observe(new ContainerObservation(
                SourceKey.enderInventory(), List.of(overworldAccess), List.of(stack), Instant.EPOCH));
        index.observe(new ContainerObservation(
                SourceKey.enderInventory(), List.of(netherAccess), List.of(stack), Instant.EPOCH.plusSeconds(1)));

        var result = index.search("ender pearl").getFirst();

        assertEquals(12, result.totalCount());
        assertEquals(List.of(overworldAccess, netherAccess),
                result.sources().stream().map(SourceResult::source).toList());
    }

    @Test
    void confirmedMissingOrdinarySourceRemovesItsContents() {
        index.observe(observation(firstChest, stack(0, "minecraft:stone", "{}", 4, "Stone")));

        index.markMissing(firstChest);

        assertTrue(index.search("stone").isEmpty());
    }

    @Test
    void snapshotCanReplaceAnotherIndexWithoutSharingMutation() {
        index.observe(observation(firstChest, stack(0, "minecraft:stone", "{}", 4, "Stone")));
        var restored = new InMemoryContainerIndex();

        restored.replace(index.snapshot());
        index.markMissing(firstChest);

        assertEquals(4, restored.search("stone").getFirst().totalCount());
    }

    private static SourceKey source(int x) {
        return SourceKey.storage(
                "minecraft:overworld",
                ContainerKind.CHEST,
                List.of(new BlockPosition(x, 64, 0)));
    }

    private static ContainerObservation observation(SourceKey source, SlotSnapshot... slots) {
        return new ContainerObservation(source, List.of(source), List.of(slots), Instant.EPOCH);
    }

    private static SlotSnapshot stack(
            int slot,
            String itemId,
            String components,
            int count,
            String displayName) {
        return new SlotSnapshot(
                slot,
                new StackSnapshot(new StackKey(itemId, components), count, displayName, List.of()));
    }
}
