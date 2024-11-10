package com.noxcrew.noxesium.feature.ui.layer;

import com.noxcrew.noxesium.NoxesiumMod;
import com.noxcrew.noxesium.feature.rule.ServerRules;
import com.noxcrew.noxesium.feature.ui.render.NoxesiumUiRenderState;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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
     * Returns the current render state.
     */
    @Nullable
    public NoxesiumUiRenderState state() {
        return state;
    }

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
        if (NoxesiumMod.getInstance().getConfig().shouldDisableExperimentalPerformancePatches() ||
            ServerRules.DISABLE_UI_OPTIMIZATIONS.getValue()) {
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

    /**
     * Returns a flattened list of this object.
     */
    public List<NoxesiumLayer.Layer> flatten() {
        var result = new ArrayList<NoxesiumLayer.Layer>();
        for (var layer : layers) {
            switch (layer) {
                case NoxesiumLayer.Layer single -> result.add(single);
                case NoxesiumLayer.LayerGroup group -> process(group, result);
            }
        }
        return result;
    }

    /**
     * Adds the contents of the layer group to the given list.
     */
    private void process(NoxesiumLayer.LayerGroup target, List<NoxesiumLayer.Layer> list) {
        for (var layer : target.layers()) {
            switch (layer) {
                case NoxesiumLayer.Layer single -> list.add(single);
                case NoxesiumLayer.LayerGroup group -> process(group, list);
            }
        }
    }

    /**
     * Returns the size of this layered draw.
     */
    public int size() {
        var total = 0;
        for (var layer : layers) {
            switch (layer) {
                case NoxesiumLayer.Layer ignored -> total++;
                case NoxesiumLayer.LayerGroup layerGroup -> total += count(layerGroup);
            }
        }
        return total;
    }

    /**
     * Returns the size of the given layer group.
     */
    private int count(NoxesiumLayer.LayerGroup group) {
        var total = 0;
        for (var layer : group.layers()) {
            switch (layer) {
                case NoxesiumLayer.Layer ignored -> total++;
                case NoxesiumLayer.LayerGroup layerGroup -> total += count(layerGroup);
            }
        }
        return total;
    }
}
