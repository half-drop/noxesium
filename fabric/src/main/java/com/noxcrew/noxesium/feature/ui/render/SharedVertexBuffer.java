package com.noxcrew.noxesium.feature.ui.render;

import com.mojang.blaze3d.buffers.BufferUsage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.CompiledShaderProgram;

import java.io.Closeable;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Stores a shared vertex buffer for running the blit shader to copy a texture to the screen.
 */
public class SharedVertexBuffer implements Closeable {

    private VertexBuffer buffer;
    private float screenWidth;
    private float screenHeight;

    private final AtomicBoolean configuring = new AtomicBoolean(false);

    /**
     * Binds this vertex buffer.
     */
    public void bind() {
        buffer.bind();
    }

    /**
     * Draws using this buffer.
     */
    public void draw(CompiledShaderProgram shader, int textureId) {
        shader.bindSampler("InSampler", textureId);
        buffer.drawWithShader(RenderSystem.getModelViewMatrix(), RenderSystem.getProjectionMatrix(), RenderSystem.getShader());
    }

    /**
     * Resizes this buffer to fit the game window.
     */
    public void resize() {
        var window = Minecraft.getInstance().getWindow();
        var width = window.getWidth();
        var height = window.getHeight();

        var guiScale = (float) window.getGuiScale();
        var screenWidth = ((float) width) / guiScale;
        var screenHeight = ((float) height) / guiScale;
        if (buffer == null || this.screenWidth != screenWidth || this.screenHeight != screenHeight) {
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
                    var builder = new BufferBuilder(new ByteBufferBuilder(4 * 6), VertexFormat.Mode.QUADS, DefaultVertexFormat.BLIT_SCREEN);
                    builder.addVertex(0.0f, screenHeight, 0.0f);
                    builder.addVertex(screenWidth, screenHeight, 0.0f);
                    builder.addVertex(screenWidth, 0.0f, 0.0f);
                    builder.addVertex(0.0f, 0.0f, 0.0f);
                    buffer.upload(builder.build());

                    // Assign the buffer instance last!
                    this.screenWidth = screenWidth;
                    this.screenHeight = screenHeight;
                    this.buffer = buffer;
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
