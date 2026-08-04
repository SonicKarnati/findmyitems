package dev.smpb.findmyitems.retrieval;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/** Client-game-test bridge for the executor's conservation-safe insertion primitive. */
public final class CraftingExecutorTestAccess {
    private CraftingExecutorTestAccess() {
    }

    public static ItemStack insertCraftedOutput(ServerPlayer player, ItemStack output) {
        return CraftingExecutor.insertCraftedOutput(player, output);
    }
}
