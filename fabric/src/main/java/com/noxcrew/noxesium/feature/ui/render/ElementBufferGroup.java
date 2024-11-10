package com.noxcrew.noxesium.feature.ui.render;

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

/**
 * Holds a group of layers and the buffer they are rendering into.
 */
public class ElementBufferGroup implements Closeable {

    private final ElementBuffer buffer = new ElementBuffer();
    private final List<NoxesiumLayer.Layer> layers = new ArrayList<>();
    private boolean lastFrameMatched = false;

    /**
     * Returns an immutable copy of the layers of this group.
     */
    public List<NoxesiumLayer.Layer> layers() {
        return Collections.unmodifiableList(layers);
    }

    /**
     * Returns the wrapped buffer of this group.
     */
    public ElementBuffer buffer() {
        return buffer;
    }

    /**
     * The current frame rate of this group.
     */
    public int framerate() {
        return lastFrameMatched ? 0 : 260;
    }

    /**
     * Returns whether the buffer should be used to render from.
     * It is given that the buffer is valid if this is true.
     */
    public boolean shouldUseBuffer() {
        // TODO Don't use buffer if a layer is constantly changing
        return buffer.isValid();
    }

    /**
     * Updates the current state of this group.
     */
    public boolean update(GuiGraphics guiGraphics, DeltaTracker deltaTracker) {
        // If the last frame matched we stop updating the buffer!
        if (lastFrameMatched) return false;

        // Prepare the buffer to be drawn to
        if (buffer.bind(guiGraphics)) {
            // Draw the gui graphics onto the buffer
            // TODO Start keeping buffers around and drawing them increasingly infrequent when they match up
            // TODO Merge together neighboring buffers that are on the same cycle
            for (var layer : layers) {
                renderLayer(guiGraphics, deltaTracker, layer);
            }

            // Run PBO snapshot creation logic
            // TODO Only run PBO logic whenever want
            // to know about its results as its slow
            buffer.snapshot();
            lastFrameMatched = buffer.process();
            return true;
        }
        return false;
    }

    /**
     * Draws this group directly to the screen.
     */
    public void drawDirectly(GuiGraphics guiGraphics, DeltaTracker deltaTracker) {
        for (var layer : layers) {
            renderLayer(guiGraphics, deltaTracker, layer);
        }
    }

    /**
     * Adds the given layers to this group.
     */
    public void addLayers(Collection<NoxesiumLayer.Layer> layers) {
        this.layers.addAll(layers);
    }

    /**
     * Removes the given layers from this group.
     */
    public void removeLayers(Collection<NoxesiumLayer.Layer> layers) {
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

    @Override
    public void close() throws IOException {
        buffer.close();
    }
}
