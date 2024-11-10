package com.noxcrew.noxesium.feature.ui.layer;

import net.minecraft.client.gui.LayeredDraw;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;

/**
 * The different contents of a NoxesiumLayeredDraw.
 */
public sealed interface NoxesiumLayer {

    /**
     * Stores a collection of layers.
     */
    record LayerGroup(
        List<NoxesiumLayer> layers,
        BooleanSupplier condition
    ) implements NoxesiumLayer {
    }

    /**
     * Stores a singular layer that is always rendered.
     */
    record Layer(
        int index,
        String name,
        LayeredDraw.Layer layer
    ) implements NoxesiumLayer {

        // A global variable used for layer indices!
        private static int LAST_LAYER_INDEX = -1;
        private static final Map<Integer, String> STANDARD_LAYER_NAMES = new HashMap<>();

        static {
            STANDARD_LAYER_NAMES.put(0, "Camera Overlays");
            STANDARD_LAYER_NAMES.put(1, "Crosshair");
            STANDARD_LAYER_NAMES.put(2, "Hotbar");
            STANDARD_LAYER_NAMES.put(3, "XP Level");
            STANDARD_LAYER_NAMES.put(4, "Effects");
            STANDARD_LAYER_NAMES.put(5, "Bossbar");
            STANDARD_LAYER_NAMES.put(6, "Sleep Overlay");
            STANDARD_LAYER_NAMES.put(7, "Demo Overlay");
            STANDARD_LAYER_NAMES.put(8, "Debug Overlay");
            STANDARD_LAYER_NAMES.put(9, "Scoreboard");
            STANDARD_LAYER_NAMES.put(10, "Actionbar");
            STANDARD_LAYER_NAMES.put(11, "Title");
            STANDARD_LAYER_NAMES.put(12, "Chat");
            STANDARD_LAYER_NAMES.put(13, "Tab List");
            STANDARD_LAYER_NAMES.put(14, "Subtitles");
        }

        public Layer(LayeredDraw.Layer layer) {
            this(++LAST_LAYER_INDEX, STANDARD_LAYER_NAMES.getOrDefault(LAST_LAYER_INDEX, "Layer #" + LAST_LAYER_INDEX), layer);
        }

        public Layer(String name, LayeredDraw.Layer layer) {
            this(++LAST_LAYER_INDEX, name, layer);
        }
    }
}
