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
            STANDARD_LAYER_NAMES.put(0, "Vanilla - Camera Overlays");
            STANDARD_LAYER_NAMES.put(1, "Vanilla - Crosshair");
            STANDARD_LAYER_NAMES.put(2, "Vanilla - Hotbar and Decorations");
            STANDARD_LAYER_NAMES.put(3, "Vanilla - Experience Level");
            STANDARD_LAYER_NAMES.put(4, "Vanilla - Effects");
            STANDARD_LAYER_NAMES.put(5, "Vanilla - Boss Overlay");
            STANDARD_LAYER_NAMES.put(6, "Vanilla - Sleep Overlay");
            STANDARD_LAYER_NAMES.put(7, "Vanilla - Demo Overlay");
            STANDARD_LAYER_NAMES.put(8, "Vanilla - Debug Overlay");
            STANDARD_LAYER_NAMES.put(9, "Vanilla - Scoreboard");
            STANDARD_LAYER_NAMES.put(10, "Vanilla - Overlay Message");
            STANDARD_LAYER_NAMES.put(11, "Vanilla - Title");
            STANDARD_LAYER_NAMES.put(12, "Vanilla - Chat");
            STANDARD_LAYER_NAMES.put(13, "Vanilla - Tab List");
            STANDARD_LAYER_NAMES.put(14, "Vanilla - Subtitle Overlay");
        }

        public Layer(LayeredDraw.Layer layer) {
            this(++LAST_LAYER_INDEX, STANDARD_LAYER_NAMES.getOrDefault(LAST_LAYER_INDEX, "Layer #" + LAST_LAYER_INDEX), layer);
        }

        public Layer(String name, LayeredDraw.Layer layer) {
            this(++LAST_LAYER_INDEX, name, layer);
        }
    }
}
