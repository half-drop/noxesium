package com.noxcrew.noxesium.feature.ui.render;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexBuffer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.CompiledShaderProgram;
import net.minecraft.client.renderer.CoreShaders;

import java.util.List;
import java.util.Objects;

/**
 * Provides various buffer related helper functions.
 */
public class BufferHelper {

    // Controls whether changes in the OpenGL blending state a currently allowed
    public static boolean allowBlendChanges = true;

    // Whether rebinding the current render target is allowed
    public static boolean allowRebindingTarget = true;

    /**
     * Performs buffer rendering for the given buffer texture ids.
     */
    public static void draw(List<Integer> textureIds) {
        if (textureIds.isEmpty()) return;

        // Set the texture and draw the buffer using the render texture
        // We can safely disable and re-enable the depth test because we know
        // the depth test is on through all UI rendering. We want to nicely
        // set the blending state back to what it was though to avoid causing
        // issues with other components.
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);

        // Cache the current blend state so we can return to it
        var blend = GlStateManager.BLEND.mode.enabled;
        var srcRgb = GlStateManager.BLEND.srcRgb;
        var dstRgb = GlStateManager.BLEND.dstRgb;
        var srcAlpha = GlStateManager.BLEND.srcAlpha;
        var dstAlpha = GlStateManager.BLEND.dstAlpha;

        // Set up the blending properties (or re-use if possible)
        if (!blend) {
            RenderSystem.enableBlend();
        }
        if (srcRgb != GlStateManager.SourceFactor.ONE.value || dstRgb != GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA.value) {
            RenderSystem.blendFunc(GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
        }

        // Set up the correct shaders and color
        var shader = Objects.requireNonNull(RenderSystem.setShader(CoreShaders.BLIT_SCREEN), "Blit shader not loaded");
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);

        // Prevent further blending changes
        allowBlendChanges = false;

        // Bind the vertex shader
        SharedVertexBuffer.bind();

        // Perform drawing of all buffers at once
        SharedVertexBuffer.draw(shader, textureIds);

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
    }

    /**
     * Unbinds any current buffer, re-binding the main render target.
     */
    public static void unbind() {
        RenderSystem.assertOnRenderThread();

        // Bind the main render target to replace this target,
        // we do not need to unbind this buffer first as it
        // gets replaced by running bindWrite.
        allowRebindingTarget = true;
        Minecraft.getInstance().getMainRenderTarget().bindWrite(true);
    }
}
