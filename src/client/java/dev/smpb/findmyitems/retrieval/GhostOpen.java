package dev.smpb.findmyitems.retrieval;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundContainerClosePacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Runs a catalog action as if the player had walked up and opened the container: a real
 * right-click packet goes to the server, so the lid swings, the sound plays and the block's
 * opener count ticks up — then the action runs, then the container is closed again.
 *
 * <p>The container GUI never reaches the screen. The server's open packet does put it up, but
 * {@link ScreenEvents#AFTER_INIT} fires inside the same client task drain that handled the packet,
 * so swapping the catalog back in there happens before any frame is rendered.
 *
 * <p>The menu itself is deliberately left on the player — only the <em>screen</em> is taken away.
 * That keeps the client's slot syncing pointed at the menu the server is broadcasting to, so
 * items landing in the inventory show up immediately instead of desyncing until the next reload.
 */
public final class GhostOpen {
    /** A chest lid swings open over roughly this long; closing sooner looks like a twitch. */
    private static final int HOLD_TICKS = 10;
    /** No open packet came back — the server refused the interaction. Stop waiting and act anyway. */
    private static final int OPEN_TIMEOUT_TICKS = 20;

    private enum Phase { IDLE, WAITING, HOLDING, CLOSING }

    private static Phase phase = Phase.IDLE;
    private static Runnable action;
    private static Screen returnTo;
    private static int ticks;

    private GhostOpen() {}

    public static void init() {
        ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> onScreenOpened(client, screen));
        ClientTickEvents.END_CLIENT_TICK.register(client -> tick());
    }

    /**
     * Opens the container at {@code pos}, runs {@code action} once it is open, and closes it.
     * Falls straight through to {@code action} when the container cannot be opened for real.
     */
    public static void openThen(BlockPos pos, Runnable action) {
        var mc = Minecraft.getInstance();
        var player = mc.player;

        // One at a time. And never while sneaking: a sneaking right-click on a chest places the
        // held block instead of opening it, which would be a genuinely destructive surprise.
        if (phase != Phase.IDLE || !canOpen(pos)) {
            return;
        }

        GhostOpen.action = action;
        returnTo = mc.gui.screen();
        phase = Phase.WAITING;
        ticks = OPEN_TIMEOUT_TICKS;

        var hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
        mc.gameMode.useItemOn(player, InteractionHand.MAIN_HAND, hit);
    }

    public static boolean canOpen(BlockPos pos) {
        var mc = Minecraft.getInstance();
        var player = mc.player;
        return player != null && mc.gameMode != null && !player.isShiftKeyDown()
                && ReachabilityService.shared().check(pos, TargetKind.CONTAINER).actionable();
    }

    private static void onScreenOpened(Minecraft mc, Screen screen) {
        if (phase != Phase.WAITING || !(screen instanceof AbstractContainerScreen<?>)) return;

        phase = Phase.HOLDING;
        ticks = HOLD_TICKS;
        mc.execute(() -> {
            if (mc.gui.screen() == screen) mc.gui.setScreen(returnTo);
        });
    }

    private static void tick() {
        if (phase == Phase.IDLE || --ticks > 0) return;

        switch (phase) {
            case WAITING -> {
                var pending = action;
                reset();
                pending.run();
            }
            case HOLDING -> {
                action.run();
                // The action does its work on the server thread; give it a tick to land before the
                // close packet queues up behind it.
                phase = Phase.CLOSING;
                ticks = 2;
            }
            case CLOSING -> {
                close();
                reset();
            }
            case IDLE -> {}
        }
    }

    private static void close() {
        var mc = Minecraft.getInstance();
        var player = mc.player;
        var connection = mc.getConnection();
        if (player == null || connection == null) return;

        var menu = player.containerMenu;
        if (menu == player.inventoryMenu) return;

        connection.send(new ServerboundContainerClosePacket(menu.containerId));
        // Not LocalPlayer#closeContainer: that also clears the current screen, which would shut the
        // catalog and re-grab the mouse. The server resyncs the inventory menu on its own side.
        player.containerMenu = player.inventoryMenu;
    }

    private static void reset() {
        phase = Phase.IDLE;
        action = null;
        returnTo = null;
    }
}
