package com.noxcrew.noxesium.feature.ui.render;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexBuffer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.CompiledShaderProgram;
import net.minecraft.client.renderer.CoreShaders;

import java.util.Objects;

/**
 * Provides various buffer related helper functions.
 */
public class BufferHelper {

    // Controls whether changes in the OpenGL blending state a currently allowed
    public static boolean allowBlendChanges = true;

    // Whether rebinding the current render target is allowed
    public static boolean allowRebindingTarget = true;

    // Store cached values for the buffer rendering state
    private static CompiledShaderProgram configured;
    private static boolean blend;
    private static int srcRgb, dstRgb;

    /**
     * Sets up for rendering buffers.
     */
    public static CompiledShaderProgram prepare(SharedVertexBuffer sharedBuffer) {
        if (configured != null) return configured;

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

        // Set up the blending properties (or re-use if possible)
        if (!blend) {
            RenderSystem.enableBlend();
        }
        var value = GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA.value;
        if (srcRgb != value || dstRgb != value) {
            RenderSystem.blendFunc(GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
        }

        // Set up the correct shaders and color
        var shader = Objects.requireNonNull(RenderSystem.setShader(CoreShaders.BLIT_SCREEN), "Blit shader not loaded");
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);

        // Bind the vertex shader
        sharedBuffer.bind();

        // Mark that we are bound
        allowBlendChanges = false;
        configured = shader;
        return shader;
    }

    /**
     * Breaks down after rendering a buffer.
     */
    public static void unprepare() {
        if (configured == null) return;

        // Unbind the shared vertex buffer
        VertexBuffer.unbind();

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
        GlStateManager._blendFunc(srcRgb, dstRgb);

        // Mark that we have unbound
        configured = null;
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
