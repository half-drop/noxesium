package com.noxcrew.noxesium.feature.ui.layer;

import com.noxcrew.noxesium.NoxesiumMod;
import com.noxcrew.noxesium.feature.ui.render.NoxesiumUiRenderState;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A custom implementation of layered draw that persists groupings
 * of layers to properly be able to distinguish between them.
 */
public class NoxesiumLayeredDraw implements LayeredDraw.Layer {

    private final List<NoxesiumLayer> layers = new ArrayList<>();
    private NoxesiumUiRenderState state;

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

    /**
     * Clears out all cached data.
     */
    public void clear() {
        if (state == null) return;
        try {
            // Whenever the display is resized we want to fully reset the render state
            // so we instantly break down all progress.
            state.close();
            state = null;
        } catch (IOException e) {
            NoxesiumMod.getInstance().getLogger().error("Failed to break down Noxesium UI render state", e);
        }
    }

    @Override
    public void render(GuiGraphics guiGraphics, @NotNull DeltaTracker deltaTracker) {
        // If experimental patches are disabled we ignore all custom logic.
        if (NoxesiumMod.getInstance().getConfig().shouldDisableExperimentalPerformancePatches()) {
            // Destroy the state if it exists
            if (state != null) state = null;

            // Directly draw everything to the screen
            guiGraphics.pose().pushPose();
            for (var layer : layers) {
                renderLayerDirectly(guiGraphics, deltaTracker, layer);
            }
            guiGraphics.pose().popPose();
            return;
        }

        // Defer rendering the object to the current state, this holds all necessary information
        // for rendering.
        if (state == null) {
            // Create a new state object if we don't have one yet, we do this here because
            // not all LayeredDraw objects are actually used for rendering because Mojang
            // uses them for sub-groups as well.
            state = new NoxesiumUiRenderState();
        }
        state.render(guiGraphics, deltaTracker, this);
    }

    /**
     * Renders a single layer directly, avoiding all custom UI optimizations.
     */
    private void renderLayerDirectly(GuiGraphics guiGraphics, DeltaTracker deltaTracker, NoxesiumLayer layer) {
        switch (layer) {
            case NoxesiumLayer.Layer single -> {
                single.layer().render(guiGraphics, deltaTracker);
                guiGraphics.pose().translate(0f, 0f, LayeredDraw.Z_SEPARATION);
            }
            case NoxesiumLayer.LayerGroup group -> {
                if (group.condition().getAsBoolean()) {
                    for (var subLayer : group.layers()) {
                        renderLayerDirectly(guiGraphics, deltaTracker, subLayer);
                    }
                }
            }
        }
    }
}
