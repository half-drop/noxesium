package com.noxcrew.noxesium.feature.ui.render.screen;

import com.noxcrew.noxesium.feature.ui.render.DynamicElement;
import com.noxcrew.noxesium.feature.ui.render.SharedVertexBuffer;
import com.noxcrew.noxesium.feature.ui.render.api.BlendState;
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

        // Try to update the buffer
        if (lastScreen != screen) {
            dynamic.redraw();
            lastScreen = screen;
        }

        // Update the buffer and redraw it if necessary
        var blendState = BlendState.standard();
        if (dynamic.update(nanoTime, guiGraphics, blendState, () -> screen.renderWithTooltip(guiGraphics, width, height, deltaTime))) {
            SharedVertexBuffer.rebindMainRenderTarget();
        }

        // If the buffer is invalid we draw directly instead of using it
        if (dynamic.isInvalid()) {
            screen.renderWithTooltip(guiGraphics, width, height, deltaTime);
            return;
        }

        // If the buffer is valid we use it to draw
        var texture = dynamic.getTextureId();
        if (texture != -1) SharedVertexBuffer.draw(List.of(texture));
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
