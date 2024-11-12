package com.noxcrew.noxesium.feature.ui.render;

import com.noxcrew.noxesium.NoxesiumMod;
import com.noxcrew.noxesium.feature.ui.render.api.BlendState;
import com.noxcrew.noxesium.feature.ui.render.api.BlendStateHook;
import net.minecraft.client.gui.GuiGraphics;
import org.lwjgl.opengl.GL11;

import java.io.Closeable;
import java.nio.ByteBuffer;
import java.util.Random;

/**
 * Manages a buffer and its current dynamic fps.
 */
public class DynamicElement implements Closeable, BlendStateHook {

    // Stores whether any elements were drawn.
    public static boolean elementsWereDrawn = false;

    private static final Random random = new Random();
    private final ElementBuffer buffer;
    private boolean bufferEmpty = false;
    private boolean needsRedraw = true;
    private long nextCheck;
    private long nextRender = -1;
    private int failedCheckCount = 0;
    private BlendState blendState;

    /**
     * The current fps at which we check for optimization steps.
     */
    private double checkFps = NoxesiumMod.getInstance().getConfig().minUiFramerate;

    /**
     * The current fps at which we re-render the UI elements.
     */
    private double renderFps = NoxesiumMod.getInstance().getConfig().maxUiFramerate;

    public DynamicElement(boolean useDepth) {
        buffer = new ElementBuffer(useDepth);

        // Determine a random nano time
        nextCheck = System.nanoTime() + (long) Math.floor(((1 / checkFps) * random.nextDouble() * 1000000000));
    }

    /**
     * Returns the internal buffer of this element.
     */
    public ElementBuffer buffer() {
        return buffer;
    }

    /**
     * Request an immediate redraw for this element.
     */
    public void redraw() {
        needsRedraw = true;
    }

    /**
     * The current frame rate of this group.
     */
    public int renderFramerate() {
        return (int) Math.floor(renderFps);
    }

    /**
     * The current update frame rate of this group.
     */
    public double updateFramerate() {
        return checkFps;
    }

    /**
     * Returns whether the buffer is invalid.
     */
    public boolean isInvalid() {
        return buffer.isInvalid();
    }

    /**
     * Returns the texture id of this element.
     */
    public int getTextureId() {
        return bufferEmpty ? -1 : buffer.getTextureId();
    }

    /**
     * Returns whether this element is ready to be considered
     * for group merging/joining.
     */
    public boolean isReady() {
        return !needsRedraw && buffer.hasValidPBO();
    }

    /**
     * Returns whether this element is often changing. Used to determine
     * when it should be split up this buffer.
     */
    public boolean isOftenChanging() {
        return failedCheckCount >= 20;
    }

    /**
     * Process recently taken snapshots to determine changes.
     */
    public void tick() {
        // Process the snapshots
        var snapshots = buffer.snapshots();
        if (snapshots == null) return;

        var empty = buffer.emptySnapshots();
        if (compare(empty, snapshots[0], snapshots[1])) {
            // The frames matched, slow down the rendering!
            renderFps = Math.max(NoxesiumMod.getInstance().getConfig().minUiFramerate, renderFps / 2);

            // Count how often the check succeeded and slot it down by up to 5x
            failedCheckCount = Math.min(-1, failedCheckCount - 1);
            if (failedCheckCount <= -10) {
                checkFps = ((double) NoxesiumMod.getInstance().getConfig().minUiFramerate / Math.min(5, -failedCheckCount / 10));
            } else {
                checkFps = NoxesiumMod.getInstance().getConfig().minUiFramerate;
            }
        } else {
            // The frames did not match, back to full speed!
            renderFps = NoxesiumMod.getInstance().getConfig().maxUiFramerate;

            // Count how often the check failed and slow it down by up to 5x
            failedCheckCount = Math.max(1, failedCheckCount + 1);
            if (failedCheckCount >= 10) {
                checkFps = ((double) NoxesiumMod.getInstance().getConfig().minUiFramerate / Math.min(5, failedCheckCount / 10));
            } else {
                checkFps = NoxesiumMod.getInstance().getConfig().minUiFramerate;
            }
        }
        buffer.requestNewPBO();

        // Determine the next check time
        var nanoTime = System.nanoTime();
        while (nextCheck <= nanoTime) {
            nextCheck = nanoTime + (long) Math.floor(((1 / checkFps) * 1000000000));
        }
    }

