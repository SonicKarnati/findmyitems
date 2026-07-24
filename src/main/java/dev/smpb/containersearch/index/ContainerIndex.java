package dev.smpb.containersearch.index;

import dev.smpb.containersearch.model.ContainerObservation;
import dev.smpb.containersearch.model.SourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ShulkerBoxBlock;

import java.util.List;

public interface ContainerIndex {
    long revision();

    void observe(ContainerObservation observation);

    void markMissing(SourceKey source);

    List<ItemResult> search(String query);

    IndexSnapshot snapshot();

    void replace(IndexSnapshot snapshot);

    default void pruneMissing(Level level, List<SourceKey> known) {
        for (var source : known) {
            if (source.positions().isEmpty()) continue;
            var pos = source.positions().getFirst();
            var mcPos = new net.minecraft.core.BlockPos(pos.x(), pos.y(), pos.z());
            if (!level.isLoaded(mcPos)) continue;
            var block = level.getBlockState(mcPos).getBlock();
            var stillContainer = block == Blocks.CHEST
                    || block == Blocks.TRAPPED_CHEST
                    || block == Blocks.BARREL
                    || block == Blocks.ENDER_CHEST
                    || block instanceof ShulkerBoxBlock;
            if (!stillContainer) {
                markMissing(source);
            }
        }
    }
}

