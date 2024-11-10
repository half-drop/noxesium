package com.noxcrew.noxesium.feature.ui.render;

import com.noxcrew.noxesium.NoxesiumMod;
import com.noxcrew.noxesium.feature.ui.LayerWithReference;
import com.noxcrew.noxesium.feature.ui.layer.NoxesiumLayer;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;

import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Holds a group of layers and the buffer they are rendering into.
 */
public class ElementBufferGroup implements Closeable {

    private final ElementBuffer buffer = new ElementBuffer();
    private final List<LayerWithReference> layers = new ArrayList<>();

    /**
     * Stores whether this element should currently be rendered
     * through the buffer, unless we are running a check.
     */
    private boolean optimizing = true;

    /**
     * The current fps at which we check for optimization steps.
     */
    private double checkFps = 1.0;

    /**
     * The current fps at which we re-render the UI elements.
     */
    private double renderFps = NoxesiumMod.getInstance().getConfig().maxUiFramerate;

    private long lastSkippedRender = -1;
    private long nextCheck = -1;
    private long nextRender = -1;
    private boolean justRenderedBuffer = false;

    /**
     * Returns an immutable copy of the layers of this group.
     */
    public List<LayerWithReference> layers() {
        return Collections.unmodifiableList(layers);
    }

    /**
     * Returns the wrapped buffer of this group.
     */
    public ElementBuffer buffer() {
        return buffer;
    }

    /**
     * Returns whether optimizations are currently enabled.
     */
    public boolean optimizing() {
        return optimizing;
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
     * Returns whether the buffer should be used to render from.
     * It is given that the buffer is valid if this is true.
     */
    public boolean shouldUseBuffer() {
        return buffer.isValid() && (justRenderedBuffer || optimizing);
    }

    /**
     * Updates the current state of this group.
     */
    public boolean update(long nanoTime, GuiGraphics guiGraphics, DeltaTracker deltaTracker) {
        // Always start by processing the current buffer
        var frameComparison = buffer.process();
        if (frameComparison.isPresent()) {
            if (frameComparison.get()) {
                // The frames matched, slow down the rendering!
                renderFps = Math.max(NoxesiumMod.getInstance().getConfig().minUiFramerate, renderFps / 2);
            } else {
                // The frames did not match, speed up rendering!
                renderFps = Math.min(NoxesiumMod.getInstance().getConfig().maxUiFramerate, renderFps * 2);
            }
        }

        // Start by determining if we need to run a buffer check yet, we skip
        // updates if we are not optimizing and there is no check to run
        if (nextCheck == -1) {
            nextCheck = nanoTime;
        }

        // Skip the update until we reach the next render time
        var shouldCheck = nextCheck <= nanoTime;
        if (!shouldCheck && !optimizing) {
            justRenderedBuffer = false;
            return false;
        }

        // Determine if we are at the next render threshold yet, otherwise
        // we wait until we have reached it

        // Initialize the value if it's missing
        if (nextRender == -1) {
            nextRender = nanoTime;
        }

        // Determine if any boolean condition has recently changed, if so we
        // make an exception and re-render the buffer this frame!
        var hasChangedRecently = false;
        for (var layer : layers) {
            if (layer.group().hasChangedRecently()) {
                hasChangedRecently = true;
                break;
            }
        }
        if (!hasChangedRecently) {
            // Skip the update until we reach the next render time
            if (nextRender > nanoTime) {
                lastSkippedRender = nanoTime;
                justRenderedBuffer = false;
                return false;
            }

            // Set the next render time
            nextRender = nanoTime + (long) Math.floor(((1 / renderFps) * 1000000000));
        }

        // Prepare the buffer to be drawn to
        if (buffer.bind(guiGraphics)) {
            // Draw the layers onto the buffer
            for (var layer : layers) {
                if (layer.group() == null || layer.group().test()) {
                    renderLayer(guiGraphics, deltaTracker, layer.layer());
                }
            }

            // Actually render things to this buffer
            guiGraphics.flush();

            // Run PBO snapshot creation logic only if we want to run a check
            if (shouldCheck) {
                buffer.snapshot();

                // Determine the next check time
                while (nextCheck <= nanoTime) {
                    nextCheck = nanoTime + (long) Math.floor(((1 / checkFps) * 1000000000));
                }
            }
            justRenderedBuffer = true;
            return true;
        }
        justRenderedBuffer = false;
        return false;
    }

    /**
     * Draws this group directly to the screen.
     */
    public void drawDirectly(GuiGraphics guiGraphics, DeltaTracker deltaTracker) {
        for (var layer : layers) {
            if (layer.group() == null || layer.group().test()) {
                renderLayer(guiGraphics, deltaTracker, layer.layer());
            }
        }
    }

    /**
     * Adds the given layers to this group.
     */
    public void addLayers(Collection<LayerWithReference> layers) {
        this.layers.addAll(layers);
    }

    /**
     * Removes the given layers from this group.
     */
    public void removeLayers(Collection<LayerWithReference> layers) {
        this.layers.removeAll(layers);
    }

    /**
     * Returns whether this group can be split.
     */
    public boolean canSplit() {
        return size() > 1;
    }

    /**
     * Returns the size of this group.
     */
    public int size() {
        return layers.size();
    }

    /**
     * Splits up this group into multiple, returns the
     * new group and edits this group.
     */
    public ElementBufferGroup split() {
        var total = size();
        if (total < 2) throw new IllegalArgumentException("Cannot split up an un-splittable group");
        var half = (int) Math.ceil(((double) total) / 2.0);
        var toSplit = layers.subList(half, total);

        removeLayers(toSplit);
        var newGroup = new ElementBufferGroup();
        newGroup.addLayers(toSplit);
        return newGroup;
    }

    /**
     * Merges another buffer group into this one.
     */
    public void join(ElementBufferGroup other) {
        addLayers(other.layers);
    }

    /**
     * Renders a given layer.
     */
    private void renderLayer(GuiGraphics guiGraphics, DeltaTracker deltaTracker, NoxesiumLayer.Layer layer) {
        // Set up the pose for each layer separately so their locations are correct
        // even if other layers are skipped.
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(0f, 0f, layer.index() * LayeredDraw.Z_SEPARATION);
        layer.layer().render(guiGraphics, deltaTracker);
        guiGraphics.pose().popPose();
    }

    /**
     * Returns the names of this group's layers as a readable string.
     */
    public String layerNames() {
        return layers().stream().map(LayerWithReference::layer).map(NoxesiumLayer.Layer::name).collect(Collectors.joining("/"));
    }

    @Override
    public void close() throws IOException {
        buffer.close();
    }
}
