package com.noxcrew.noxesium.feature.ui.render;

import com.noxcrew.noxesium.feature.ui.layer.NoxesiumLayer;
import com.noxcrew.noxesium.feature.ui.layer.NoxesiumLayeredDraw;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;

import java.io.Closeable;
import java.io.IOException;
import java.util.List;

/**
 * Stores the entire render state of the current UI.
 */
public class NoxesiumUiRenderState implements Closeable {

    private ElementBuffer buffer;

    /**
     * Ticks this render state, triggering requests to the GPU to read back
     * the contents of dynamic buffers.
     */
    public void tick() {

    }

    /**
     * Renders the given layered draw object to the screen.
     */
    public void render(GuiGraphics guiGraphics, DeltaTracker deltaTracker, NoxesiumLayeredDraw layeredDraw) {
        // Set up the buffer
        if (buffer == null) {
            buffer = new ElementBuffer();
        }

        try {
            // Prepare the buffer to be drawn to
            buffer.bind(guiGraphics);

            // Draw the gui graphics onto the buffer
            guiGraphics.pose().pushPose();
            for (var layer : layeredDraw.layers()) {
                renderLayer(guiGraphics, deltaTracker, layer);
            }
            guiGraphics.pose().popPose();
        } finally {
            BufferHelper.unbind(guiGraphics);
        }

        // Finally we can draw the buffer to the actual screen
        BufferHelper.draw(List.of(buffer));
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

    @Override
    public void close() throws IOException {
        if (buffer != null) {
            buffer.close();
        }
        buffer = null;
    }
}
