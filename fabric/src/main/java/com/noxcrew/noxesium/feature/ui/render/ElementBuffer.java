
package com.noxcrew.noxesium.feature.ui.render;

import com.mojang.blaze3d.buffers.BufferUsage;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

import java.io.Closeable;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * A wrapper around a VertexBuffer used for capturing rendered UI elements
 * and re-using them.
 * <p>
 * Inspired by <a href="https://github.com/tr7zw/Exordium">Exordium</a>!
 */
public class ElementBuffer implements Closeable {

    private RenderTarget target;
    private VertexBuffer buffer;
    private double screenWidth;
    private double screenHeight;

    private final AtomicBoolean configuring = new AtomicBoolean(false);

    /**
     * Binds this buffer to the render target, replacing any previous target.
     */
    public boolean bind(GuiGraphics guiGraphics) {
        RenderSystem.assertOnRenderThread();

        // Flush the gui graphics to finish drawing to whatever it was on
        guiGraphics.flush();

        // Before binding we want to resize this buffer if necessary
        resize();

        // If the buffer has been properly created we can bind it
        // and clear it.
        if (isValid()) {
            target.setClearColor(0, 0, 0, 0);
            target.clear();
            target.bindWrite(false);
            return true;
        }
        return false;
    }

    /**
     * Resizes this buffer to fit the game window.
     */
    private void resize() {
        var window = Minecraft.getInstance().getWindow();
        var width = window.getWidth();
        var height = window.getHeight();

        // Do the screen size calculation manually so we can use doubles which
        // give necessary precision.
        var guiScale = (float) window.getGuiScale();
        var screenWidth = ((float) width) / guiScale;
        var screenHeight = ((float) height) / guiScale;

        if (target == null || target.width != width || target.height != height || this.screenWidth != screenWidth || this.screenHeight != screenHeight) {
            if (configuring.compareAndSet(false, true)) {
                try {
                    // Close the old buffer
                    if (buffer != null) {
                        buffer.close();
                        buffer = null;
                    }

                    // Create a single texture that stretches the entirety of the buffer
                    var buffer = new VertexBuffer(BufferUsage.STATIC_WRITE);
                    buffer.bind();

                    var builder = new BufferBuilder(new ByteBufferBuilder(4 * 6), VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
                    builder.addVertex(0.0f, screenHeight, 0.0f).setUv(0.0f, 0.0f);
                    builder.addVertex(screenWidth, screenHeight, 0.0f).setUv(1.0f, 0.0f);
                    builder.addVertex(screenWidth, 0.0f, 0.0f).setUv(1.0f, 1.0f);
                    builder.addVertex(0.0f, 0.0f, 0.0f).setUv(0.0f, 1.0f);
                    buffer.upload(builder.build());

                    if (target == null) {
                        target = new TextureTarget(width, height, true);
                    } else {
                        target.resize(width, height);
                    }

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

    /**
     * Draws this buffer directly and immediately.
     */
    protected void draw() {
        RenderSystem.setShaderTexture(0, target.getColorTextureId());
        buffer.bind();
        buffer.drawWithShader(RenderSystem.getModelViewMatrix(), RenderSystem.getProjectionMatrix(), RenderSystem.getShader());
    }

    /**
     * Returns whether the buffer is valid and has been prepared.
     */
    public boolean isValid() {
        return buffer != null;
    }

    @Override
    public void close() {
        if (buffer != null) {
            buffer.close();
            buffer = null;
        }
        if (target != null) {
            target.destroyBuffers();
            target = null;
        }
    }

}
