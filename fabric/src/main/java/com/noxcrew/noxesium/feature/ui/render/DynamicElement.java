package com.noxcrew.noxesium.feature.ui.render;

import com.mojang.blaze3d.platform.GlStateManager;
import com.noxcrew.noxesium.NoxesiumMod;
import com.noxcrew.noxesium.feature.ui.render.api.BlendState;
import com.noxcrew.noxesium.feature.ui.render.api.BlendStateHook;
import net.minecraft.client.gui.GuiGraphics;
import org.lwjgl.opengl.GL14;

import java.io.Closeable;
import java.nio.ByteBuffer;
import java.util.Random;

/**
 * Manages a buffer and its current dynamic fps.
 */
public class DynamicElement implements Closeable, BlendStateHook {

    // Stores whether any elements were drawn.
    public static boolean elementsWereDrawn = false;

    public static final BlendState DEFAULT_BLEND_STATE = BlendState.standard();
    public static final BlendState GLINT_BLEND_STATE = BlendState.glint();

    private static final Random random = new Random();
    private final ElementBuffer buffer;
    private boolean bufferEmpty = false;
    private boolean needsRedraw = true;
    private long nextCheck;
    private long nextRender = -1;
    private int failedCheckCount = 0;

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
    public boolean update(long nanoTime, GuiGraphics guiGraphics, Runnable draw) {
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

        // Actually render things to this buffer
        guiGraphics.flush();
        SharedVertexBuffer.blendStateHook = null;

        // Nothing was drawn, this layer does not contain anything!
        bufferEmpty = !elementsWereDrawn;

        // Run PBO snapshot creation logic only if we want to run a check
        if (buffer.canSnapshot() && nextCheck <= nanoTime) {
            buffer.snapshot(bufferEmpty);
        }
        return true;
    }

    @Override
    public boolean changeState(boolean newValue) {
        // Ignore any enabling of blending as blending is already enabled
        if (newValue) return false;

        // If blending is turned off at any point we don't need to fork the buffer, we just need to temporarily change how
        // we approach blending. We want to copy the RGB normally but set the alpha to a static value of 255. For this we
        // use the constant color system.
        SharedVertexBuffer.ignoreBlendStateHook = true;
        GlStateManager._blendFuncSeparate(
                // Copy normal colors directly
                GL14.GL_ONE,
                GL14.GL_ZERO,
                // Use the constant color's alpha value
                // for the resulting pixel in the buffer,
                // since the buffer starts out being entirely
                // transparent this puts a fully opaque
                // pixels at any pixel we draw to when not
                // blending.
                GL14.GL_CONSTANT_ALPHA,
                GL14.GL_ZERO
        );
        SharedVertexBuffer.ignoreBlendStateHook = false;

        // Don't let the blending disable go through
        return true;
    }

    @Override
    public boolean changeFunc(int srcRgb, int dstRgb, int srcAlpha, int dstAlpha) {
        // Ignore any changes that do not actually change from the default blend state, or
        // if they are specific types of blend states that are permitted.
        if (DEFAULT_BLEND_STATE.matches(srcRgb, dstRgb, srcAlpha, dstAlpha) ||
                // We allow glint states as this is one that specifically applies edits to an existing
                // item that was just rendered in the same buffer.
                GLINT_BLEND_STATE.matches(srcRgb, dstRgb, srcAlpha, dstAlpha)) return false;

        // TODO Split the buffer!
        // System.out.println("Layer: " + layer + "Changing to " + srcRgb + ", " + dstRgb + ", " + srcAlpha + ", " + dstAlpha);
        return false;
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