    /**
     * Updates the current state of this element.
     */
    public boolean update(long nanoTime, GuiGraphics guiGraphics, BlendState blendState, Runnable draw) {
        // Use the blend state while making edits for this object
        this.blendState = blendState;

        // Always start by awaiting the GPU fence
        buffer.awaitFence();

        // Determine if we are at the next render threshold yet, otherwise
        // we wait until we have reached it

        // Initialize the value if it's missing
        if (nextRender == -1) {
            nextRender = nanoTime;
        }

        // Skip the update until we reach the next render time
        if (!needsRedraw && nextRender > nanoTime) return false;
        needsRedraw = false;

        // Set the next render time
        nextRender = nanoTime + (long) Math.floor(((1 / renderFps) * 1000000000));

        // Bind the buffer, abort is something goes wrong
        if (!buffer.bind(guiGraphics)) return false;

        // Draw the layers onto the buffer while capturing the blending state
        elementsWereDrawn = false;
        SharedVertexBuffer.blendStateHook = this;
        draw.run();
        SharedVertexBuffer.blendStateHook = null;

        // Actually render things to this buffer
        guiGraphics.flush();

        // Nothing was drawn, this layer does not contain anything!
        bufferEmpty = !elementsWereDrawn;

        // Run PBO snapshot creation logic only if we want to run a check
        if (buffer.canSnapshot() && nextCheck <= nanoTime) {
            buffer.snapshot(bufferEmpty);
        }
        return true;
    }

    /*
        Default blending is:
            RenderSystem.enableBlend();
            RenderSystem.blendFuncSeparate(
                GlStateManager.SourceFactor.SRC_ALPHA,
                GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                GlStateManager.SourceFactor.ONE,
                GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA
            );

        In vanilla we expect the following blend state changes:
            Vignette has VIGNETTE_TRANSPARENCY with:
                RenderSystem.enableBlend();
                RenderSystem.blendFunc(GlStateManager.SourceFactor.ZERO, GlStateManager.DestFactor.ONE_MINUS_SRC_COLOR);

            Crosshair has CROSSHAIR_TRANSPARENCY with:
                RenderSystem.enableBlend();
                RenderSystem.blendFuncSeparate(
                    GlStateManager.SourceFactor.ONE_MINUS_DST_COLOR,
                    GlStateManager.DestFactor.ONE_MINUS_SRC_COLOR,
                    GlStateManager.SourceFactor.ONE,
                    GlStateManager.DestFactor.ZERO
                );

            Nausea overlay has NAUSEA_OVERLAY_TRANSPARENCY with:
                RenderSystem.enableBlend();
                RenderSystem.blendFuncSeparate(
                    GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE, GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE
                );

            Hotbar items use entity cutout which has NO_TRANSPARENCY with:
                RenderSystem.disableBlend();
                (default blend func is implied but unnecessary)

            Hotbar item glints uses GLINT_TRANSPERNCY with:
                RenderSystem.enableBlend();
                RenderSystem.blendFuncSeparate(
                    GlStateManager.SourceFactor.SRC_COLOR, GlStateManager.DestFactor.ONE, GlStateManager.SourceFactor.ZERO, GlStateManager.DestFactor.ONE
                );

            Likely other overlays used.

            Our default blend function for drawing the buffers is:
            GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA
     */

    @Override
    public boolean changeState(boolean newValue) {
        if (newValue == blendState.enabled()) return true;
        // System.out.println("blend state -> " + newValue);
        if (!newValue) {
            // Whenever blending is disabled we trigger a change to the default blend function as well
            // as vanilla assumes this to be the default when it's given that blending is off
            changeFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
        }
        return true;
    }

    @Override
    public boolean changeFunc(int srcRgb, int dstRgb, int srcAlpha, int dstAlpha) {
        if (srcRgb == blendState.srcRgb() && dstRgb == blendState.dstRgb() && srcAlpha == blendState.srcAlpha() && dstAlpha == blendState.dstAlpha()) return true;
        // System.out.println("blend func -> " + srcRgb + ", " + dstRgb + ", " + srcAlpha + ", " + dstAlpha);
        return true;
    }

    @Override
    public void close() {
        buffer.close();
    }

    /**
     * Compares two frame snapshots.
     */
    private boolean compare(boolean[] empty, ByteBuffer first, ByteBuffer second) {
        // If both are empty, they match.
        if (empty[0] && empty[1]) return true;

        // If one is empty but the other isn't, they don't match.
        if (empty[0] || empty[1]) return false;

        return first.mismatch(second) == -1;
    }
}
