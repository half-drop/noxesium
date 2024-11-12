package com.noxcrew.noxesium.feature.ui.render;

import com.mojang.blaze3d.buffers.BufferUsage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.CompiledShaderProgram;

import java.io.Closeable;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Stores a shared vertex buffer for running the blit shader to copy a render target to the screen.
 * The vertex buffer is always the same so we re-use it. Vanilla creates them every frame when it
 * uses the shader but we'll reuse them.
 */
public class SharedVertexBuffer implements Closeable {

    private static VertexBuffer buffer;
    private static final AtomicBoolean configuring = new AtomicBoolean(false);

    /**
     * Binds this vertex buffer.
     */
    public static void bind() {
        buffer.bind();
    }

    /**
     * Draws using this buffer.
     */
    public static void draw(CompiledShaderProgram shader, List<Integer> textureIds) {
        bind();
        for (var textureId : textureIds) {
            shader.bindSampler("InSampler", textureId);
            buffer.drawWithShader(RenderSystem.getModelViewMatrix(), RenderSystem.getProjectionMatrix(), RenderSystem.getShader());
        }
        VertexBuffer.unbind();
    }

    /**
     * Creates the buffer if it does not yet exist.
     */
    public static void create() {
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
}
