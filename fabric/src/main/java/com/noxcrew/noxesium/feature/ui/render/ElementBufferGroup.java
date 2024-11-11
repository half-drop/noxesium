package com.noxcrew.noxesium.feature.ui.render;

import com.noxcrew.noxesium.feature.ui.LayerWithReference;
import com.noxcrew.noxesium.feature.ui.layer.NoxesiumLayer;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;

import java.io.Closeable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Holds a group of layers and the buffer they are rendering into.
 */
public class ElementBufferGroup implements Closeable {

    private final DynamicElement dynamic = new DynamicElement(true);
    private final List<LayerWithReference> layers = new ArrayList<>();

    /**
     * Returns the dynamic element used by this group.
     */
    public DynamicElement dynamic() {
        return dynamic;
    }

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
        return dynamic.buffer();
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
    public void renderLayer(GuiGraphics guiGraphics, DeltaTracker deltaTracker, NoxesiumLayer.Layer layer) {
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
    public void close() {
        dynamic.close();
    }
}
