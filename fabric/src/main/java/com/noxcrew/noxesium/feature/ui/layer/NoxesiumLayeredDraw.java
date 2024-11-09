package com.noxcrew.noxesium.feature.ui.layer;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A custom implementation of layered draw that persists groupings
 * of layers to properly be able to distinguish between them.
 */
public class NoxesiumLayeredDraw implements LayeredDraw.Layer {

    private final List<NoxesiumLayer> layers = new ArrayList<>();

    /**
     * Returns an unmodifiable copy of this object's layers.
     */
    public List<NoxesiumLayer> layers() {
        return Collections.unmodifiableList(layers);
    }

    /**
     * Adds a new layer to this object.
     */
    public void add(NoxesiumLayer layer) {
        layers.add(layer);
    }

    @Override
    public void render(GuiGraphics guiGraphics, @NotNull DeltaTracker deltaTracker) {
        guiGraphics.pose().pushPose();
        for (var layer : layers) {
            renderLayer(guiGraphics, deltaTracker, layer);
        }
        guiGraphics.pose().popPose();
    }

    /**
     * Renders a single layer.
     */
    private void renderLayer(GuiGraphics guiGraphics, DeltaTracker deltaTracker, NoxesiumLayer layer) {
        switch (layer) {
            case NoxesiumLayer.Layer single -> {
                single.layer().render(guiGraphics, deltaTracker);
                guiGraphics.pose().translate(0f, 0f, LayeredDraw.Z_SEPARATION);
            }
            case NoxesiumLayer.LayerGroup group -> {
                if (group.condition().getAsBoolean()) {
                    for (var subLayer : group.layers()) {
                        renderLayer(guiGraphics, deltaTracker, subLayer);
                    }
                }
            }
        }
    }
}
