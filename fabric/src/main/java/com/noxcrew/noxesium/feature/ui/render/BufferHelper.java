package com.noxcrew.noxesium.feature.ui.render;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.CoreShaders;

import java.util.Collection;

/**
 * Provides various buffer related helper functions.
 */
public class BufferHelper {

    // Controls whether changes in the OpenGL blending state a currently allowed
    public static boolean allowBlendChanges = true;

    /**
     * Unbinds any current buffer, re-binding the main render target.
     */
    public static void unbind(GuiGraphics guiGraphics) {
        RenderSystem.assertOnRenderThread();

        // Flush the gui graphics to finish drawing to the buffer what was in it
        guiGraphics.flush();

        // Bind the main render target to replace this target,
        // we do not need to unbind this buffer first as it
        // gets replaced by running bindWrite.
        Minecraft.getInstance().getMainRenderTarget().bindWrite(true);
    }

    /**
     * Draws a given collection of buffers to the screen as a group.
     */
    public void draw(Collection<ElementBuffer> buffers) {
        // Set the texture and draw the buffer using the render texture
        // We can safely disable and re-enable the depth test because we know
        // the depth test is on through all UI rendering. We want to nicely
        // set the blending state back to what it was though to avoid causing
        // issues with other components.
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        withBlend(() -> {
            RenderSystem.enableBlend();
            RenderSystem.blendFunc(GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
        }, () -> {
            RenderSystem.setShader(CoreShaders.POSITION_TEX);
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
            for (var buffer : buffers) {
                if (buffer.isValid()) {
                    buffer.draw();
                }
            }
        });
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    /**
     * Runs the given runnable and sets back the blending state after.
     */
    public static void withBlend(Runnable configure, Runnable runnable) {
        // Cache the current blend state so we can return to it
        final var currentBlend = GlStateManager.BLEND.mode.enabled;
        final var srcRgb = GlStateManager.BLEND.srcRgb;
        final var dstRgb = GlStateManager.BLEND.dstRgb;
        final var srcAlpha = GlStateManager.BLEND.srcAlpha;
        final var dstAlpha = GlStateManager.BLEND.dstAlpha;

        configure.run();
        allowBlendChanges = false;
        runnable.run();
        allowBlendChanges = true;

        // Restore the original state
        if (currentBlend) {
            RenderSystem.enableBlend();
        } else {
            RenderSystem.disableBlend();
        }
        GlStateManager._blendFuncSeparate(srcRgb, dstRgb, srcAlpha, dstAlpha);
    }
}
