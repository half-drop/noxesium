package com.noxcrew.noxesium.feature.ui.render;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.CoreShaders;

/**
 * Provides various buffer related helper functions.
 */
public class BufferHelper {

    // Controls whether changes in the OpenGL blending state a currently allowed
    public static boolean allowBlendChanges = true;

    // Store cached values for the buffer rendering state
    private static boolean configured = false;
    private static boolean blend;
    private static int srcRgb, dstRgb, srcAlpha, dstAlpha;

    /**
     * Sets up for rendering buffers.
     */
    public static void prepare() {
        if (configured) return;

        // Set the texture and draw the buffer using the render texture
        // We can safely disable and re-enable the depth test because we know
        // the depth test is on through all UI rendering. We want to nicely
        // set the blending state back to what it was though to avoid causing
        // issues with other components.
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);

        // Cache the current blend state so we can return to it
        blend = GlStateManager.BLEND.mode.enabled;
        srcRgb = GlStateManager.BLEND.srcRgb;
        dstRgb = GlStateManager.BLEND.dstRgb;
        srcAlpha = GlStateManager.BLEND.srcAlpha;
        dstAlpha = GlStateManager.BLEND.dstAlpha;

        // Set up the blending properties
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);

        // Set up the correct shaders and color
        RenderSystem.setShader(CoreShaders.POSITION_TEX);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);

        // Mark that we are bound
        allowBlendChanges = false;
        configured = true;
    }

    /**
     * Breaks down after rendering a buffer.
     */
    public static void unprepare() {
        if (!configured) return;

        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);

        // Restore the original state
        allowBlendChanges = true;
        if (blend) {
            RenderSystem.enableBlend();
        } else {
            RenderSystem.disableBlend();
        }
        GlStateManager._blendFuncSeparate(srcRgb, dstRgb, srcAlpha, dstAlpha);

        // Mark that we have unbound
        configured = false;
    }

    /**
     * Unbinds any current buffer, re-binding the main render target.
     */
    public static void unbind() {
        RenderSystem.assertOnRenderThread();

        // Bind the main render target to replace this target,
        // we do not need to unbind this buffer first as it
        // gets replaced by running bindWrite.
        Minecraft.getInstance().getMainRenderTarget().bindWrite(true);
    }
}
