package dev.smpb.findmyitems.craft;

import dev.smpb.findmyitems.model.StackKey;
import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

final class InventorySimulationTest {
    private static StackKey key(String id) {
        return new StackKey(id, "{}");
    }


    @Test
    void snapshotCopiesTheThirtySixStorageSlots() {
        var input = new ArrayList<>(java.util.Collections.nCopies(36, ItemStack.EMPTY));
        input.clear();

        assertThrows(IllegalArgumentException.class, () -> InventorySimulation.PlayerInventorySnapshot.of(input,
                new ArrayList<>(java.util.Collections.nCopies(36, null))));
    }

    @Test
    void nonProviderSnapshotFactoryCannotPretendToKnowRegistryIdentity() {
        var input = new ArrayList<>(java.util.Collections.nCopies(36, ItemStack.EMPTY));

        assertThrows(IllegalArgumentException.class, () -> InventorySimulation.PlayerInventorySnapshot.of(input));
    }

    @Test
    void plannedSourceAbsentFromSnapshotIsUnsafe() {
        var input = new ArrayList<>(java.util.Collections.nCopies(36, ItemStack.EMPTY));

        assertThrows(IllegalArgumentException.class, () -> InventorySimulation.PlayerInventorySnapshot.of(input,
                new ArrayList<>(java.util.Collections.nCopies(36, null))));
    }

}
