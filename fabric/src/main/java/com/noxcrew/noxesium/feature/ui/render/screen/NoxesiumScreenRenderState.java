package com.noxcrew.noxesium.feature.ui.render.screen;

import com.noxcrew.noxesium.feature.ui.render.BufferHelper;
import com.noxcrew.noxesium.feature.ui.render.DynamicElement;
import com.noxcrew.noxesium.feature.ui.render.SharedVertexBuffer;
import com.noxcrew.noxesium.feature.ui.render.api.NoxesiumRenderState;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;

import java.util.List;

/**
 * Stores the render state for the on-screen UI element.
 */
public class NoxesiumScreenRenderState implements NoxesiumRenderState {

    private Screen lastScreen;
    private final DynamicElement dynamic = new DynamicElement(true);

    /**
     * Returns the dynamic element used for rendering the screen.
     */
    public DynamicElement dynamic() {
        return dynamic;
    }

    /**
     * Renders the given screen.
     */
    public void render(GuiGraphics guiGraphics, int width, int height, float deltaTime, Screen screen) {
        var nanoTime = System.nanoTime();

        // Update the vertex buffer
        SharedVertexBuffer.create();

        // Try to update the buffer
        if (lastScreen != screen) {
            dynamic.redraw();
            lastScreen = screen;
        }

        // Update the buffer and redraw it if necessary
        if (dynamic.update(nanoTime, guiGraphics, () -> {
            screen.renderWithTooltip(guiGraphics, width, height, deltaTime);
        })) {
            BufferHelper.unbind();
        }

        // Try to draw the buffer to the screen
        if (dynamic.shouldUseBuffer()) {
            // If the buffer is valid we use it to draw
            BufferHelper.draw(List.of(dynamic.getTextureId()));
        } else {
            // If the buffer is invalid we draw directly
            screen.renderWithTooltip(guiGraphics, width, height, deltaTime);
        }
    }

    @Override
    public void tick() {
        dynamic.tick();
    }

    @Override
    public void close() {
        dynamic.close();
    }
}
