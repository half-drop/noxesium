package com.noxcrew.noxesium.feature.ui;

import com.noxcrew.noxesium.feature.ui.layer.NoxesiumLayer;

/**
 * Holds a layer and a reference to its group.
 */
public record LayerWithReference(
        NoxesiumLayer.Layer layer,
        NoxesiumLayer.LayerGroup group
) {
}