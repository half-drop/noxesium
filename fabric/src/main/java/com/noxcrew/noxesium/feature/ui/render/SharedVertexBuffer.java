package com.noxcrew.noxesium.feature.ui.render;

import com.mojang.blaze3d.buffers.BufferUsage;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.noxcrew.noxesium.feature.CustomCoreShaders;
import net.minecraft.client.Minecraft;
import org.joml.Matrix4f;

import java.io.Closeable;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Stores a shared vertex buffer for running the blit shader to copy a render target to the screen.
 * The vertex buffer is always the same so we re-use it. Vanilla creates them every frame when it
 * uses the shader but we'll reuse them.
 */
public class SharedVertexBuffer implements Closeable {

    private static final int MAX_SAMPLERS = 8;
    private static final Matrix4f NULL_MATRIX = new Matrix4f();

    // Controls whether changes in the OpenGL blending state a currently allowed
    public static boolean allowBlendChanges = true;

    // Whether rebinding the current render target is allowed
    public static boolean allowRebindingTarget = true;

    private static VertexBuffer buffer;
    private static final AtomicBoolean configuring = new AtomicBoolean(false);

    /**
     * Binds this vertex buffer.
     */
    public static void bind() {
        buffer.bind();
    }

    /**
     * Performs buffer rendering for the given buffer texture ids.
     */
    public static void draw(List<Integer> textureIds) {
        if (textureIds.isEmpty()) return;

        // Create the vertex buffer if it's not already been made
        create();
        if (buffer == null) return;

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
        var shader = Objects.requireNonNull(RenderSystem.setShader(CustomCoreShaders.BLIT_SCREEN_MULTIPLE), "Blit shader not loaded");
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);

        // Prevent further blending changes
        allowBlendChanges = false;

        // Bind the vertex shader
        SharedVertexBuffer.bind();

        // Perform drawing of all buffers at once
        bind();

        // Set up the uniform with the amount of buffers
        var samplerCount = textureIds.size();
        var draws = ((samplerCount - 1) / MAX_SAMPLERS) + 1;
        var uniform = Objects.requireNonNull(shader.getUniform("SamplerCount"));

        System.out.println("drawing " + samplerCount + " in " + draws + " batches");

        for (int pass = 0; pass < draws; pass++) {
            // Tell the shader how many samplers are valid
            var count = (pass == draws - 1) ? samplerCount % MAX_SAMPLERS : MAX_SAMPLERS;
            uniform.set(count);

            // Bind all samplers to either -1 or to the actual texture, we do this
            // because the samplers don't get cleared and we don't want unnecessary
            // texture binding to make performance worse.
            for (var index = 0; index < MAX_SAMPLERS; index++) {
                if (index >= count) {
                    RenderSystem.setShaderTexture(index, -1);
                } else {
                    RenderSystem.setShaderTexture(index, textureIds.get(MAX_SAMPLERS * pass + index));
                }
            }

            // We don't use the matrices in the shader so we pass null ones.
            buffer.drawWithShader(NULL_MATRIX, NULL_MATRIX, shader);
        }

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
        GlStateManager._blendFuncSeparate(srcRgb, dstRgb, srcAlpha, dstAlpha);
    }

    /**
     * Creates the buffer if it does not yet exist.
     */
    private static void create() {
        if (buffer == null) {
            if (configuring.compareAndSet(false, true)) {
                try {
                    // Close the old buffer
                    if (buffer != null) {
                        buffer.close();
                        buffer = null;
                    }

                    // Create a buffer that just has geometry to render the texture onto the entire screen
                    var buffer = new VertexBuffer(BufferUsage.STATIC_WRITE);
                    buffer.bind();

                    // Do not use the main tesselator as it gets cleared at the end of frames.
                    var builder = RenderSystem.renderThreadTesselator().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.BLIT_SCREEN);
                    builder.addVertex(0f, 0f, 0f);
                    builder.addVertex(1f, 0f, 0f);
                    builder.addVertex(1f, 1f, 0f);
                    builder.addVertex(0f, 1f, 0f);
                    buffer.upload(builder.build());

                    // Assign the buffer instance last!
                    SharedVertexBuffer.buffer = buffer;
                } finally {
                    configuring.set(false);
                }
            }
        }
    }

    @Override
    public void close() {
        if (buffer != null) {
            buffer.close();
            buffer = null;
        }
    }

    /**
     * Rebinds the main render target.
     */
    public static void rebindMainRenderTarget() {
        RenderSystem.assertOnRenderThread();

        // Bind the main render target to replace this target,
        // we do not need to unbind this buffer first as it
        // gets replaced by running bindWrite.
        allowRebindingTarget = true;
        Minecraft.getInstance().getMainRenderTarget().bindWrite(true);
    }
}
