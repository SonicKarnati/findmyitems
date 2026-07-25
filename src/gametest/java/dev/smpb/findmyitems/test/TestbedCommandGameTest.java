package dev.smpb.findmyitems.test;

import dev.smpb.findmyitems.FindMyItemsClient;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.minecraft.world.Container;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;

/**
 * Checks that {@code /fmitest build} really produces a usable testbed and that {@code /fmitest clear}
 * puts the world back. The command exists so the mod can be tried by hand, so the thing worth
 * asserting is that a player who types it gets containers with items in them.
 */
public final class TestbedCommandGameTest implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext context) {
        try (var singleplayer = context.worldBuilder().create()) {
            singleplayer.getClientLevel().waitForChunksRender();

            var server = singleplayer.getServer();
            var before = solidBlocksAround(server);

            asPlayer(server, "fmitest build");
            context.waitTicks(10);

            var containers = server.computeOnServer(s -> {
                var level = s.overworld();
                var player = s.getPlayerList().getPlayers().getFirst();
                var found = 0;
                var stacks = 0;
                // The row is laid out within a couple of dozen blocks of where the player stood.
                for (var pos : net.minecraft.core.BlockPos.betweenClosed(
                        player.blockPosition().offset(-30, -3, -30),
                        player.blockPosition().offset(30, 3, 30))) {
                    if (level.getBlockEntity(pos) instanceof Container container) {
                        found++;
                        for (int i = 0; i < container.getContainerSize(); i++) {
                            if (!container.getItem(i).isEmpty()) stacks++;
                        }
                    }
                }
                return new int[]{found, stacks};
            });

            // 11 near containers plus the far one; the double chest is two block entities.
            if (containers[0] < 12) {
                throw new AssertionError("expected at least 12 containers from /fmitest build, found " + containers[0]);
            }
            if (containers[1] < 30) {
                throw new AssertionError("testbed containers should be stocked, found " + containers[1] + " stacks");
            }

            context.takeScreenshot("testbed-built");

            // Walking up to the first chest and opening it must index it like any other chest.
            openNearestChest(context, server);
            var indexed = context.computeOnClient(mc -> FindMyItemsClient.index().snapshot().containers().size());
            if (indexed == 0) {
                throw new AssertionError("opening a testbed chest should have indexed it");
            }

            context.setScreen(() -> null);
            context.waitTicks(5);

            asPlayer(server, "fmitest clear");
            context.waitTicks(10);

            var after = solidBlocksAround(server);
            if (after != before) {
                throw new AssertionError("/fmitest clear should restore the world exactly: "
                        + before + " solid blocks before, " + after + " after");
            }
            context.takeScreenshot("testbed-cleared");
        }
    }

    /** Runs a command the way a player typing it would, rather than as the server console. */
    private static void asPlayer(TestServerContext server, String command) {
        server.runOnServer(s -> {
            var player = s.getPlayerList().getPlayers().getFirst();
            s.getCommands().performPrefixedCommand(player.createCommandSourceStack(), command);
        });
    }

    private static int solidBlocksAround(TestServerContext server) {
        return server.computeOnServer(s -> {
            var level = s.overworld();
            var player = s.getPlayerList().getPlayers().getFirst();
            var solid = 0;
            for (var pos : net.minecraft.core.BlockPos.betweenClosed(
                    player.blockPosition().offset(-30, -3, -30),
                    player.blockPosition().offset(30, 3, 30))) {
                if (!level.getBlockState(pos).is(Blocks.AIR)) solid++;
            }
            return solid;
        });
    }

    private static void openNearestChest(ClientGameTestContext context, TestServerContext server) {
        var chest = server.computeOnServer(s -> {
            var level = s.overworld();
            var player = s.getPlayerList().getPlayers().getFirst();
            for (var pos : net.minecraft.core.BlockPos.betweenClosed(
                    player.blockPosition().offset(-30, -3, -30),
                    player.blockPosition().offset(30, 3, 30))) {
                if (level.getBlockEntity(pos) instanceof ChestBlockEntity) return pos.immutable();
            }
            return null;
        });
        if (chest == null) throw new AssertionError("no chest in the testbed to open");

        // Stand on top of it so the block is unambiguously in reach, then right-click for real.
        server.runOnServer(s -> s.getPlayerList().getPlayers()
                .getFirst().teleportTo(chest.getX() + 0.5, chest.getY() + 1, chest.getZ() + 2.5));
        context.waitTicks(5);
        context.getInput().lookAt(chest);
        context.waitTicks(2);
        context.getInput().holdKeyFor(options -> options.keyUse, 2);
        context.waitTicks(10);
    }
}
