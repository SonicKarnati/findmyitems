package dev.smpb.containersearch.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

final class ModelTest {
    @Test
    void doubleChestPositionsAreCanonical() {
        var first = new BlockPosition(10, 64, 3);
        var second = new BlockPosition(9, 64, 3);

        var source = SourceKey.storage(
                "minecraft:overworld",
                ContainerKind.CHEST,
                List.of(first, second, first));

        assertEquals(List.of(second, first), source.positions());
    }

    @Test
    void stackCountMustBePositive() {
        var key = new StackKey("minecraft:stone", "{}");

        assertThrows(
                IllegalArgumentException.class,
                () -> new StackSnapshot(key, 0, "Stone", List.of()));
    }

    @Test
    void observationsDefensivelyCopyTheirLists() {
        var source = SourceKey.storage(
                "minecraft:overworld",
                ContainerKind.BARREL,
                List.of(new BlockPosition(1, 2, 3)));
        var accessSources = new ArrayList<>(List.of(source));
        var slots = new ArrayList<>(List.of(new SlotSnapshot(
                0,
                new StackSnapshot(new StackKey("minecraft:stone", "{}"), 4, "Stone", List.of()))));

        var observation = new ContainerObservation(source, accessSources, slots, Instant.EPOCH);
        accessSources.clear();
        slots.clear();

        assertEquals(1, observation.accessSources().size());
        assertEquals(1, observation.slots().size());
        assertThrows(UnsupportedOperationException.class, () -> observation.slots().clear());
    }

    @Test
    void enderContentsUseOneLogicalKeyWithPhysicalAccessSources() {
        var access = SourceKey.storage(
                "minecraft:the_nether",
                ContainerKind.ENDER_CHEST,
                List.of(new BlockPosition(4, 70, -2)));

        var observation = new ContainerObservation(
                SourceKey.enderInventory(),
                List.of(access),
                List.of(),
                Instant.EPOCH);

        assertEquals(List.of(), observation.contentsKey().positions());
        assertEquals(List.of(access), observation.accessSources());
    }
}
