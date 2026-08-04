package dev.smpb.findmyitems.observation;

import static org.junit.jupiter.api.Assertions.*;

import dev.smpb.findmyitems.model.BlockPosition;
import dev.smpb.findmyitems.model.ContainerKind;
import dev.smpb.findmyitems.model.SourceKey;
import java.util.List;
import net.minecraft.core.component.DataComponents;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemContainerContents;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;

final class ObservationBuilderTest {
    private static final String DIM = "minecraft:overworld";
    private static final BlockPosition POS = new BlockPosition(10, 64, 200);
    private static final List<BlockPosition> SINGLE = List.of(POS);
    private static final List<BlockPosition> DOUBLE = List.of(
            new BlockPosition(10, 64, 200), new BlockPosition(11, 64, 200));

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

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

    @Test
    void nestedContentsRetainOuterHolderProvenance() {
        var provenance = new dev.smpb.findmyitems.model.StackSnapshot.Provenance(
                List.of(0, 0), 0);
        assertEquals(List.of(0, 0), provenance.slots());
        assertEquals(0, provenance.holderSlot());
    }

    @Test
    void deeplyNestedContentsKeepTheFirstHolder() {
        var provenance = new dev.smpb.findmyitems.model.StackSnapshot.Provenance(
                List.of(0, 0, 0), 0);
        assertEquals(0, provenance.holderSlot());
        assertEquals(3, provenance.slots().size());
    }

}
