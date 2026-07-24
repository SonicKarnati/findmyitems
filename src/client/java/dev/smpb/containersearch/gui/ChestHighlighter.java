package dev.smpb.containersearch.gui;

import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.CompareOp;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.LayeringTransform;
import net.minecraft.client.renderer.rendertype.OutputTarget;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Draws a pulsing gold outline around a container for a few seconds.
 *
 * <p>Entirely client-side and entirely visual: the highlighted positions live in a set here, and
 * every frame their block outlines are submitted as line geometry. Nothing is sent to the server
 * and no entity is spawned — the earlier version faked the vanilla glow by spawning a client-only
 * glass {@code BlockDisplay}, which fought the real block for depth and had to be inflated to stop
 * flickering.
 *
 * <p>Visible through walls, which is the whole point of "where is my iron": that needs a depth test
 * that always passes, and no vanilla pipeline has one. {@link #SEE_THROUGH_LINES} is vanilla's own
 * line pipeline with exactly that one property changed. If building it ever fails — a renderer
 * replacing the pipeline internals, say — {@link #lineType()} falls back to plain depth-tested
 * lines, so the highlight degrades to "visible when not behind something" rather than crashing.
 */
public final class ChestHighlighter {
    /** 100 ticks = 5 seconds at 20 tps. */
    private static final int HIGHLIGHT_TICKS = 100;
    /** Past this the outline is a couple of pixels of noise; not worth the draw call. */
    private static final double MAX_DISTANCE = 128.0;

    /** Gold reads against stone, wood and grass alike, and is not a vanilla team colour. */
    private static final float RED = 1.0f;
    private static final float GREEN = 0.72f;
    private static final float BLUE = 0.1f;
    /** Radians per tick of the brightness pulse — about one beat a second. */
    private static final float PULSE_SPEED = 0.15f;

    private static final RenderType SEE_THROUGH_LINES = createSeeThroughLines();

    private static final Set<BlockPos> ACTIVE = new HashSet<>();
    private static String dimension = "";
    private static int ticksLeft;

    private ChestHighlighter() {}

    public static void init() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> tick());
        LevelRenderEvents.COLLECT_SUBMITS.register(ChestHighlighter::render);
    }

    /** Highlight the given blocks (all positions of one container). Replaces any previous highlight. */
    public static void highlight(List<BlockPos> positions, String dimension) {
        ACTIVE.clear();
        for (var pos : positions) ACTIVE.add(pos.immutable());
        ChestHighlighter.dimension = dimension;
        ticksLeft = HIGHLIGHT_TICKS;
    }

    private static void tick() {
        if (ACTIVE.isEmpty()) return;
        if (--ticksLeft <= 0) ACTIVE.clear();
    }

    private static void render(LevelRenderContext context) {
        if (ACTIVE.isEmpty()) return;

        var level = Minecraft.getInstance().level;
        if (level == null) return;
        if (!level.dimension().identifier().toString().equals(dimension)) return;

        var camera = context.levelState().cameraRenderState.pos;
        var poseStack = context.poseStack();
        var collector = context.submitNodeCollector();
        var lineWidth = context.gameRenderer().gameRenderState().windowRenderState.appropriateLineWidth;
        var color = pulseColor(level.getGameTime());

        for (var pos : ACTIVE) {
            if (camera.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > MAX_DISTANCE * MAX_DISTANCE) {
                continue;
            }

            var state = level.getBlockState(pos);
            var shape = state.getShape(level, pos);
            if (shape.isEmpty()) continue;

            poseStack.pushPose();
            poseStack.translate(pos.getX() - camera.x, pos.getY() - camera.y, pos.getZ() - camera.z);
            collector.submitShapeOutline(poseStack, shape, lineType(), color, lineWidth, false);
            poseStack.popPose();
        }
    }

    /** Breathes between dim and full so the highlight reads as "this one" even in a wall of chests. */
    private static int pulseColor(long gameTime) {
        var pulse = 0.65f + 0.35f * Mth.sin(gameTime * PULSE_SPEED);
        return ARGB.colorFromFloat(0.55f + 0.45f * pulse, RED, GREEN * pulse, BLUE);
    }

    private static RenderType lineType() {
        return SEE_THROUGH_LINES != null ? SEE_THROUGH_LINES : RenderTypes.lines();
    }

    /** Vanilla's line pipeline with the depth test forced to always pass, so walls do not hide it. */
    private static RenderType createSeeThroughLines() {
        try {
            var pipeline = RenderPipeline.builder(RenderPipelines.LINES_SNIPPET)
                    .withLocation(Identifier.fromNamespaceAndPath("container-search", "pipeline/see_through_lines"))
                    .withDepthStencilState(new DepthStencilState(CompareOp.ALWAYS_PASS, false))
                    .build();

            return RenderType.create("container_search_see_through_lines", RenderSetup.builder(pipeline)
                    .setLayeringTransform(LayeringTransform.VIEW_OFFSET_Z_LAYERING)
                    .setOutputTarget(OutputTarget.ITEM_ENTITY_TARGET)
                    .createRenderSetup());
        } catch (Throwable t) {
            t.printStackTrace();
            return null;
        }
    }
}
