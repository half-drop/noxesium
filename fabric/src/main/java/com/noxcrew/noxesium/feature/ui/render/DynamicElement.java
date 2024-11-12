package com.noxcrew.noxesium.feature.ui.render;

import com.noxcrew.noxesium.NoxesiumMod;
import net.minecraft.client.gui.GuiGraphics;

import java.io.Closeable;
import java.nio.ByteBuffer;
import java.util.Random;

/**
 * Manages a buffer and its current dynamic fps.
 */
public class DynamicElement implements Closeable {

    // Stores whether any elements were drawn.
    public static boolean elementsWereDrawn = false;

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

        // Draw the layers onto the buffer
        elementsWereDrawn = false;
        draw.run();

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
