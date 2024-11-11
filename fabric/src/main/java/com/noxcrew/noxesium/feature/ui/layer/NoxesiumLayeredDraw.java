package com.noxcrew.noxesium.feature.ui.layer;

import com.noxcrew.noxesium.NoxesiumMod;
import com.noxcrew.noxesium.feature.rule.ServerRules;
import com.noxcrew.noxesium.feature.ui.LayerWithReference;
import com.noxcrew.noxesium.feature.ui.render.api.NoxesiumRenderState;
import com.noxcrew.noxesium.feature.ui.render.api.NoxesiumRenderStateHolder;
import com.noxcrew.noxesium.feature.ui.render.NoxesiumUiRenderState;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A custom implementation of layered draw that persists groupings
 * of layers to properly be able to distinguish between them.
 */
public class NoxesiumLayeredDraw implements LayeredDraw.Layer, NoxesiumRenderStateHolder<NoxesiumUiRenderState> {

    private final List<NoxesiumLayer> layers = new ArrayList<>();
    private final List<NoxesiumLayer.LayerGroup> subgroups = new ArrayList<>();
    private int size;
    private NoxesiumUiRenderState state;

    /**
     * Returns an unmodifiable copy of this object's layers.
     */
    public List<NoxesiumLayer> layers() {
        return Collections.unmodifiableList(layers);
    }

    /**
     * Returns all groups within this layered draw.
     */
    public List<NoxesiumLayer.LayerGroup> subgroups() {
        return subgroups;
    }

    /**
     * Adds a new layer to this object.
     */
    public void add(NoxesiumLayer layer) {
        layers.add(layer);
        if (layer instanceof NoxesiumLayer.LayerGroup group) {
            subgroups.add(group);
        }

        // We naively assume groups are not edited
        // after being added so we don't need to actually
        // track the size we just want to know if there
        // were new layers added.
        size++;
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
    public List<LayerWithReference> flatten() {
        var result = new ArrayList<LayerWithReference>();
        for (var layer : layers) {
            switch (layer) {
                case NoxesiumLayer.Layer single -> result.add(new LayerWithReference(single, null));
                case NoxesiumLayer.LayerGroup group -> process(group, result);
            }
        }
        return result;
    }

    /**
     * Adds the contents of the layer group to the given list.
     */
    private void process(NoxesiumLayer.LayerGroup target, List<LayerWithReference> list) {
        for (var layer : target.layers()) {
            switch (layer) {
                case NoxesiumLayer.Layer single -> list.add(new LayerWithReference(single, target));
                case NoxesiumLayer.LayerGroup group -> process(group, list);
            }
        }
    }

    /**
     * Returns the size of this layered draw.
     */
    public int size() {
        return size;
    }

    @Override
    public @Nullable NoxesiumRenderState get() {
        return state;
    }

    @Override
    public void clear() {
        if (state != null) {
            state.close();
            state = null;
        }
    }
}
